package com.sakiprime.DrivenFear.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
@AllArgsConstructor
public enum TaskStatusEnum {

    PENDING("PENDING", "等待中"),
    PROCESSING("PROCESSING", "处理中"),
    SUCCESS("SUCCESS", "成功"),
    FAILED("FAILED", "失败");

    private final String code;
    private final String desc;

    private static final Map<String, TaskStatusEnum> CODE_CACHE = new HashMap<>();

    static {
        for (TaskStatusEnum status : values()) {
            CODE_CACHE.put(status.getCode(), status);
        }
    }

    public static TaskStatusEnum getByCode(String code) {
        return CODE_CACHE.get(code);
    }

    public static String getDescByCode(String code) {
        TaskStatusEnum status = CODE_CACHE.get(code);
        return status == null ? "未知状态" : status.getDesc();
    }

    public static boolean isValid(String code) {
        return CODE_CACHE.containsKey(code);
    }
}