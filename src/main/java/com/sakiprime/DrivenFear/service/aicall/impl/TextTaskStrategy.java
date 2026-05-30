package com.sakiprime.DrivenFear.service.aicall.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    public static volatile Map<String, JsonNode> TEXT_MODEL_TEMPLATE_MAP = new ConcurrentHashMap<>();

    public Map<String, JsonNode> getTextModelTemplateMap() {
        try {
            LambdaQueryWrapper<AIModelConfigEntity> wrapper = Wrappers.lambdaQuery(AIModelConfigEntity.class);
            wrapper.eq(AIModelConfigEntity::getModelType, "TEXT");
            wrapper.eq(AIModelConfigEntity::getStatus, true);

            List<AIModelConfigEntity> modelList = aiModelConfigMapper.selectList(wrapper);
            if (CollUtil.isEmpty(modelList)) {
                log.warn("未查询到任何启用的文本模型");
                return new HashMap<>();
            }
            ObjectMapper mapper = new ObjectMapper();
            return modelList.stream()
                    .filter(Objects::nonNull)
                    .filter(entity -> entity.getTemplate() != null)
                    .collect(HashMap::new, (map, entity) -> {
                        try {
                            map.put(entity.getModelName(), mapper.readTree(entity.getTemplate()));
                        } catch (Exception e) {
                            log.warn("解析模型[{}]template失败", entity.getModelName(), e);
                        }
                    }, HashMap::putAll);
        } catch (Exception e) {
            log.warn("查询文本模型模板配置异常", e);
            return new HashMap<>();
        }
    }

    @PostConstruct
    public boolean refreshModelTemplateMap() {
        Map<String, JsonNode> templateMap;
        for (int i = 0; i < 3; i++) {
            templateMap = getTextModelTemplateMap();
            if (!templateMap.isEmpty()) {
                TEXT_MODEL_TEMPLATE_MAP = new ConcurrentHashMap<>(templateMap);
                log.info("文本模型模板配置表初始化成功，加载到{}条配置", TEXT_MODEL_TEMPLATE_MAP.size());
                return true;
            }
        }
        log.error("[需要人工核查]文本模型模板配置表初始化3次重试全部失败，无可用模板配置");
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

    @SuppressWarnings("unchecked")
    private Integer getModelCostAndInit(AICallRequestDTO request) {
        JsonNode template = TEXT_MODEL_TEMPLATE_MAP.get(request.getAIModel());
        if (template == null) return null;

        request.setParams(fillDefaultParams(request.getParams(), template.get("paramsSchema")));
        request.setTemplate(template.toString());

        JsonNode costFormula = template.get("costFormula");
        if (costFormula == null) return null;

        int base = costFormula.get("base").asInt(DEFAULT_MODEL_VALUE);
        JsonNode modifiers = costFormula.get("modifiers");
        if (modifiers == null) return base;

        Map<String, Object> params = new HashMap<>();
        if (request.getParams() != null && !request.getParams().isEmpty()) {
            try {
                params = new ObjectMapper().readValue(request.getParams(), Map.class);
            } catch (Exception e) {
                log.warn("解析请求参数失败,订单号:{}", request.getOrderId(), e);
            }
        }

        int extra = 0;
        Iterator<String> modKeys = modifiers.fieldNames();
        while (modKeys.hasNext()) {
            String key = modKeys.next();
            JsonNode modValue = modifiers.get(key);

            if (modValue.isObject()) {
                Object paramVal = params.get(key);
                if (paramVal != null) {
                    JsonNode match = modValue.get(paramVal.toString());
                    if (match != null && match.isNumber()) {
                        extra += match.asInt(0);
                    }
                }
            } else if (modValue.isNumber()) {
                Object val = params.get(key);
                if (val != null && !"false".equals(val.toString()) && !"0".equals(val.toString())) {
                    extra += modValue.asInt(0);
                }
            }
        }

        return base + extra;
    }

    @SuppressWarnings("unchecked")
    private String fillDefaultParams(String paramsStr, JsonNode paramsSchema) {
        if (paramsSchema == null) return StrUtil.emptyToDefault(paramsStr, "{}");

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> params = new HashMap<>();
        if (StrUtil.isNotEmpty(paramsStr)) {
            try {
                params = mapper.readValue(paramsStr, Map.class);
            } catch (Exception e) {
                log.warn("解析请求参数失败", e);
            }
        }

        boolean changed = false;
        Iterator<String> fields = paramsSchema.fieldNames();
        while (fields.hasNext()) {
            String key = fields.next();
            if (params.containsKey(key)) continue;
            JsonNode defVal = paramsSchema.get(key).get("default");
            if (defVal == null || defVal.isNull() || defVal.isMissingNode()) continue;
            if (defVal.isBoolean()) params.put(key, defVal.asBoolean());
            else if (defVal.isNumber()) params.put(key, defVal.asInt());
            else params.put(key, defVal.asText());
            changed = true;
        }

        if (changed) {
            try {
                return mapper.writeValueAsString(params);
            } catch (Exception e) {
                log.warn("序列化默认参数失败", e);
            }
        }
        return StrUtil.emptyToDefault(paramsStr, "{}");
    }

    @Override
    public boolean execute(AICallRequestDTO request) {
        String key = "user:points:" + request.getUserId();
        Integer cost = getModelCostAndInit(request);
        if (cost == null) {
            log.warn("文本模型调用失败:非法的模型或缺少template配置");
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
            log.warn("文本模型调用失败:Token扣除失败");
            return false;
        }
        String logInfo = String.format("文本生成任务创建|用户:%s,订单:%s", request.getUserId(), request.getOrderId());
        MessageCorrelationData correlation = new MessageCorrelationData(
                TEXT_TASK_QUEUE,
                request,
                logInfo,
                MessageCorrelationData.AI_TASK
        );
        rabbitTemplate.convertAndSend(TEXT_TASK_QUEUE, correlation, correlation);
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
