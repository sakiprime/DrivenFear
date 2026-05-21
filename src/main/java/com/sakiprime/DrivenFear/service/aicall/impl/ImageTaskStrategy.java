package com.sakiprime.DrivenFear.service.aicall.impl;

import cn.hutool.core.collection.CollUtil;
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
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.sakiprime.DrivenFear.config.RabbitConfig.IMAGE_TASK_QUEUE;

@Component("IMAGE")
@Slf4j
@RequiredArgsConstructor
public class ImageTaskStrategy implements AITaskStrategy {
    private final RedisTemplate<String, Object> redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final AIModelConfigMapper aiModelConfigMapper;
    private final UserCommonService userCommonService;
    @Resource(name = "dmxWebClient")
    private WebClient dmxWebClient;
    @Value("${aimodelconfig.image.default-key}")
    private String DEFAULT_MODEL_KEY;
    @Value("${aimodelconfig.image.default-value}")
    private Integer DEFAULT_MODEL_VALUE;

    public static volatile Map<String, Integer> IMAGE_MODEL_COST_MAP = new ConcurrentHashMap<>();

    public Map<String, Integer> getImageModelCostMap() {
        try {
            LambdaQueryWrapper<AIModelConfigEntity> wrapper = Wrappers.lambdaQuery(AIModelConfigEntity.class);
            wrapper.eq(AIModelConfigEntity::getModelType, "IMAGE");
            wrapper.eq(AIModelConfigEntity::getStatus, true);

            List<AIModelConfigEntity> modelList = aiModelConfigMapper.selectList(wrapper);
            if (CollUtil.isEmpty(modelList)) {
                log.warn("未查询到任何启用的图片模型");
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
            log.warn("查询图片模型价格配置异常", e);
            return new HashMap<>();
        }
    }

    @PostConstruct
    public boolean refreshModelCostMap() {
        Map<String, Integer> costMap;
        for (int i = 0; i < 3; i++) {
            costMap = getImageModelCostMap();
            if (!costMap.isEmpty()) {
                IMAGE_MODEL_COST_MAP = new ConcurrentHashMap<>(costMap);
                log.info("图片模型配置表初始化成功，加载到{}条配置", IMAGE_MODEL_COST_MAP.size());
                return true;
            }
        }
        IMAGE_MODEL_COST_MAP = new ConcurrentHashMap<>(
                Collections.singletonMap(DEFAULT_MODEL_KEY, DEFAULT_MODEL_VALUE)
        );
        log.error("[需要人工核查]图片模型配置表初始化3次重试全部失败，已启用兜底默认配置");
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
        return IMAGE_MODEL_COST_MAP.get(model);
    }

    @Override
    public boolean execute(AICallRequestDTO request) {

        String key = "user:points:" + request.getUserId();
        Integer cost = getModelCost(request.getAIModel());
        if (cost == null) {
            log.warn("图片模型调用失败:非法的模型");
            return false;
        }
        if (!redisTemplate.hasKey(key)) { //刷新用户Token在Redis的缓存
            userCommonService.refreshUserTokenRedis(request.getUserId());
        }
        Long orderId = IdWorker.getId();
        request.setOrderId(orderId);
        request.setTokenCost(cost); //确认Token消费
        RedisScript<Long> script = RedisScript.of(LUA_SCRIPT, Long.class); //可以移到方法外
        Long result = redisTemplate.execute(script, Collections.singletonList(key), cost);
        if (result.equals(0L)) {
            log.warn("图片模型调用失败:Token扣除失败");
            return false;
        }
        String logInfo = String.format("图片生成任务创建|用户:%s,订单:%s", request.getUserId(), request.getOrderId());
        MessageCorrelationData correlation = new MessageCorrelationData(
                IMAGE_TASK_QUEUE,
                request,
                logInfo,
                MessageCorrelationData.AI_TASK
        );
        rabbitTemplate.convertAndSend(IMAGE_TASK_QUEUE, correlation, correlation);
        return true;
    }

    @Override
    public String processTask(AICallTaskEntity task) {
        String model = task.getAIModel();
        String prompt = task.getPrompt();

        Map<String, Object> body = buildRequestBody(model, prompt);

        String response = dmxWebClient.post()
                .uri("/v1/responses")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        return extractImageUrl(model, response);
    }

    private Map<String, Object> buildRequestBody(String model, String prompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("response_format", "url");
        body.put("watermark", false);

        if (model != null && model.startsWith("wan")) {
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("messages", List.of(Map.of(
                    "role", "user",
                    "content", List.of(Map.of("text", prompt))
            )));
            body.put("input", input);
            body.put("parameters", Map.of(
                    "size", "2K",
                    "n", 1
            ));
        } else {
            body.put("input", prompt);
            body.put("size", "2K");
        }
        return body;
    }

    private String extractImageUrl(String model, String responseBody) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(responseBody);
            String text = root.at("/output/0/content/0/text").asText();

            if (model != null && model.startsWith("doubao")) {
                int start = text.indexOf("](");
                int end = text.lastIndexOf(")");
                if (start != -1 && end != -1) {
                    return text.substring(start + 2, end);
                }
            }
            return text;
        } catch (Exception e) {
            log.error("解析图片生成响应失败, model:{}, response:{}", model, responseBody, e);
            throw new RuntimeException("图片生成响应解析失败", e);
        }
    }
}
