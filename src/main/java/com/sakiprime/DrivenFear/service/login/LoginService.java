package com.sakiprime.DrivenFear.service.login;

import com.sakiprime.DrivenFear.entity.UserDTO;
import com.sakiprime.DrivenFear.entity.UserEntity;
import com.sakiprime.DrivenFear.common.util.Result;

/**
 * 登录服务
 *
 * @author 凋零
 * @since 2026/05/04
 */
public interface LoginService {
    /**
     * 合法性检查
     *
     * @param user 用户
     * @return {@link Result }<{@link Void }>
     *///校验注册信息合法性。
    Result<Void> LegalityCheck(UserDTO user);

    /**
     * 注册
     *
     * @param user 用户
     * @return {@link Result }<{@link Void }>
     */
    Result<Void> register(UserDTO user);

    /**
     * 登录
     *
     * @param id       标识符
     * @param password 密码
     * @return {@link Result }<{@link UserEntity }>
     */
    Result<UserEntity> login(String id, String password);

    /**
     * 通过用户id获取电子邮件
     *
     * @param userId 用户ID
     * @return {@link String }
     */
    String getEmailByUserId(String userId);

    /**
     * 通过电子邮件获取用户id
     *
     * @param email 电子邮件
     * @return {@link String }
     */
    String getUserIdByEmail(String email);
}
