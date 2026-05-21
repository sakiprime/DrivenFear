package com.sakiprime.DrivenFear.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 发电机请求
 *
 * @author 凋零
 * @since 2026/05/11
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AICallFastRequest {

    private String userPrompt;
    private String taskType;
    private String systemPrompt;
    private String modelName ="DeepSeekV4-flash";
    private Double temperature;
    private Double topP;
    private Integer maxTokens;
    private Integer contextWindow;
    private Boolean creativeMode = true;

    /**
     * 转换成ollmaMap
     *
     * @return {@link Map }<{@link String }, {@link Object }>
     */
    public Map<String, Object> toOllamaMap() {
        Map<String, Object> requestMap = new HashMap<>();
        if (modelName == null) {
            modelName = "gemma4-fast:latest";
        }
        requestMap.put("model", this.modelName);
        requestMap.put("system", this.systemPrompt);
        requestMap.put("prompt", this.userPrompt);
        requestMap.put("stream", false);
        requestMap.put("think", false);

        Map<String, Object> options = new HashMap<>();

        if (Boolean.TRUE.equals(this.creativeMode)) {
            if (this.temperature == null) options.put("temperature", 1.0);
            if (this.topP == null) options.put("top_p", 0.9);
            options.put("top_k", 85);
        } else {
            if (this.temperature == null) options.put("temperature", 0.5);
            if (this.topP == null) options.put("top_p", 0.2);
            options.put("top_k", 50);
        }


        requestMap.put("options", options);
        return requestMap;
    }
}
