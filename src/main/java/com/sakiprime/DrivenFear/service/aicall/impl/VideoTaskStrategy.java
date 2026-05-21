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
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component("VIDEO")
@Slf4j
@RequiredArgsConstructor
public class VideoTaskStrategy implements AITaskStrategy {
    private static final String VIDEO_TASK_QUEUE = "VideoTaskQueue";

    private final RedisTemplate<String, Object> redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final AIModelConfigMapper aiModelConfigMapper;
    private final UserCommonService userCommonService;
    @Value("${aimodelconfig.video.default-key}")
    private String DEFAULT_MODEL_KEY;
    @Value("${aimodelconfig.video.default-value}")
    private Integer DEFAULT_MODEL_VALUE;

    public static volatile Map<String, Integer> VIDEO_MODEL_COST_MAP = new ConcurrentHashMap<>();

    public Map<String, Integer> getVideoModelCostMap() {
        try {
            LambdaQueryWrapper<AIModelConfigEntity> wrapper = Wrappers.lambdaQuery(AIModelConfigEntity.class);
            wrapper.eq(AIModelConfigEntity::getModelType, "VIDEO");
            wrapper.eq(AIModelConfigEntity::getStatus, true);

            List<AIModelConfigEntity> modelList = aiModelConfigMapper.selectList(wrapper);
            if (CollUtil.isEmpty(modelList)) {
                log.warn("未查询到任何启用的视频模型");
                return new HashMap<>();
            }
            return modelList.stream()
                    .filter(Objects::nonNull)
                    .filter(entity -> entity.getCostToken() != null)
                    .collect(Collectors.toMap(
                            AIModelConfigEntity::getModelName,
                            AIModelConfigEntity::getCostToken,
                            (oldValue, newValue) -> oldValue
                    ));
        } catch (Exception e) {
            log.warn("查询视频模型价格配置异常", e);
            return new HashMap<>();
        }
    }

    @PostConstruct
    public boolean refreshModelCostMap() {
        Map<String, Integer> costMap;
        for (int i = 0; i < 3; i++) {
            costMap = getVideoModelCostMap();
            if (!costMap.isEmpty()) {
                VIDEO_MODEL_COST_MAP = new ConcurrentHashMap<>(costMap);
                log.info("视频模型配置表初始化成功，加载到{}条配置", VIDEO_MODEL_COST_MAP.size());
                return true;
            }
        }
        VIDEO_MODEL_COST_MAP = new ConcurrentHashMap<>(
                Collections.singletonMap(DEFAULT_MODEL_KEY, DEFAULT_MODEL_VALUE)
        );
        log.error("[需要人工核查]视频模型配置表初始化3次重试全部失败，已启用兜底默认配置");
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

    private Integer getModelCost(String model) {
        return VIDEO_MODEL_COST_MAP.get(model);
    }

    @Override
    public boolean execute(AICallRequestDTO request) {
        String key = "user:points:" + request.getUserId();
        Integer cost = getModelCost(request.getAIModel());
        if (cost == null) {
            log.warn("视频模型调用失败:非法的模型");
            return false;
        }
        if (!redisTemplate.hasKey(key)) {
            userCommonService.refreshUserTokenRedis(request.getUserId());
        }
        Long orderId = IdWorker.getId();
        request.setOrderId(orderId);
        request.setTokenCost(cost);
        RedisScript<Long> script = RedisScript.of(LUA_SCRIPT, Long.class);
        Long result = redisTemplate.execute(script, Collections.singletonList(key), cost);
        if (result.equals(0L)) {
            log.warn("视频模型调用失败:Token扣除失败");
            return false;
        }
        String logInfo = String.format("视频生成任务创建|用户:%s,订单:%s", request.getUserId(), request.getOrderId());
        MessageCorrelationData correlation = new MessageCorrelationData(
                VIDEO_TASK_QUEUE,
                request,
                logInfo,
                MessageCorrelationData.AI_TASK
        );
        rabbitTemplate.convertAndSend(VIDEO_TASK_QUEUE, correlation, correlation);
        return true;
    }

    @Override
    public String processTask(AICallTaskEntity task) {
        return "";
    }
}
