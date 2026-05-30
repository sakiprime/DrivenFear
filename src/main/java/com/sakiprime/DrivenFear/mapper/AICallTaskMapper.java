package com.sakiprime.DrivenFear.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakiprime.DrivenFear.entity.AICallTaskEntity;
import com.sakiprime.DrivenFear.entity.DashboardVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;


@Mapper
public interface AICallTaskMapper extends BaseMapper<AICallTaskEntity> {
    @Update("""
    UPDATE ai_call_task
    SET require_manual = #{status}
    WHERE order_id = #{orderId}
""")
    int updateTaskRequireManual(
            @Param("orderId") Long orderId,
            @Param("status") Boolean status
    );
    @Update("""
    UPDATE ai_call_task
    SET is_private = #{isPrivate}
    WHERE order_id = #{orderId}
      AND user_id = #{userId}
""")
    int updateTaskIsPrivate(
            @Param("orderId") Long orderId,
            @Param("userId") String userId,
            @Param("isPrivate") Boolean isPrivate
    );
    @Update("""
    UPDATE ai_call_task
    SET task_status = #{taskStatus}
    WHERE order_id = #{orderId}
""")
    int updateTaskStatus(
            @Param("orderId") Long orderId,
            @Param("taskStatus") String taskStatus
    );

    /** 管理后台概览 — 1 RTT 聚合查询 */
    @Select("""
        SELECT *
        FROM (
          SELECT
            (SELECT COUNT(*) FROM user_data) AS total_users,
            (SELECT COUNT(*) FROM user_data WHERE create_time >= NOW() - INTERVAL 7 DAY) AS new_users_7d
        ) base
        CROSS JOIN (
          SELECT
            COUNT(*) AS today_tasks,
            COUNT(IF(require_manual = 1, 1, NULL)) AS pending_task_manual,
            COALESCE(SUM(IF(DATE(create_time) = CURDATE(), token_cost, 0)), 0) AS tk_d0,
            COALESCE(SUM(IF(DATE(create_time) = CURDATE() - INTERVAL 1 DAY, token_cost, 0)), 0) AS tk_d1,
            COALESCE(SUM(IF(DATE(create_time) = CURDATE() - INTERVAL 2 DAY, token_cost, 0)), 0) AS tk_d2,
            COALESCE(SUM(IF(DATE(create_time) = CURDATE() - INTERVAL 3 DAY, token_cost, 0)), 0) AS tk_d3,
            COALESCE(SUM(IF(DATE(create_time) = CURDATE() - INTERVAL 4 DAY, token_cost, 0)), 0) AS tk_d4,
            COALESCE(SUM(IF(DATE(create_time) = CURDATE() - INTERVAL 5 DAY, token_cost, 0)), 0) AS tk_d5,
            COALESCE(SUM(IF(DATE(create_time) = CURDATE() - INTERVAL 6 DAY, token_cost, 0)), 0) AS tk_d6
          FROM ai_call_task
          WHERE create_time >= CURDATE() - INTERVAL 6 DAY
        ) tasks
        CROSS JOIN (
          SELECT
            COUNT(IF(require_manual = 1, 1, NULL)) AS pending_order_manual,
            COALESCE(SUM(IF(DATE(create_time) = CURDATE(), payment_amount, 0)), 0) AS pay_d0,
            COALESCE(SUM(IF(DATE(create_time) = CURDATE() - INTERVAL 1 DAY, payment_amount, 0)), 0) AS pay_d1,
            COALESCE(SUM(IF(DATE(create_time) = CURDATE() - INTERVAL 2 DAY, payment_amount, 0)), 0) AS pay_d2,
            COALESCE(SUM(IF(DATE(create_time) = CURDATE() - INTERVAL 3 DAY, payment_amount, 0)), 0) AS pay_d3,
            COALESCE(SUM(IF(DATE(create_time) = CURDATE() - INTERVAL 4 DAY, payment_amount, 0)), 0) AS pay_d4,
            COALESCE(SUM(IF(DATE(create_time) = CURDATE() - INTERVAL 5 DAY, payment_amount, 0)), 0) AS pay_d5,
            COALESCE(SUM(IF(DATE(create_time) = CURDATE() - INTERVAL 6 DAY, payment_amount, 0)), 0) AS pay_d6
          FROM user_recharge_order
          WHERE create_time >= CURDATE() - INTERVAL 6 DAY
            AND trade_status IN ('TRADE_SUCCESS', 'TRADE_FINISHED')
        ) orders
    """)
    DashboardVO selectDashboard();
}
