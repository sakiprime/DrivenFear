package com.sakiprime.DrivenFear.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * aicall任务实体
 *
 * @author 凋零
 * @since 2026/05/04
 */
@Data
@NoArgsConstructor
@TableName("ai_call_task")
public class AICallTaskEntity {
    /**
     * 订单号
     */
    @TableId
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;
    /**
     * 用户ID
     */
    private String userId;
    /**
     * 提示
     */
    private String prompt;
    /**
     * aimodel
     */
    @TableField("ai_model")
    @JsonProperty("AIModel")
    private String AIModel;//记得校验合法性。
    /**
     * Token成本
     */
    private Integer tokenCost;//该变量不从前端中获取，也无法被篡改。
    /**
     * 任务类型
     */
    private String taskType;
    /**
     * 参数
     */
    private String params;
    /**
     * 预编码模板JSON
     */
    private String template;
    /**
     * 外部供应商的任务ID（用于异步查询结果）
     */
    private String externalTaskId;
    /**
     * 作品描述
     */
    private String taskDescription;
    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    /**
     * 文本内容
     */
    private String textMessage;
    /**
     * 图片网址
     */
    private String imageUrl;
    /**
     * 视频url
     */
    private String videoUrl;
    /**
     * 任务状态
     */
    private String taskStatus;
    /**
     * 要求人工
     */
    private Boolean requireManual = false;
    /**
     * 是私人
     */
    private Boolean isPrivate = false;
    /**
     * 热度
     */
    private Integer heatScore = 0;

    /**
     * aicall任务实体
     *
     * @param aicCallRequestDTO aic呼叫请求dto
     */
    public AICallTaskEntity(AICallRequestDTO aicCallRequestDTO) {
        this.orderId = aicCallRequestDTO.getOrderId();
        this.userId = aicCallRequestDTO.getUserId();
        this.prompt = aicCallRequestDTO.getPrompt();
        this.AIModel = aicCallRequestDTO.getAIModel();
        this.tokenCost = aicCallRequestDTO.getTokenCost();
        this.taskType = aicCallRequestDTO.getTaskType();
        this.params = aicCallRequestDTO.getParams();
        this.template = aicCallRequestDTO.getTemplate();
        this.taskDescription = aicCallRequestDTO.getTaskDescription();
        this.createTime = aicCallRequestDTO.getCreateTime();
        this.updateTime = aicCallRequestDTO.getUpdateTime();
        this.taskStatus = "PENDING";
    }
}
