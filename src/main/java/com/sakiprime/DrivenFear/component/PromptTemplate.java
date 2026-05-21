package com.sakiprime.DrivenFear.component;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 提示模板
 *
 * @author 凋零
 * @since 2026/05/15
 */
@Component
public class PromptTemplate {


    /**
     * 任务类型
     *
     * @author 凋零
     * @since 2026/05/15
     */
    public enum TaskType {
        OPTIMIZE_IMAGE,
        OPTIMIZE_IMAGE_CREATIVE,
        REVERSE_IMAGE
    }

    /**
     * 优化图像提示
     */
    @Value("${prompt.template.optimize.image}")
    private String optimizeImagePrompt;

    /**
     * 优化图像提示创意
     */
    @Value("${prompt.template.optimize.image_creative}")
    private String optimizeImagePromptCreative;

    /**
     * 反转图像提示
     */
    @Value("${prompt.template.reverse.image}")
    private String reverseImagePrompt;


    /**
     * 获取模板
     *
     * @param taskType 任务类型
     * @return {@link String }
     */
    public String getTemplate(TaskType taskType) {
        return switch (taskType) {
            case OPTIMIZE_IMAGE -> optimizeImagePrompt;
            case OPTIMIZE_IMAGE_CREATIVE -> optimizeImagePromptCreative;
            case REVERSE_IMAGE -> reverseImagePrompt;
        };
    }


    /**
     * 获取模板(字符串入参)
     *
     * @param taskTypeStr 任务类型str
     * @return {@link String }
     */
    public String getTemplate(String taskTypeStr) {
        TaskType taskType;
        try {
            taskType = TaskType.valueOf(taskTypeStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("不支持的任务类型：" + taskTypeStr);
        }
        return getTemplate(taskType);
    }
}