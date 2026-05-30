package com.sakiprime.DrivenFear.entity;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserDTO {
    public interface LoginGroup {}
    public interface RegisterGroup {}
    public interface MFAGroup {}


    /**
     * 用户ID
     */
    @NotEmpty(groups = {LoginGroup.class, RegisterGroup.class},message = "账号不能为空。")
    @Pattern(regexp = "^[a-zA-Z0-9_]{5,20}$",
            message = "账号长度不符合规范，或包含特殊字符", groups = {//LoginGroup.class, 因为要接轨邮箱登录暂时注释掉。
            RegisterGroup.class})
    @Pattern(regexp = "^[a-zA-Z0-9_@.-]{4,100}$",
            message = "账号长度不符合规范，或包含特殊字符", groups = {
            //更宽泛能同时满足email和userId的正则。由于注册的时候账号是严格正则，所以login场景包含@的userId必然是邮箱。
            LoginGroup.class})
    private String userId;


    /**
     * 电子邮件
     */
    @NotEmpty(groups = {RegisterGroup.class},message = "邮箱不能为空。")
    @Pattern(regexp = "^[\\w.-]{1,30}@[\\w.-]{1,20}\\.[\\w.-]{2,10}$",
            message = "邮箱账号长度不符合规范，或包含特殊字符", groups = {RegisterGroup.class})
    private String email;


    /**
     * 密码
     */
    @NotEmpty(groups = {LoginGroup.class, RegisterGroup.class},message = "密码不能为空。")
    @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*\\d)[A-Za-z0-9!@#$%^*()_.?-]{8,40}$",
            message = "密码长度不符合规范，或包含特殊字符。", groups = {LoginGroup.class, RegisterGroup.class})
    private String password;


    /**
     * 邮件代码
     */
    @NotEmpty(groups = {MFAGroup.class}, message = "验证码不能为空。")
    private String mailCode;

    /**
     * 指纹
     */
    @NotEmpty(groups = {LoginGroup.class, RegisterGroup.class}, message = "设备指纹异常，请刷新页面。")
    private String fingerPrint;

    /**
     * 电话
     */
    private String phone;
    /**
     * 用户名
     */
    private String username;
}
