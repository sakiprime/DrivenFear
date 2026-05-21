package com.sakiprime.DrivenFear.service.alipay;

import com.sakiprime.DrivenFear.entity.UserRechargeOrderEntity;

public interface AliPayServiceTransaction {
    boolean handleRechargeSuccessTransactional(UserRechargeOrderEntity purchase);
}
