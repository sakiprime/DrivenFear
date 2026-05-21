package com.sakiprime.DrivenFear.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakiprime.DrivenFear.entity.UserRechargeOrderEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserRechargeMapper extends BaseMapper<UserRechargeOrderEntity> {
    @Update("""
    UPDATE user_recharge_order
    SET require_manual = #{status}
    WHERE order_id = #{orderId}
""")
    int updateRechargeOrderRequireManual(
            @Param("orderId") Long orderId,
            @Param("status") Boolean status
    );
}
