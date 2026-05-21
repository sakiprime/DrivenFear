package com.sakiprime.DrivenFear.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakiprime.DrivenFear.entity.AICallTaskEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
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
}
