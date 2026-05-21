package com.sakiprime.DrivenFear.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("user_recharge_order")
public class UserRechargeOrderEntity {
    /**
     * 订单号
     */
    @TableId
    private Long orderId;//订单ID
    /**
     * 标识符
     */
    private Long id;//商品ID
    /**
     * 用户ID
     */
    private String userId;//用户ID
    /**
     * 付款金额
     *///以下两个成员变量不来自前端，并会在执行中被商品ID对应的正确数据覆写。(你觉得我写注释很像AI?)
    private BigDecimal paymentAmount;//付款金额（元）
    /**
     * 代币金额
     */
    private Integer tokensAmount;//获得的token数

    /**
     * 要求人工
     */
    private Boolean requireManual = false;
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
     * 付款时间
     */
    private LocalDateTime paymentTime;
    /**
     * 贸易状态
     */
    private String tradeStatus;
    /**
     * 返回网址
     */
    private String returnUrl;
    /**
     * 版本
     */
    @Version
    private Integer version;
}
