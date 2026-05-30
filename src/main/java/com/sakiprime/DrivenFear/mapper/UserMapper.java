package com.sakiprime.DrivenFear.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakiprime.DrivenFear.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 用户映射器
 *
 * @author 凋零
 * @since 2026/05/06
 */
@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {
    /**
     * 扣除Token
     *
     * @param userId 用户ID
     * @param cost   成本
     * @return int
     */
    @Update("""
        UPDATE user_data
        SET token_balance = token_balance - #{cost}
        WHERE user_id = #{userId}
          AND token_balance >= #{cost}
    """)
    int deductToken(
            @Param("userId") String userId,
            @Param("cost") Integer cost
    );

    /**
     * 更新任务需要人工
     *
     * @param userId 用户ID
     * @param status 状态
     * @return int
     */
    @Update("""
    UPDATE user_data
    SET has_task_need_manual = #{status}
    WHERE user_id = #{userId}
""")
    int updateTaskNeedManual(
            @Param("userId") String userId,
            @Param("status") Boolean status
    );

    /**
     * 更新支付订单需要人工
     *
     * @param userId 用户ID
     * @param status 状态
     * @return int
     */
    @Update("""
    UPDATE user_data
    SET has_pay_order_need_manual = #{status}
    WHERE user_id = #{userId}
""")
    int updatePayOrderNeedManual(
            @Param("userId") String userId,
            @Param("status") Boolean status
    );

    /**
     * 通过电子邮件选择
     *
     * @param email 电子邮件
     * @return {@link UserEntity }
     */
    @Select("""
        SELECT * FROM user_data
        WHERE email = #{email}
    """)
    UserEntity selectByEmail(@Param("email") String email);

    /**
     * 取密码哈希（仅登录校验用，邮箱路线）
     *
     * @param email 电子邮件
     * @return {@link String } BCrypt哈希
     */
    @Select("SELECT password FROM user_data WHERE email = #{email}")
    String selectPasswordByEmail(@Param("email") String email);

    /**
     * 按id选择电子邮件
     *
     * @param userId 用户ID
     * @return {@link String }
     */
    @Select("""
        SELECT email FROM user_data
        WHERE user_id = #{userId}
        """)
    String selectEmailById(@Param("userId") String userId);

    /**
     * 通过电子邮件选择id
     *
     * @param email 电子邮件
     * @return {@link String }
     */
    @Select("""
        SELECT user_id
        FROM user_data
        WHERE email = #{email}
        """)
    String selectIdByEmail(@Param("email") String email);
    /**
     * 取密码哈希（仅LoginService密码校验用）
     *
     * @param userId 用户ID
     * @return {@link String } BCrypt哈希
     */
    @Select("SELECT password FROM user_data WHERE user_id = #{userId}")
    String selectPasswordById(@Param("userId") String userId);

    /**
     * 增加Token平衡原子
     *
     * @param userId 用户ID
     * @param tokens 令牌
     * @return int
     */
    @Update("UPDATE user_data " +
            "SET token_balance = token_balance + #{tokens} " +
            "WHERE user_id = #{userId}")
    int increaseTokenBalanceAtomic(
            @Param("userId") String userId,
            @Param("tokens") Integer tokens
    );

    /**
     * 更新头像密钥原子
     *
     * @param userId    用户ID
     * @param avatarKey 头像钥匙
     * @return int
     */
    @Update("""
    UPDATE user_data
    SET avatar_key = #{avatarKey}
    WHERE user_id = #{userId}
""")
    int updateAvatarKeyAtomic(
            @Param("userId") String userId,
            @Param("avatarKey") String avatarKey
    );

    /**
     * 更新用户名原子
     *
     * @param userId   用户ID
     * @param username 用户名
     * @return int
     */
    @Update("""
    UPDATE user_data
    SET username = #{username}
    WHERE user_id = #{userId}
""")
    int updateUsernameAtomic(
            @Param("userId") String userId,
            @Param("username") String username
    );

    /**
     * 更新手机原子
     *
     * @param userId 用户ID
     * @param phone  电话
     * @return int
     */
    @Update("""
    UPDATE user_data
    SET phone = #{phone}
    WHERE user_id = #{userId}
""")
    int updatePhoneAtomic(
            @Param("userId") String userId,
            @Param("phone") String phone
    );
}
