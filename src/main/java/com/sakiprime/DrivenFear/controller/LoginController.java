package com.sakiprime.DrivenFear.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.sakiprime.DrivenFear.annotation.ApiRateLimit;
import com.sakiprime.DrivenFear.component.MailDirectComponent;
import com.sakiprime.DrivenFear.entity.UserDTO;
import com.sakiprime.DrivenFear.entity.UserEntity;
import com.sakiprime.DrivenFear.service.login.LoginService;
import com.sakiprime.DrivenFear.common.util.Result;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 登录控制器
 *
 * @author 凋零
 * @since 2026/05/04
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@Validated
public class LoginController {
    private final LoginService loginService;
    private final MailDirectComponent mailDirectComponent;

    /**
     * 发送邮件验证:注册
     *
     * @param user 用户
     * @return {@link Result }<{@link Void }>
     */
    //注册场景时，邮箱地址直接从前端获取。
    @PostMapping("/verification/email/code/register")
    @ApiRateLimit(interFace = "sendMailVerificationReg",ipLimit = 3)
    public Result<Void> sendMailVerificationReg(@Validated(
            UserDTO.RegisterGroup.class) @RequestBody UserDTO user) {

        return mailDirectComponent.
                sendVerificationMail(user.getEmail(),"注册验证码",user.getUserId());
    }

    /**
     * 验证电子邮件代码:注册
     *
     * @param user 用户
     * @return {@link Result }<{@link Void }>
     */
    @PostMapping("/verification/email/verify/register")
    @ApiRateLimit(interFace = "verifyEmailCodeReg",ipLimit = 9)
    public Result<Void> verifyEmailCodeReg(@Validated(
            {UserDTO.RegisterGroup.class,UserDTO.MFAGroup.class})@RequestBody UserDTO user){

        return mailDirectComponent.verifyMailCode(user.getMailCode(),
                user.getUserId(),user.getFingerPrint(),user.getEmail());
    }

    /**
     * 发送邮件验证
     *
     * @param userId 用户ID
     * @return {@link Result }<{@link Void }>
     *///非注册场景时，邮箱只从DB获取，防止通过验证别的邮箱绕过。
    @PostMapping("/verification/email/code")
    @ApiRateLimit(interFace = "sendMailVerification",ipLimit = 3)
    public Result<Void> sendMailVerification(@NotEmpty(
            message = "账号不能为空。") String userId) {

        String email = loginService.getEmailByUserId(userId);
        if (email==null){
            return Result.fail(400,"此账号尚未注册。");
        }
        return mailDirectComponent.
                sendVerificationMail(email,
                        "登录验证码",userId);
    }

    /**
     * 验证电子邮件代码
     *
     * @param user 用户
     * @return {@link Result }<{@link Void }>
     */
    @PostMapping("/verification/email/verify")
    @ApiRateLimit(interFace = "verifyEmailCode",ipLimit = 9)
    public Result<Void> verifyEmailCode(
            @Validated({UserDTO.LoginGroup.class,UserDTO.MFAGroup.class})@RequestBody UserDTO user){

        String email = loginService.getEmailByUserId(user.getUserId());
        if (email==null){
            return Result.fail(400,"此账号尚未注册。");
        }
        return mailDirectComponent.verifyMailCode(user.getMailCode(),
                user.getUserId(),user.getFingerPrint(),email);
    }


    /**
     * 注册
     *
     * @param user 用户
     * @return {@link Result }<{@link Void }>
     */
    @PostMapping("/register")
    @ApiRateLimit(interFace = "register",ipLimit = 5)
    public Result<Void> register(
            @Validated(UserDTO.RegisterGroup.class)@RequestBody UserDTO user){
        //首先效验邮箱验证状态。
        Result<Void> result = mailDirectComponent.verifyEmailMFASafe(user.getEmail());
        if (result.getCode()!= 200) return result;
        Result<Void> legalityCheck = loginService.LegalityCheck(user);
        if(legalityCheck.getCode()!=200){
            return legalityCheck;
        }
        Result<Void> registerResult = loginService.register(user);
        if(registerResult.getCode()!=200){
            return registerResult;
        }
        //业务成功后再删除验证状态（自然情况5分钟过期），防止用户需要多次收发验证码降低体验。
        mailDirectComponent.deleteEmailMFASafe(loginService.getEmailByUserId(user.getUserId()));
        //现在这个方法实质上也不再被需要了。
        //不登录顶号的话safe状态会继续存续。但是不登录的话，MFA的safe状态的用法只有注册账号——还不能重复邮箱。没用。
        return Result.success(null);
    }

    /**
     * 登录
     *
     * @param user 用户
     * @return {@link Result }<{@link Void }>
     */
    @PostMapping("/login")
    @ApiRateLimit(interFace = "login",ipLimit = 10)
    public Result<Void> login(
            @Validated({UserDTO.LoginGroup.class})@RequestBody UserDTO user) {
        //Redis中没有对应设备指纹/指纹已过期的时候，触发风控。前端收到该result后会跳转到MFALogin页。
        Result<Void> verifyResult = mailDirectComponent.isNeedMFA(user.getUserId(),user.getFingerPrint());
        if (verifyResult.getCode()!= 200) return verifyResult;
        Result<UserEntity> loginResult = loginService.login(user.getUserId(), user.getPassword());
        if(loginResult.getCode() != 200){
            return Result.fail(500,"账号或密码错误");
        }

        StpUtil.login(loginResult.getData().getUserId());
        //把用户数据（不含密码）缓存到session中。
        StpUtil.getSessionByLoginId(loginResult.getData().getUserId())
                .set("loginUser", loginResult.getData());

        return Result.success(null);

    }

    /**
     * 使用mfa登录
     *
     * @param user 用户
     * @return {@link Result }<{@link Void }>
     */
    @PostMapping("/login-mfa")
    @ApiRateLimit(interFace = "login-mfa",ipLimit = 10)
    public Result<Void> loginWithMFA(
            @Validated(UserDTO.LoginGroup.class)@RequestBody UserDTO user) {
        //登录触发MFA时，无需用户手动输入email。也不能....
        String email = loginService.getEmailByUserId(user.getUserId());
        if (email==null){
            return Result.fail(400,"此账号尚未注册。");
        }
        Result<Void> verifyResult = mailDirectComponent
                .verifyEmailMFASafe(email);
        if (verifyResult.getCode()!= 200) return verifyResult;
        Result<UserEntity> loginResult = loginService.login(user.getUserId(), user.getPassword());
        if(loginResult.getCode() != 200){
            return Result.fail(500,"账号或密码错误");
        }

        StpUtil.login(loginResult.getData().getUserId());
        //把用户数据（不含密码）缓存到session中。
        StpUtil.getSessionByLoginId(loginResult.getData().getUserId())
                .set("loginUser", loginResult.getData());

        return Result.success(null);
    }

    /**
     * 登出
     *
     * @return {@link Result }<{@link Void }>
     */
    @PostMapping("/logout")
    @ApiRateLimit(interFace = "logout",ipLimit = 10)
    public Result<Void> logout() {
        StpUtil.logout();
        return Result.success("退出登录成功", null);
    }

}
