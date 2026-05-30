package com.sakiprime.DrivenFear.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * aicall请求dto
 *
 * @author 凋零
 * @since 2026/05/04
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AICallRequestDTO {
    /**
     * 订单号
     */
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
     * 预编码模板JSON（服务端注入，不来自前端）
     */
    private String template;
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

}
