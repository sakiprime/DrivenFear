package com.sakiprime.DrivenFear.component;
import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.DesensitizedUtil;
import com.aliyun.dm20151123.Client;
import com.aliyun.tea.TeaException;
import com.sakiprime.DrivenFear.common.util.Result;
import com.sakiprime.DrivenFear.service.login.LoginService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 邮件验证组件
 *
 * @author 凋零
 * @since 2026/05/06
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MailDirectComponent {
    private final Client client;
    private final LoginService loginService;
    @Value("${aliyun.dm.accountName}")
    private String accountName;
    //采用常量。我觉得更专业。😈验证码过期时间实际为六分钟是为了留出冗余。
    private static final long EXPIRE_TIME = 6*60;
    public static final long TRUSTED_DEVICE_EXPIRE_SECONDS = 14 * 24 * 60 * 60;

    /**
     * 获取mfacode密钥
     *
     * @param userId 用户ID
     * @param email  电子邮件
     * @return {@link String }
     *///Redis的Key从本方法中获取，不在使用的方法中拼接，保证一致性。
    private String getMFACodeKey(String userId, String email){
        String tokenValue = StpUtil.getTokenValue();
        return userId+":"+email+":mfa_email_code:"+tokenValue;
    }

    /**
     * 获取mfatrusted设备密钥
     *
     * @param userId      用户ID
     * @param fingerPrint 指纹
     * @return {@link String }
     */
    private String getMFATrustedDeviceKey(String userId, String fingerPrint){
        return userId+":mfa_trusted_device:"+fingerPrint;
    }

    /**
     * 设置mfacode密钥
     *
     * @param userId   用户ID
     * @param mailCode 邮件代码
     * @param email    电子邮件
     * @return boolean
     */
    private boolean setMFACodeKey(String userId, String mailCode, String email){
        String key = getMFACodeKey(userId,email);
        for(int i=0;i<2;i++){
            try{
                SaManager.getSaTokenDao().set(key, mailCode, EXPIRE_TIME);
                if(SaManager.getSaTokenDao().get(key)!=null){
                    return true;
                }
            }
            catch (Exception e){
                log.error("MFA验证码Token设置出错,用户：{}",userId,e);
            }
        }
        return false;
    }

    /**
     * 打开mfasafe
     *
     * @param service 服务
     * @param id      标识符
     */
    private void openMFASafe(String service, String id){//以后可以拓展新的MFA方法。
        StpUtil.openSafe(service+id, EXPIRE_TIME);
    }

    /**
     * 设置受信任设备
     *
     * @param userId      用户ID
     * @param fingerPrint 指纹
     */
    private void setTrustedDevice(String userId, String fingerPrint){

        String key = getMFATrustedDeviceKey(userId,fingerPrint);
        for(int i=0;i<2;i++){
            try{
                SaManager.getSaTokenDao().set(key, "true", TRUSTED_DEVICE_EXPIRE_SECONDS);
                if(SaManager.getSaTokenDao().get(key)!=null){
                    return;
                }
            }
            //当设备信任状态失败时，应当降级。这不该太多影响主进程。
            catch (Exception e){
                log.error("设备信任状态设置出错,用户：{}",userId,e);
            }
        }
    }

    /**
     * 发送验证邮件
     *
     * @param ToAddress 目标地址
     * @param subject   主题
     * @param userId    用户ID
     * @return {@link Result }<{@link Void }>
     *///主要的方法：发送邮件验证码。
    public Result<Void> sendVerificationMail(String ToAddress, String subject, String userId) {

        String mailCode = String.format("%06d", ThreadLocalRandom.current().nextInt(100_000, 1_000_000));
        if(!setMFACodeKey(userId, mailCode,ToAddress)){//先行设置token内MFA状态的轻操作。
            return Result.fail(500,"数据异常，请重试。");
        }

        com.aliyun.dm20151123.models.SingleSendMailRequest singleSendMailRequest = new com.aliyun.dm20151123.models.SingleSendMailRequest()
                .setReplyToAddress(false)
                .setAccountName(accountName)
                .setAddressType(1)
                .setToAddress(ToAddress)
                .setSubject(subject)
                .setHtmlBody("""
                        <p style="font-family: 'Microsoft YaHei', sans-serif; font-size: 16px; color: #333;">
                        您好，您的 DrivenFear 验证码是：
                </p>
                <p style="font-size: 24px; font-weight: bold; color: #1677ff; margin: 20px 0;">"""
                        +mailCode+
                        """
                </p>
                <p style="font-size: 14px; color: #666;">
        验证码有效期为5分钟，请勿泄露给他人。<br>
                如果不是您本人操作，请忽略此邮件。
</p>""")
                .setTextBody("""
                        您好，您的 DrivenFear 验证码是："""
                        +mailCode+"""
                        验证码有效期为5分钟，请勿泄露给他人。
                        如果不是您本人操作，请忽略此邮件。""")
                                .setFromAlias("sakiprime.cn");
        com.aliyun.teautil.models.RuntimeOptions runtime = new com.aliyun.teautil.models.RuntimeOptions();
        try {
            com.aliyun.dm20151123.models.SingleSendMailResponse resp = client.singleSendMailWithOptions(singleSendMailRequest, runtime);
            System.out.println(new com.google.gson.Gson().toJson(resp));
        }
        //当邮件未成功发送时，先前设置的codeKey会在五分钟内自动过期。
        //用户此时应当会在引导下重新请求发送邮件，而使用随机验证码尝试会返回验证码错误。多次会被防刷器拦截。
        catch (TeaException error) {
            log.error("[DirectMail调用异常]验证邮件发送失败,错误信息：{},主题:{},用户id:{},目标邮箱地址:{}",
                    error.getMessage(),subject,userId,ToAddress);
            log.error("{}",error.getData().get("Recommend"));
            //return Result.fail(500,error.getMessage());不应该给前端展示错误类型.....
            return Result.fail(500,"系统繁忙，请稍后重试");
        }
        catch (Exception _error) {
            TeaException error = new TeaException(_error.getMessage(), _error);
            log.error("[系统异常]验证邮件发送失败,错误信息：{},主题:{},用户id:{},目标邮箱地址:{}",
                    error.getMessage(),subject,userId,ToAddress);
            log.error("{}",error.getData().get("Recommend"));
            return Result.fail(500,"系统繁忙，请稍后重试");
        }
        String desensitizedEmail = DesensitizedUtil.email(ToAddress);
        //成功时返回提示信息。邮箱已去敏。赞美hutool!
        return Result.success("已向您的邮箱:"+desensitizedEmail
                +"发送验证码。若未收到则请检查垃圾邮件。",null);
    }

    public Result<Void> sendNotificationMail(String ToAddress, String taskType, Long orderId,
                                             String time, Integer tokenCost, String userId) {

    String subject = "DrivenFear生成任务完成通知";


        com.aliyun.dm20151123.models.SingleSendMailRequest singleSendMailRequest = new com.aliyun.dm20151123.models.SingleSendMailRequest()
                .setReplyToAddress(false)
                .setAccountName(accountName)
                .setAddressType(1)
                .setToAddress(ToAddress)
                .setSubject(subject)
                .setHtmlBody("""
                    <p style="font-family: 'Microsoft YaHei', sans-serif; font-size: 16px; color: #333;">
                    您好，您在 %s 创建的 %s 生成任务（订单号：%d）已经处理完成。<br>
                    DrivenFear平台感谢您的支持。
                    </p>
                    <p style="font-size: 16px; font-weight: bold; color: #333; margin: 20px 0;">
                    本次共消耗 Token：<span style="color: #1677ff; font-size: 20px;">%d</span>
                    </p>
                    <p style="font-size: 14px; color: #666;">
                    如有疑问，请登录系统查看详情。<br>
                    如果不是您本人操作，请忽略此邮件。
                    </p>""".formatted(time, taskType, orderId, tokenCost))
                .setTextBody("""
                    您好，您在 %s 创建的 %s 生成任务（订单号：%d）已经处理完成。
                    DrivenFear平台感谢您的支持。
        
                    本次共消耗 Token：%d
        
                    如有疑问，请登录系统查看详情。
                    如果不是您本人操作，请忽略此邮件。""".formatted(time, taskType, orderId, tokenCost))
                .setFromAlias("sakiprime.cn");
        com.aliyun.teautil.models.RuntimeOptions runtime = new com.aliyun.teautil.models.RuntimeOptions();
        try {
            com.aliyun.dm20151123.models.SingleSendMailResponse resp = client.singleSendMailWithOptions(singleSendMailRequest, runtime);
            System.out.println(new com.google.gson.Gson().toJson(resp));
        }
        //当邮件未成功发送时，先前设置的codeKey会在五分钟内自动过期。
        //用户此时应当会在引导下重新请求发送邮件，而使用随机验证码尝试会返回验证码错误。多次会被防刷器拦截。
        catch (TeaException error) {
            log.error("[DirectMail调用异常]生成任务完成通知邮件发送失败,错误信息：{},任务订单号:{},用户id:{},目标邮箱地址:{}",
                    error.getMessage(),orderId,userId,ToAddress);
            //return Result.fail(500,error.getMessage());不应该给前端展示错误类型.....
            return Result.fail(500,"[DirectMail调用异常]生成任务完成通知邮件发送失败");
        }
        catch (Exception _error) {
            TeaException error = new TeaException(_error.getMessage(), _error);
            log.error("[系统异常]生成任务完成通知邮件发送失败,错误信息：{},任务订单号:{},用户id:{},目标邮箱地址:{}",
                    error.getMessage(),orderId,userId,ToAddress);
            return Result.fail(500,"[系统异常]生成任务完成通知邮件发送失败");
        }

        return Result.success(null);
    }
    /**
     * 验证邮件代码
     *
     * @param mailCode    邮件代码
     * @param userId      用户ID
     * @param fingerPrint 指纹
     * @param email       电子邮件
     * @return {@link Result }<{@link Void }>
     */
    public Result<Void> verifyMailCode(String mailCode,String userId,String fingerPrint,String email) {
        String codeKey = getMFACodeKey(userId,email);
        String MFAcode =SaManager.getSaTokenDao().get(codeKey);
        if(MFAcode ==null){//如果用户从未请求验证码，也会显示过期。这应该是前端要做的小检验。
            return Result.fail(401,"验证码已过期，请重试。");
        }
        if(!MFAcode.equals(mailCode)){
            return Result.fail(422,"验证码错误，请重试。");
        }
        SaManager.getSaTokenDao().delete(codeKey);
        //创建一个临时登录账号。由于冒号不在合法字符里，所以临时登录账号永不重复。最终会被自然顶掉。
        StpUtil.login("temp:"+userId);
        openMFASafe("mailMFA",email);
        setTrustedDevice(userId,fingerPrint);
        return Result.success(null);
    }

    /**
     * 验证邮件代码是否已登录
     *
     * @param mailCode    邮件代码
     * @param userId      用户ID
     * @param fingerPrint 指纹
     * @param email       电子邮件
     * @return {@link Result }<{@link Void }>
     *///这个方法是为了已经登录的情况下使用MFA认证而准备的。大概是需要二次认证的敏感操作如注销。
    public Result<Void> verifyMailCodeHasLogin(String mailCode,String userId,String fingerPrint,String email) {
        String codeKey = getMFACodeKey(userId,email);
        String MFAcode =SaManager.getSaTokenDao().get(codeKey);
        if(MFAcode ==null){//如果用户从未请求验证码，也会显示过期。这应该是前端要做的小检验。
            return Result.fail(401,"验证码已过期，请重试。");
        }
        if(!MFAcode.equals(mailCode)){
            return Result.fail(422,"验证码错误，请重试。");
        }
        SaManager.getSaTokenDao().delete(codeKey);
        openMFASafe("mailMFA",email);
        setTrustedDevice(userId,fingerPrint);
        return Result.success(null);
    }

    /**
     * 验证电子邮件mfasafe
     *
     * @param email 电子邮件
     * @return {@link Result }<{@link Void }>
     */
    public Result<Void> verifyEmailMFASafe(String email) {
        boolean isSafe = StpUtil.isSafe("mailMFA"+email);
        //选择在业务成功后再关闭Safe状态（自然过期时长为6min）。
        if(!isSafe){
            return Result.fail(422,"邮箱验证未通过，请重试。");
        }
        return Result.success(null);
    }

    /**
     * 删除电子邮件mfasafe
     *
     * @param email 电子邮件
     *///把关闭safe状态封装为了单独的方法。这是有其必要性的，但这里空间太小，我写不下。😈
    //现在没有必要性咯！顶号后safe状态会自然重置。
    public void deleteEmailMFASafe(String email) {StpUtil.closeSafe("mailMFA"+email);}

    /**
     * 需要mfa
     *
     * @param userId      用户ID
     * @param fingerPrint 指纹
     * @return {@link Result }<{@link Void }>
     */
    public Result<Void> isNeedMFA(String userId,String fingerPrint) {

        if(Validator.isEmail(userId)){
            userId = loginService.getUserIdByEmail(userId);
        }
        String key = getMFATrustedDeviceKey(userId,fingerPrint);
        String status = SaManager.getSaTokenDao().get(key);
        if(status ==null){//status不存在时，未在此设备上进行过MFA验证或验证状态已过期。这个服务器是一个茶壶！
            return Result.fail(418,"您正在使用新设备登录，请进行邮箱验证。");
        }
        if(!status.equals("true")){//status不为真，事实上不应存在这种情况，因为方法必然设置为true。
            return Result.fail(418,"您正在使用新设备登录，请进行邮箱验证。");
        }
        return Result.success(null);
    }
}
