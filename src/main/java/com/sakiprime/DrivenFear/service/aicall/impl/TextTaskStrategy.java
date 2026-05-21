package com.sakiprime.DrivenFear.service.aicall.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sakiprime.DrivenFear.entity.AICallRequestDTO;
import com.sakiprime.DrivenFear.entity.AICallTaskEntity;
import com.sakiprime.DrivenFear.entity.AIModelConfigEntity;
import com.sakiprime.DrivenFear.entity.MessageCorrelationData;
import com.sakiprime.DrivenFear.mapper.AIModelConfigMapper;
import com.sakiprime.DrivenFear.service.aicall.AITaskStrategy;
import com.sakiprime.DrivenFear.service.userfile.UserCommonService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.sakiprime.DrivenFear.config.RabbitConfig.TEXT_TASK_QUEUE;

/**
 * 文本任务策略
 *
 * @author 凋零
 * @since 2026/05/04
 */
@Component("TEXT")
@Slf4j
@RequiredArgsConstructor
public class TextTaskStrategy implements AITaskStrategy {
    private final RedisTemplate<String, Object> redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final AIModelConfigMapper aiModelConfigMapper;
    private final UserCommonService userCommonService;
    private final ChatClient chatClient;
    @Value("${aimodelconfig.text.default-key}")
    private String DEFAULT_MODEL_KEY;
    @Value("${aimodelconfig.text.default-value}")
    private Integer DEFAULT_MODEL_VALUE;


    public static volatile Map<String, Integer> TEXT_MODEL_COST_MAP = new ConcurrentHashMap<>();


    public Map<String, Integer> getTextModelCostMap() {

        try{
        LambdaQueryWrapper<AIModelConfigEntity> wrapper = Wrappers.lambdaQuery(AIModelConfigEntity.class);
        wrapper.eq(AIModelConfigEntity::getModelType, "TEXT");
        //只检索已经启用的文本模型。
        wrapper.eq(AIModelConfigEntity::getStatus, true);

        List<AIModelConfigEntity> modelList = aiModelConfigMapper.selectList(wrapper);
            if (CollUtil.isEmpty(modelList)) {
                log.warn("未查询到任何启用的文本模型");
                return new HashMap<>();
            }
            return modelList.stream()
                    .filter(Objects::nonNull)
                    .filter(entity -> entity.getCostToken() != null)
                    .collect(Collectors.toMap(
                            AIModelConfigEntity::getModelName,
                            AIModelConfigEntity::getCostToken,
                            (oldValue, newValue) -> oldValue //model_name重复的时候留先加载到的配置，不过不太应该出错
                    ));
        }
        catch (Exception e){
            log.warn("查询文本模型价格配置异常", e);
            return new HashMap<>();
        }
    }

    /**
     * 刷新模型成本图
     *
     */
    @PostConstruct
    public boolean refreshModelCostMap() {
        Map<String,Integer> costMap;
        for(int i=0;i<3;i++) { //最多三次重试机会。
             costMap = getTextModelCostMap();
            if (!costMap.isEmpty()) {
                TEXT_MODEL_COST_MAP = new ConcurrentHashMap<>(costMap);
                log.info("文本模型配置表初始化成功，加载到{}条配置", TEXT_MODEL_COST_MAP.size());
                return true;
            }
        }
        TEXT_MODEL_COST_MAP = new ConcurrentHashMap<>(
                Collections.singletonMap(DEFAULT_MODEL_KEY, DEFAULT_MODEL_VALUE)
        );
        log.error("[需要人工核查]文本模型配置表初始化3次重试全部失败，已启用兜底默认配置");
        return false;
    }
    private static final String LUA_SCRIPT =
            """
                    local current = tonumber(redis.call('GET', KEYS[1]) or 0)
                    local cost = tonumber(ARGV[1])
                    if current >= cost then
                        redis.call('DECRBY', KEYS[1], cost)
                        return 1
                    else
                        return 0
                    end""";

    /**
     * 获取模型成本
     *
     * @param model 模型
     * @return {@link Integer }
     */
    private Integer getModelCost(String model) {

        return TEXT_MODEL_COST_MAP.get(model); //从MAP获得模型tokens花费
    }

    /**
     * 执行
     *
     * @param request 请求
     * @return boolean
     */
    @Override
    public boolean execute(AICallRequestDTO request) {
    String key = "user:points:" + request.getUserId();
    Integer cost =getModelCost(request.getAIModel());
    if (cost == null) { //校验模型
        log.warn("文本模型调用失败:非法的模型{}", request.getAIModel());
        return false;
    }
    //懒加载Redis的Token缓存。三天过期。
    if(!redisTemplate.hasKey(key)){
        userCommonService.refreshUserTokenRedis(request.getUserId());
    }
    Long orderId = IdWorker.getId();//雪花订单号
    request.setOrderId(orderId);
    request.setTokenCost(cost);
    RedisScript<Long> script = RedisScript.of(LUA_SCRIPT, Long.class);
    Long result = redisTemplate.execute(script, Collections.singletonList(key), cost);
    if (result.equals(0L)) {
        log.warn("文本模型调用失败:Token扣除失败");
        return false;
    }//成功扣除点数
    String logInfo = String.format("文本生成任务创建|用户:%s,订单:%s", request.getUserId(),request.getOrderId());
    MessageCorrelationData  correlation = new MessageCorrelationData(
            TEXT_TASK_QUEUE,
            request,
            logInfo,
            MessageCorrelationData.AI_TASK
    );
    rabbitTemplate.convertAndSend(TEXT_TASK_QUEUE,correlation,correlation);
    return true;
    }

    @Override
    public String processTask(AICallTaskEntity task) {
        return chatClient.prompt()
                .user(task.getPrompt())
                .call()
                .content();
    }
}
