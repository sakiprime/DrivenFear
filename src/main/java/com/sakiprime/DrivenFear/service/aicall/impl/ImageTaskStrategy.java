package com.sakiprime.DrivenFear.service.aicall.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.JsonPath;
import com.sakiprime.DrivenFear.entity.AICallRequestDTO;
import com.sakiprime.DrivenFear.entity.AICallTaskEntity;
import com.sakiprime.DrivenFear.entity.AIModelConfigEntity;
import com.sakiprime.DrivenFear.common.util.Result;
import com.sakiprime.DrivenFear.component.QiniuOSS;
import com.sakiprime.DrivenFear.entity.MessageCorrelationData;
import com.sakiprime.DrivenFear.mapper.AIModelConfigMapper;
import com.sakiprime.DrivenFear.service.aicall.AITaskStrategy;
import com.sakiprime.DrivenFear.service.userfile.UserCommonService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.util.PropertyPlaceholderHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.core.io.JsonStringEncoder;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
    private final QiniuOSS qiniuOSS;
    @Value("${aimodelconfig.image.default-value}")
    private Integer DEFAULT_MODEL_COST;

    public static volatile Map<String, JsonNode> IMAGE_MODEL_TEMPLATE_MAP = new ConcurrentHashMap<>();

    private static final JsonStringEncoder JSON_ENCODER = JsonStringEncoder.getInstance();
    public Map<String, JsonNode> getImageModelTemplateMap() {
        try {
            LambdaQueryWrapper<AIModelConfigEntity> wrapper = Wrappers.lambdaQuery(AIModelConfigEntity.class);
            wrapper.eq(AIModelConfigEntity::getModelType, "IMAGE");
            wrapper.eq(AIModelConfigEntity::getStatus, true);

            List<AIModelConfigEntity> modelList = aiModelConfigMapper.selectList(wrapper);
            if (CollUtil.isEmpty(modelList)) {
                log.warn("未查询到任何启用的图片模型");
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
            log.warn("查询图片模型模板配置异常", e);
            return new HashMap<>();
        }
    }

    @PostConstruct
    public boolean refreshModelTemplateMap() {
        Map<String, JsonNode> templateMap;
        for (int i = 0; i < 3; i++) {
            templateMap = getImageModelTemplateMap();
            if (!templateMap.isEmpty()) {
                IMAGE_MODEL_TEMPLATE_MAP = new ConcurrentHashMap<>(templateMap);
                log.info("图片模型模板配置表初始化成功，加载到{}条配置", IMAGE_MODEL_TEMPLATE_MAP.size());
                return true;
            }
        }
        log.error("[需要人工核查]图片模型模板配置表初始化3次重试全部失败，无可用模板配置");
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
        JsonNode template = IMAGE_MODEL_TEMPLATE_MAP.get(request.getAIModel());
        if (template == null) return null;

        // 用 paramsSchema.default 填充遗漏参数，保证 cost 和 requestBody 用同一份参数
        request.setParams(fillDefaultParams(request.getParams(), template.get("paramsSchema")));
        //后续在转化为entity后会存在数据库，可用于追溯
        request.setTemplate(template.toString());

        JsonNode costFormula = template.get("costFormula");
        if (costFormula == null) return null;

        int base = costFormula.get("base").asInt(DEFAULT_MODEL_COST);
        JsonNode modifiers = costFormula.get("modifiers");
        if (modifiers == null) return base;

        // 解析请求参数
        Map<String, Object> params = new HashMap<>();
        if (request.getParams() != null && !request.getParams().isEmpty()) {
            try {
                params = new ObjectMapper().readValue(request.getParams(), Map.class);
            } catch (Exception e) {
                log.warn("解析请求参数失败,订单号:{}", request.getOrderId(),e);
            }
        }

        int extra = 0;
        Iterator<String> modKeys = modifiers.fieldNames();
        while (modKeys.hasNext()) {
            String key = modKeys.next();
            JsonNode modValue = modifiers.get(key);

            if (modValue.isObject()) {
                // 对象型 modifier: {"1K":0, "2K":10, "4K":20}
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
                // fall through: 返回原始 params
            }
        }
        return StrUtil.emptyToDefault(paramsStr, "{}");
    }

    @Override
    public boolean execute(AICallRequestDTO request) {

        String key = "user:points:" + request.getUserId();
        Integer cost = getModelCostAndInit(request);
        if (cost == null) {
            log.warn("图片模型调用失败:非法的模型或缺少template配置");
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
        Map<String, Object> body = buildRequestBodyFromTemplate(task);
        if (body == null) {
            log.warn("任务{}模型[{}]构建请求体失败", task.getOrderId(),task.getAIModel());
            return null;
        }
        log.warn("Image任务原始任务实体:{}",task);
        log.warn("Image任务原始请求体:{}",body);
        String response = dmxWebClient.post()
                .uri("/v1/responses")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        log.warn("ImageAPI原始响应：{}",response);
        String externalUrl = extractImageUrl(task, response);
        if (externalUrl == null) return null;
        return fetchToOSS(externalUrl, task);
    }

    private String fetchToOSS(String externalUrl, AICallTaskEntity task) {
        Result<String> OSSFetchResult;
        for (int i = 1; i <= 3; i++) {
            try {
                OSSFetchResult = qiniuOSS.fetchResource(externalUrl, task.getOrderId().toString(), task.getUserId());
            } catch (Exception e) {
                continue;
            }
            if (OSSFetchResult.getCode() == 200) {
                return OSSFetchResult.getData();
            }
        }
        log.warn("OSS尝试拉取临时图床URL失败。订单号:{}", task.getOrderId());
        return null;
    }

    private String extractImageUrl(AICallTaskEntity task, String responseBody) {

        if (StrUtil.hasBlank(task.getTemplate())) {
            log.warn("模型[{}]缺少template配置", task.getAIModel());
            return null;
        }
        try {
            JsonNode template = new ObjectMapper().readTree(task.getTemplate());
            JsonNode rpNode = template.get("responsePath");
            if (rpNode == null) {
                log.warn("模型[{}]缺少responsePath", task.getAIModel());
                return null;
            }


            Map<String, Object> params = parseParams(task.getParams(), task.getOrderId());
            //通过params的参数判断是否是i2i模式。
            boolean i2i =Boolean.TRUE.equals(params.get("i2i"));

            String jsonPath = i2i && rpNode.has("i2i")
                    ? rpNode.get("i2i").asText()
                    : rpNode.get("default").asText();

            //通过配置好的response路径解析出图片url。
            Object raw = JsonPath.parse(responseBody).read(jsonPath);
            String text = raw != null ? raw.toString() : null;

            /*
            * 后处理：如果返回值含 markdown图片链接语法，提取URL
            * 比如seedream返回的就是markdown格式。
            * TODO 但是这也可以被包含在template里面。
            * */
            if (text != null) {
                int start = text.indexOf("](");
                int end = text.lastIndexOf(")");
                if (start != -1 && end != -1) {
                    return text.substring(start + 2, end);
                }
            }
            return text;

        } catch (Exception e) {
            log.error("解析图片生成响应失败, model:{}, response:{}", task.getAIModel(), responseBody, e);
            return null;
        }
    }



    @SuppressWarnings("unchecked")
    private Map<String, Object> buildRequestBodyFromTemplate(AICallTaskEntity task) {
        String templateJson = task.getTemplate();
        if (templateJson == null) return null;

        Map<String, Object> params = parseParams(task.getParams(), task.getOrderId());

        //判断 t2i / i2i
        boolean i2i = Boolean.TRUE.equals(params.get("i2i"));

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(templateJson);
            JsonNode bodyTemplate = i2i
                    ? root.get("requestBody").get("i2i")
                    : root.get("requestBody").get("t2i");
            if (bodyTemplate == null) return null;

            //预编码替换
            String bodyStr = mapper.writeValueAsString(bodyTemplate);
            PropertyPlaceholderHelper helper = new PropertyPlaceholderHelper("${", "}", ":", null, true);
            String resolved = helper.replacePlaceholders(bodyStr, name -> {
                if ("model".equals(name)) return task.getAIModel();
                if ("prompt".equals(name)) return escapeJson(task.getPrompt());
                if (name.startsWith("params.")) {
                    String key = name.substring(7);
                    int bracket = key.indexOf('[');
                    if (bracket != -1) {
                        String arrayKey = key.substring(0, bracket);
                        int idx = Integer.parseInt(key.substring(bracket + 1, key.indexOf(']')));
                        Object val = params.get(arrayKey);
                        if (val instanceof List && idx < ((List<Object>) val).size()) {
                            Object elem = ((List<Object>) val).get(idx);
                            return elem != null ? escapeJson(elem.toString()) : null;
                        }
                        return null;
                    }
                    Object val = params.get(key);
                    return val != null ? escapeJson(val.toString()) : null;
                }
                return null;
            });

            JsonNode result = mapper.readTree(resolved);
            coerceTypes(result);
            return mapper.convertValue(result, Map.class);

        } catch (Exception e) {
            log.error("构建请求体失败, model:{}", task.getAIModel(), e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseParams(String paramsJson, Long orderId) {
        if (StrUtil.isEmpty(paramsJson)) return new HashMap<>();
        try {
            return new ObjectMapper().readValue(paramsJson, Map.class);
        } catch (Exception e) {
            log.warn("解析params失败, order:{}", orderId, e);
            return new HashMap<>();
        }
    }

    private String escapeJson(String raw) {
        if (raw == null) return null;
        return new String(JSON_ENCODER.quoteAsString(raw));
    }

    private void coerceTypes(JsonNode node) {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            Iterator<String> fields = obj.fieldNames();
            while (fields.hasNext()) {
                String key = fields.next();
                JsonNode child = obj.get(key);
                if (child.isTextual()) {
                    String text = child.asText();
                    if ("true".equals(text)) obj.set(key, BooleanNode.TRUE);
                    else if ("false".equals(text)) obj.set(key, BooleanNode.FALSE);
                    else {
                        try { obj.put(key, Integer.parseInt(text)); }
                        catch (NumberFormatException ignored) {}
                    }
                } else {
                    coerceTypes(child);
                }
            }
        } else if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            for (int i = 0; i < arr.size(); i++) {
                JsonNode child = arr.get(i);
                if (child.isTextual()) {
                    String text = child.asText();
                    if ("true".equals(text)) arr.set(i, BooleanNode.TRUE);
                    else if ("false".equals(text)) arr.set(i, BooleanNode.FALSE);
                    else {
                        try { arr.set(i, new IntNode(Integer.parseInt(text))); }
                        catch (NumberFormatException ignored) {}
                    }
                } else {
                    coerceTypes(child);
                }
            }
        }
    }
}
