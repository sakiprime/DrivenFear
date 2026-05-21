package com.sakiprime.DrivenFear.service.alipay;

import com.sakiprime.DrivenFear.entity.UserRechargeOrderEntity;
import com.sakiprime.DrivenFear.mapper.UserMapper;
import com.sakiprime.DrivenFear.mapper.UserRechargeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AliPayServiceTransactionImpl implements AliPayServiceTransaction {
    private final UserMapper userMapper;
    private final UserRechargeMapper userRechargeMapper;
    private boolean updatePurchaseInfo(UserRechargeOrderEntity userRechargeOrderEntity) {

        return userRechargeMapper.updateById(userRechargeOrderEntity) > 0;
    }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean handleRechargeSuccessTransactional(UserRechargeOrderEntity purchase){
        //(当补偿时)被乐观锁阻断，是因为(很罕见地)积压到33min后的异步回调已经作用，此时无需补偿。
        if(!updatePurchaseInfo(purchase)){
            log.warn("支付订单更新被阻断。订单号:{},用户ID:{}",
                    purchase.getOrderId(),purchase.getUserId());
            return false;
        }
        return userMapper.increaseTokenBalanceAtomic(
                purchase.getUserId(), purchase.getTokensAmount()) > 0;
    }
}
