package com.sakiprime.DrivenFear.service.alipay;


import com.alipay.easysdk.payment.page.models.AlipayTradePagePayResponse;
import com.sakiprime.DrivenFear.common.util.Result;
import com.sakiprime.DrivenFear.entity.UserRechargeOrderEntity;
import com.sakiprime.DrivenFear.entity.RechargePackageEntity;

import java.util.List;
import java.util.Map;

public interface AlipayService {
    RechargePackageEntity getPackageInfo(Long id);
    Result<List<RechargePackageEntity>> getPackageInfoList();
    UserRechargeOrderEntity getPurchaseInfo(Long orderId);
    boolean updatePurchaseInfo(UserRechargeOrderEntity userRechargeOrderEntity);
    boolean handleAlipayNotify(Map<String, String> params);
    Result<AlipayTradePagePayResponse> saveOrderAndInitiatePayment(UserRechargeOrderEntity purchaseEntity) throws Exception;
    boolean handleRechargeSuccess(UserRechargeOrderEntity purchase);
    boolean handleRechargeClosed(UserRechargeOrderEntity purchase);
}
