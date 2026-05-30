package com.sakiprime.DrivenFear.controller;
import cn.dev33.satoken.stp.StpUtil;
import com.alipay.easysdk.factory.Factory;
import com.alipay.easysdk.payment.page.models.AlipayTradePagePayResponse;
import com.sakiprime.DrivenFear.annotation.ApiRateLimit;
import com.sakiprime.DrivenFear.annotation.RequireRole;
import com.sakiprime.DrivenFear.entity.RechargePackageEntity;
import com.sakiprime.DrivenFear.entity.UserRechargeOrderEntity;
import com.sakiprime.DrivenFear.service.alipay.AlipayService;
import com.sakiprime.DrivenFear.common.util.RequestUtil;
import com.sakiprime.DrivenFear.common.util.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;


import java.util.List;
import java.util.Map;

/**
 * 支付宝控制器
 *
 * @author 凋零
 * @since 2026/05/04
 */
@RestController
@Slf4j
public class AlipayController {
    private final AlipayService alipayService;

    /**
     * 支付宝控制器
     *
     * @param alipayService 支付宝服务
     */
    public AlipayController(AlipayService alipayService) {
        this.alipayService = alipayService;
    }


    /**
     * 获取充值套餐信息列表
     *
     * @return {@link Result }<{@link List }<{@link RechargePackageEntity }>>
     */
    @GetMapping("/recharge")
    @ApiRateLimit(interFace = "getRechargePackageInfoList")
    @RequireRole
    public Result<List<RechargePackageEntity>> getRechargePackageInfoList(){

        return alipayService.getPackageInfoList();
    }
    /**
     * 获取当前登录用户ID（下单前确认）
     *
     * @return {@link Result }<{@link String }>
     */
    @GetMapping("/recharge/current-user")
    @ApiRateLimit(interFace = "getCurrentUserId")
    @RequireRole
    public Result<String> getCurrentUserId(){

        String userId = StpUtil.getLoginIdAsString();
        log.info("用户{}充值前确认ID", userId);
        return Result.success(userId);
    }

    /**
     * 页面支付
     *
     * @param purchasePackage 购买套餐
     * @return {@link Result }<{@link String }>
     * @throws Exception 异常
     */

    @PostMapping("/recharge/orders")
    @ApiRateLimit(interFace = "pagePay")
    @RequireRole
    public Result<String> pagePay(@RequestBody UserRechargeOrderEntity purchasePackage) throws Exception {
        //重置用户ID
        String userId = StpUtil.getLoginIdAsString();
        purchasePackage.setUserId(userId);
        Result<AlipayTradePagePayResponse> repResult =alipayService.saveOrderAndInitiatePayment(purchasePackage);
        if (repResult.getCode() != 200){
            return Result.fail(500,repResult.getMsg());
        }
        return Result.success(repResult.getData().getBody());
    }

    /**
     * 支付宝通知
     *
     * @param request 请求
     * @return {@link String }
     * @throws Exception 异常
     */
    @PostMapping("/recharge/notify")
    public String alipayNotify(HttpServletRequest request) throws Exception {

        Map<String, String> params = RequestUtil.convertNotifyParams(request);
        boolean signVerified = Factory.Payment.Common().verifyNotify(params);
        if (!signVerified) {
            return "fail";
        }//验签不匹配
        boolean isSuccess = alipayService.handleAlipayNotify(params);

        return (isSuccess) ? "success" : "fail";
    }
}
