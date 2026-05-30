package com.sakiprime.DrivenFear.entity;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class DashboardVO {
    /** 总用户数 */
    private Long totalUsers;
    /** 近7日注册数 */
    private Long newUsers7d;
    /** 今日任务数 */
    private Long todayTasks;
    /** 待处理任务申诉 */
    private Long pendingTaskManual;
    /** 待处理订单申诉 */
    private Long pendingOrderManual;

    /** 近七天Token消耗*/
    private BigDecimal tkD0;
    private BigDecimal tkD1;
    private BigDecimal tkD2;
    private BigDecimal tkD3;
    private BigDecimal tkD4;
    private BigDecimal tkD5;
    private BigDecimal tkD6;

    /** 近七天充值金额趋势 */
    private BigDecimal payD0;
    private BigDecimal payD1;
    private BigDecimal payD2;
    private BigDecimal payD3;
    private BigDecimal payD4;
    private BigDecimal payD5;
    private BigDecimal payD6;
}
