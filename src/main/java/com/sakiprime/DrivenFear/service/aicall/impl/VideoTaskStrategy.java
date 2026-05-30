package com.sakiprime.DrivenFear.service.aicall.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakiprime.DrivenFear.common.util.Result;
import com.sakiprime.DrivenFear.component.QiniuOSS;
import com.sakiprime.DrivenFear.entity.AICallRequestDTO;
import com.sakiprime.DrivenFear.entity.AICallTaskEntity;
import com.sakiprime.DrivenFear.entity.AIModelConfigEntity;
import com.sakiprime.DrivenFear.entity.MessageCorrelationData;
import com.sakiprime.DrivenFear.mapper.AICallTaskMapper;
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

import static com.sakiprime.DrivenFear.config.RabbitConfig.VIDEO_TASK_QUEUE;

@Component("VIDEO")
@Slf4j
@RequiredArgsConstructor
public class VideoTaskStrategy implements AITaskStrategy {
    private final RedisTemplate<String, Object> redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final AIModelConfigMapper aiModelConfigMapper;
    private final AICallTaskMapper aiCallTaskMapper;
    private final UserCommonService userCommonService;
    private final QiniuOSS qiniuOSS;

    @Resource(name = "dmxWebClient")
    private WebClient dmxWebClient;
    @Value("${aimodelconfig.video.default-key}")
    private String DEFAULT_MODEL_KEY;
    @Value("${aimodelconfig.video.default-value}")
    private Integer DEFAULT_MODEL_VALUE;

    public static volatile Map<String, JsonNode> VIDEO_MODEL_TEMPLATE_MAP = new ConcurrentHashMap<>();

    public Map<String, JsonNode> getVideoModelTemplateMap() {
        try {
            LambdaQueryWrapper<AIModelConfigEntity> wrapper = Wrappers.lambdaQuery(AIModelConfigEntity.class);
            wrapper.eq(AIModelConfigEntity::getModelType, "VIDEO");
            wrapper.eq(AIModelConfigEntity::getStatus, true);

            List<AIModelConfigEntity> modelList = aiModelConfigMapper.selectList(wrapper);
            if (CollUtil.isEmpty(modelList)) {
                log.warn("未查询到任何启用的视频模型");
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
            log.warn("查询视频模型模板配置异常", e);
            return new HashMap<>();
        }
    }

    @PostConstruct
    public boolean refreshModelTemplateMap() {
        Map<String, JsonNode> templateMap;
        for (int i = 0; i < 3; i++) {
            templateMap = getVideoModelTemplateMap();
            if (!templateMap.isEmpty()) {
                VIDEO_MODEL_TEMPLATE_MAP = new ConcurrentHashMap<>(templateMap);
                log.info("视频模型模板配置表初始化成功，加载到{}条配置", VIDEO_MODEL_TEMPLATE_MAP.size());
                return true;
            }
        }
        log.error("[需要人工核查]视频模型模板配置表初始化3次重试全部失败，无可用模板配置");
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
        JsonNode template = VIDEO_MODEL_TEMPLATE_MAP.get(request.getAIModel());
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
            log.warn("视频模型调用失败:非法的模型或缺少template配置");
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
            log.warn("视频模型调用失败:Token扣除失败,用户余额不足或网络波动，用户Id:{}",request.getUserId());
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
        String model = task.getAIModel();
        String prompt = task.getPrompt();
        String externalTaskId = task.getExternalTaskId();

        // 1. 已有 DMX taskId → 跳过 submit（免费），直接查询
        if (externalTaskId == null) {
            Map<String, Object> body = buildRequestBody(model, prompt, task.getParams());

            log.warn("DMX API 请求体: model={}, body={}", model, body);
            String submitResponse = dmxWebClient.post()
                    .uri("/v1/responses")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            log.warn("DMX API 提交响应: model={}, response={}", model, submitResponse);

            // 尝试从提交响应中直接提取视频URL（同步返回的情况）
            String videoUrl = extractVideoUrl(submitResponse);
            if (videoUrl != null) {
                return fetchToOSS(videoUrl, task);
            }

            externalTaskId = extractTaskId(submitResponse);
            if (externalTaskId == null) {
                log.warn("DMX API 提交响应中没有任务ID，response:{}", submitResponse);
                return null;
            }

            // 保存 externalTaskId，防止重试重复付费
            task.setExternalTaskId(externalTaskId);
            aiCallTaskMapper.updateById(task);
        }

        // 2. 查询任务结果
        Map<String, Object> queryBody = new LinkedHashMap<>();
        queryBody.put("model", "seedance-2-0-get");
        queryBody.put("input", externalTaskId);

        String queryResponse;
        try {
            queryResponse = dmxWebClient.post()
                    .uri("/v1/responses")
                    .bodyValue(queryBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            log.warn("DMX API 查询失败: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            return null;
        }

        log.warn("DMX API 查询响应: taskId={}, response={}", externalTaskId, queryResponse);

        String videoUrl = extractVideoUrl(queryResponse);
        if (videoUrl == null) {
            log.warn("DMX API 查询未返回视频URL，等待Consumer重试。taskId:{}", externalTaskId);
            return null;
        }

        return fetchToOSS(videoUrl, task);
    }

    private String fetchToOSS(String videoUrl, AICallTaskEntity task) {
        Result<String> OSSFetchResult;
        for (int i = 1; i <= 3; i++) {
            try {
                OSSFetchResult = qiniuOSS.fetchResource(videoUrl, task.getOrderId().toString(), task.getUserId());
            } catch (Exception e) {
                continue;
            }
            if (OSSFetchResult.getCode() == 200) {
                return OSSFetchResult.getData();
            }
        }
        log.warn("OSS尝试拉取临时视频URL失败。订单号:{}", task.getOrderId());
        return null;
    }

    private Map<String, Object> buildRequestBody(String model, String prompt, String paramsJson) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", List.of(Map.of("type", "text", "text", prompt)));
        body.put("watermark", false);

        // 从 params 解析额外参数
        if (paramsJson != null && !paramsJson.isBlank()) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode params = mapper.readTree(paramsJson);
                if (params.has("ratio")) body.put("ratio", params.get("ratio").asText());
                if (params.has("duration")) body.put("duration", params.get("duration").asInt());
                if (params.has("resolution")) body.put("resolution", params.get("resolution").asText());
                if (params.has("generate_audio")) body.put("generate_audio", params.get("generate_audio").asBoolean());
                if (params.has("seed")) body.put("seed", params.get("seed").asInt());
            } catch (JsonProcessingException e) {
                log.warn("视频参数解析失败: {}", paramsJson);
            }
        }

        return body;
    }

    private String extractTaskId(String responseBody) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(responseBody);
            String id = root.at("/id").asText();
            return (id != null && !id.isBlank()) ? id : null;
        } catch (Exception e) {
            log.warn("从DMX响应中提取任务ID失败", e);
            return null;
        }
    }

    private String extractVideoUrl(String responseBody) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(responseBody);

            // 尝试从标准输出路径提取: output[0].content[0].text → JSON → content.video_url
            JsonNode output = root.at("/output");
            if (output.isArray() && !output.isEmpty()) {
                JsonNode content = output.get(0).at("/content");
                if (content.isArray() && !content.isEmpty()) {
                    String text = content.get(0).at("/text").asText();
                    if (text != null && !text.isBlank()) {
                        // text 可能是 JSON 字符串，也可能直接是 URL
                        try {
                            JsonNode textJson = mapper.readTree(text);
                            String url = textJson.at("/content/video_url").asText();
                            if (url != null && !url.isBlank()) return url;
                        } catch (Exception ignored) {
                        }
                        // 不是 JSON 就当作直接 URL
                        if (text.startsWith("http")) return text;
                    }
                }
            }

            // 尝试直接从根节点取 video_url
            String directUrl = root.at("/content/video_url").asText();
            if (directUrl != null && !directUrl.isBlank()) return directUrl;

            return null;
        } catch (Exception e) {
            log.warn("视频API返回的JSON解析出错", e);
            return null;
        }
    }
}
