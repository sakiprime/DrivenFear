package com.sakiprime.DrivenFear.service.aicall.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.core.io.JsonStringEncoder;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

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
    private final ObjectMapper mapper;
    //@Value("${aimodelconfig.image.default-value}") 强契约架构下不应silent fallback
    //private Integer DEFAULT_MODEL_COST;

    public static volatile Map<String, JsonNode> IMAGE_MODEL_TEMPLATE_MAP = new ConcurrentHashMap<>();

    private static final JsonStringEncoder JSON_ENCODER = JsonStringEncoder.getInstance();

    /**
     * 获取图像模型模板映射
     *
     * @return {@link Map }<{@link String }, {@link JsonNode }>
     */
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

    /**
     * 刷新模型模板映射
     *
     * @return boolean
     */
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

    private static final String DEDUCT_TOKEN_LUA_SCRIPT =
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
     * 获取模型token成本和初始化参数
     *
     * @param request 请求
     * @return {@link Integer }
     */
    private Integer getModelCostAndInit(AICallRequestDTO request) {
        JsonNode template = IMAGE_MODEL_TEMPLATE_MAP.get(request.getAIModel());
        if (template == null) return null;

        //用paramsSchema.default填充遗漏参数，保证costFormula和requestBody用同一份参数
        JSONObject params = fillDefaultParams(request.getParams(), template.get("paramsSchema"));
        if (params == null) {
            //这里的null会让任务在execute入口处快返。
            log.warn("图片任务模板params初始化失败，任务模型{}",request.getAIModel());
            return null;
        }
        request.setParams(params.toString());
        //后续在转化为entity后template会存在数据库，可用于追溯。
        request.setTemplate(template.toString());

        JsonNode costFormula = template.get("costFormula");
        if (costFormula == null) {
            log.warn("图片任务模板无costFormula，任务模型{}",request.getAIModel());
            return null;
        }

        int base = costFormula.get("base").asInt(-1);
        if (base < 0) {
            log.warn("图片任务模板costFormula-baseToken异常，任务模型{}", request.getAIModel());
            return null;
        }
        JsonNode modifiers = costFormula.get("modifiers");
        if (modifiers == null) return base;

        int extra = 0;
        Iterator<String> modKeys = modifiers.fieldNames();
        //小小迭代器。从costFormula的可选计费参数出发索引request的params字段。
        while (modKeys.hasNext()) {
            String key = modKeys.next();
            JsonNode modValue = modifiers.get(key);

            if (modValue.isObject()) {
                //对象型select-modifier比如: {"1K":0, "2K":10, "4K":20}，匹配具体value。
                Object paramVal = params.get(key);
                if (paramVal != null) {
                    JsonNode match = modValue.get(paramVal.toString());
                    if (match != null && match.isNumber()) {
                        extra += match.asInt(0);
                    }
                    else {
                        log.warn("图片生成任务不合法的costFormula-modifiers-value类型。任务模型{},异常字段{}",request.getAIModel(),modValue);
                    }
                }
            }
            //标量modifiers，比如："i2i":"10"，意为图生图链路额外增加10token花费，直接+=value即可。这是人类写的注释。
            else if (modValue.isNumber()) {
                Object val = params.get(key);
                if (val != null && !"false".equals(val.toString())) {
                    extra += modValue.asInt(0);
                }
            }
        }

        return base + extra;
    }


    /**
     * 初始化默认task参数
     *这里返回体选用JSONObject是因为之后要.toString()序列化回存request。
     *
     * @param paramsStr    params str
     * @param paramsSchema params架构
     * @return {@link JSONObject }
     */
    private JSONObject fillDefaultParams(String paramsStr, JsonNode paramsSchema) {
        //校验paramsSchema是否存在，否则返回透传null快返。任务会被终结在execute入口。
        if (paramsSchema == null) return null;

        //反序列化拿取params和paramsSchema
        JSONObject schema = JSONUtil.parseObj(paramsSchema.toString());
        JSONObject params;
        try {
            params = StrUtil.isNotEmpty(paramsStr)
                    ? JSONUtil.parseObj(paramsStr)
                    : JSONUtil.createObj();
        } catch (Exception e) {
            return null;
        }

        //小小迭代器，从schema约定的params子项出发校验并初始化task的params。
        for (String key : schema.keySet()) {
            if (params.get(key) != null) continue;
            Object defVal = schema.getJSONObject(key).get("default");
            if (defVal == null) continue;
            params.set(key, defVal);
        }

        return params;
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
        RedisScript<Long> script = RedisScript.of(DEDUCT_TOKEN_LUA_SCRIPT, Long.class); //可以移到方法外
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

    /**
     * 拉取第三方资源到OSS存储。
     *
     * @param externalUrl 外部url
     * @param task        任务
     * @return {@link String }
     */
    private String fetchToOSS(String externalUrl, AICallTaskEntity task) {
        Result<String> OSSFetchResult;
        for (int i = 1; i <= 3; i++) {
            try {
                OSSFetchResult = qiniuOSS.fetchResource(externalUrl, task.getOrderId().toString(), task.getUserId());
            } catch (Exception e) {
                if(i == 3) log.warn("拉取API响应图床Url到OSS存储失败。",e);
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
            JsonNode template = mapper.readTree(task.getTemplate());
            JsonNode rpNode = template.get("responsePath");
            if (rpNode == null) {
                log.warn("模型[{}]缺少responsePath", task.getAIModel());
                return null;
            }


            Map<String, Object> params = parseParams(task.getParams(), task.getOrderId());
            //通过params的参数判断是否是i2i模式，然后取对应解析模板。
            //TODO
            boolean i2i =Boolean.TRUE.equals(params.get("i2i"));

            String jsonPath = i2i && rpNode.has("i2i")
                    ? rpNode.get("i2i").asText()
                    : rpNode.get("default").asText();

            //通过配置好的response路径解析出图片url，直接通过JsonPath精准索引。
            //TODO 对于多图生成支持性不良。现有template约定了单图生成，不过之间没有因果性。
            Object raw = JsonPath.parse(responseBody).read(jsonPath);
            String text = raw != null ? raw.toString() : null;

            /*
            * 后处理：如果返回值含 markdown图片链接语法，提取URL
            * 比如seedream返回的就是markdown格式。
            * TODO 但是这个分支也可以被包含在template里面。
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


    /**
     * 从模板构建请求体
     *
     * @param task 任务
     * @return {@link Map }<{@link String }, {@link Object }>
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildRequestBodyFromTemplate(AICallTaskEntity task) {
        String templateJson = task.getTemplate();
        if (templateJson == null) return null;

        Map<String, Object> params = parseParams(task.getParams(), task.getOrderId());

        //判断t2i/i2i参数
        boolean i2i = Boolean.TRUE.equals(params.get("i2i"));

        try {
            JsonNode root = mapper.readTree(templateJson);
            //获取对应响应体模板，t2i任务无法强塞refImage来白嫖i2i额外费用。
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

                    //List: ${params.list:xxx?key=yyy} → 哨兵，最后一行正则展开
                    if (key.startsWith("list:")) {
                        return resolveListSentinel(key.substring(5), params);
                    }

                    //标量数组下标: ${params.xxx[0]} ，可能是冗余的。因为单标量无需数组，多标量用List。
                    int bracket = key.indexOf('[');
                    if (bracket != -1) {
                        String arrayKey = key.substring(0, bracket);
                        int idx = Integer.parseInt(key.substring(bracket + 1, key.indexOf(']')));
                        Object val = params.get(arrayKey);
                        if (val instanceof List) {
                            Object elem = ((List<Object>) val).get(idx);
                            return elem != null ? escapeJson(elem.toString()) : null;
                        }
                        return null;
                    }

                    // 标量: ${params.width}
                    Object val = params.get(key);
                    return val != null ? escapeJson(val.toString()) : null;
                }
                return null;
            });

            //TODO List哨兵展开: "[[SENT:xxx]]" → 数组JSON（外层引号被正则吃掉）
            resolved = LIST_SENTINEL.matcher(resolved).replaceAll(match -> {
                String paramKey = match.group(1);
                String wrapKey = match.group(2);
                Object val = params.get(paramKey);
                if (val instanceof List<?> list && !list.isEmpty()) {
                    if (wrapKey != null) {
                        List<Map<String, Object>> wrapped = new ArrayList<>();
                        for (Object item : list) wrapped.add(Map.of(wrapKey, item));
                        return JSONUtil.toJsonStr(wrapped);
                    }
                    return JSONUtil.toJsonStr(list);
                }
                return "[]";
            });

            return JSONUtil.parseObj(resolved);

        } catch (Exception e) {
            log.error("构建请求体失败, model:{}", task.getAIModel(), e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseParams(String paramsJson, Long orderId) {
        //反序列化器，task中的params为JSON格式需解析为map。支持空params入参，PPH有默认值。计费和默认值统一。
        //事实上，getModelCostAndInit方法会在execute方法开头初始化参数，空params入参为兜底。
        if (StrUtil.isEmpty(paramsJson)) return new HashMap<>();
        try {
            return mapper.readValue(paramsJson, Map.class);
        } catch (Exception e) {
            log.warn("解析params失败, order:{}", orderId, e);
            return new HashMap<>();
        }
    }

    private String escapeJson(String raw) {
        if (raw == null) return null;
        return new String(JSON_ENCODER.quoteAsString(raw));
    }

    private static final Pattern LIST_SENTINEL = Pattern.compile("\"\\[\\[SENT:(\\w+)(?::(\\w+))?]]\"");

    private String resolveListSentinel(String raw, Map<String, Object> params) {
        String wrapKey = null;
        int qIdx = raw.indexOf("?key=");
        if (qIdx != -1) {
            wrapKey = raw.substring(qIdx + 5);
            raw = raw.substring(0, qIdx);
        }
        return "[[" + "SENT:" + raw + (wrapKey != null ? ":" + wrapKey : "") + "]]";
    }

}
