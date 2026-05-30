package com.sakiprime.DrivenFear.service.alipay;

import com.alipay.easysdk.factory.Factory;
import com.alipay.easysdk.kernel.util.ResponseChecker;
import com.alipay.easysdk.payment.page.models.AlipayTradePagePayResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.sakiprime.DrivenFear.common.util.Result;
import com.sakiprime.DrivenFear.entity.MessageCorrelationData;
import com.sakiprime.DrivenFear.entity.UserRechargeOrderEntity;
import com.sakiprime.DrivenFear.entity.RechargePackageEntity;
import com.sakiprime.DrivenFear.mapper.UserMapper;
import com.sakiprime.DrivenFear.mapper.UserRechargeMapper;
import com.sakiprime.DrivenFear.mapper.RechargePackageMapper;
import com.sakiprime.DrivenFear.common.util.AmountUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.sakiprime.DrivenFear.config.RabbitDelayConfig.DELAY_CONSUMER_QUEUE;
import static com.sakiprime.DrivenFear.config.RabbitDelayConfig.DELAY_WAIT_QUEUE;


/**
 * 支付宝服务实施
 *
 * @author 凋零
 * @since 2026/05/06
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AlipayServiceImpl implements AlipayService {
    private final RechargePackageMapper rechargePackageMapper;
    private final UserRechargeMapper userRechargeMapper;
    private final AliPayServiceTransaction aliPayServiceTransaction;
    private final RabbitTemplate rabbitTemplate;
    private final UserMapper userMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final DateTimeFormatter ALIPAY_TIME_FORMATTER = DateTimeFormatter.
            ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 获取包信息
     *
     * @param id 标识符
     * @return {@link RechargePackageEntity }
     */
    @Override
    public RechargePackageEntity getPackageInfo(Long id){
        String key = "recharge:package:" + id;
        RechargePackageEntity cached = (RechargePackageEntity) redisTemplate.opsForValue().get(key);
        if (cached != null) return cached;

        RechargePackageEntity entity = rechargePackageMapper.selectById(id);
        if (entity != null) {
            redisTemplate.opsForValue().set(key, entity, 60, TimeUnit.SECONDS);
        }
        return entity;
    }

    /**
     * 获取软件包信息列表
     *
     * @return {@link Result }<{@link List }<{@link RechargePackageEntity }>>
     */
    @Override
    @SuppressWarnings("unchecked")
    public Result<List<RechargePackageEntity>> getPackageInfoList() {
        String key = "recharge:package:list";
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached instanceof List<?> list) {
            if (!list.isEmpty() && list.get(0) instanceof RechargePackageEntity) {
                return Result.success((List<RechargePackageEntity>) list);
            }
        }

        LambdaQueryWrapper<RechargePackageEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(RechargePackageEntity::getOnSale, true);

        List<RechargePackageEntity> list = rechargePackageMapper.selectList(queryWrapper);
        redisTemplate.opsForValue().set(key, list, 60, TimeUnit.SECONDS);
        return Result.success(list);
    }

    /**
     * 获取购买信息
     *
     * @param orderId 订单号
     * @return {@link UserRechargeOrderEntity }
     */
    @Override
    public UserRechargeOrderEntity getPurchaseInfo(Long orderId){

        return userRechargeMapper.selectById(orderId);
    }

    /**
     * 更新购买信息
     *
     * @param userRechargeOrderEntity 用户充值订单实体
     * @return boolean
     */
    @Override
    public boolean updatePurchaseInfo(UserRechargeOrderEntity userRechargeOrderEntity) {

        return userRechargeMapper.updateById(userRechargeOrderEntity) > 0;
    }


    /**
     * 保存订单并启动付款
     *
     * @param purchaseEntity 采购信息
     * @return {@link Result }<{@link AlipayTradePagePayResponse }>
     * @throws Exception 异常
     */
    @Override
    public Result<AlipayTradePagePayResponse> saveOrderAndInitiatePayment(UserRechargeOrderEntity purchaseEntity)throws Exception{

        Long orderId = IdWorker.getId();
        purchaseEntity.setOrderId(orderId);//雪花订单号
        /*
            通过套餐id得到具体套餐，防篡改。
            事实上，此处也可以复用AI模型的缓存方案，大大减少查库IO。
            但是，充值接口调用次数较少，故此处查库总负载不高，可以接受。
         */
        RechargePackageEntity packageInfo =
                getPackageInfo(purchaseEntity.getId());
        //套餐id由前端刷新指定，不应为非法，此处为防篡改可用套餐id或防前端缓存过时。
        if(packageInfo == null || packageInfo.getOnSale() != true){
            return Result.fail(400,"非法的充值套餐编号");
        }
        BigDecimal priceYuan = AmountUtil.fenToYuan(packageInfo.getDiscountedPrice());//转化金额到元
        purchaseEntity.setPaymentAmount(priceYuan);
        purchaseEntity.setTokensAmount(packageInfo.getTokensAmount());
        //设置状态......一定程度上这个PENDING扮演着PROCESSING的作用。
        purchaseEntity.setTradeStatus("PENDING");
        if(userRechargeMapper.insert(purchaseEntity)==0){
            log.error("支付宝订单存储失败。用户:{}",purchaseEntity.getUserId());
            return Result.fail(500,"创建支付宝订单失败，请重试。");
        }

        AlipayTradePagePayResponse response = Factory.Payment.Page()
                .optional("timeout_express", "30m")  //30分钟后过期
                .pay(packageInfo.getPackageName(), //商品名
                        purchaseEntity.getOrderId().toString(), //订单号
                        priceYuan.toString(), //实际价格
                        purchaseEntity.getReturnUrl());//支付后跳转页

        if(!ResponseChecker.success(response)){
            return Result.fail(500,"创建支付宝订单失败，请重试。");
        }
        // 打印表单HTML中的notify_url，确认回调地址
        if (response.getBody() != null && response.getBody().contains("notify_url")) {
            String snippet = response.getBody().replaceAll("[\r\n]", " ").replaceAll(".*(notify_url[^\"]*\"[^\"]*\").*", "$1");
            log.info("支付表单notify_url片段: {}", snippet.length() > 200 ? snippet.substring(0, 200) : snippet);
        } else {
            log.warn("支付表单中未找到notify_url，body长度={}", response.getBody() == null ? 0 : response.getBody().length());
        }
        String logInfo = String.format("成功创建支付宝充值订单。订单号:%s,用户:%s,套餐ID:%s",
                orderId, purchaseEntity.getUserId(), purchaseEntity.getId());
        log.info(logInfo);
        MessageCorrelationData correlation = new MessageCorrelationData(
                DELAY_CONSUMER_QUEUE,
                purchaseEntity,
                logInfo,
                MessageCorrelationData.ALIPAY_TASK
        );
        /*延时主动查单。设置约33min的延迟，是为了留出缓冲补偿时间。用户可能在订单过期恰好过期前的30min支付，
          如果延时查单时间也为30min，则可能导致延时查单在订单处理完之前发生。
         */
        rabbitTemplate.convertAndSend(
                "",
                DELAY_WAIT_QUEUE,
                correlation,//这里直接把correlation当内容转发。
                message -> {
                    message.getMessageProperties().setExpiration("2000000");//约33min
                    return message;
                }
        );
        return Result.success(response);
    }

    /**
     * 处理支付宝通知
     *
     * @param params 参数
     * @return boolean
     */
    @Override
    public boolean handleAlipayNotify(Map<String, String> params){
        String tradeStatus = params.get("trade_status");
        String outTradeNoStr = params.get("out_trade_no");
        String gmtCreateTime = params.get("gmt_create");
        String notifyTime = params.get("notify_time");
        if (outTradeNoStr == null || outTradeNoStr.isBlank()) {
            return false;
        }
        Long outTradeNo = Long.parseLong(outTradeNoStr);//转成雪花Long类型
        UserRechargeOrderEntity purchaseInfo = getPurchaseInfo(outTradeNo);
        if (purchaseInfo == null) {
            return false;
        }
        purchaseInfo.setTradeStatus(tradeStatus);//更新基础状态
        if (gmtCreateTime != null && !gmtCreateTime.isEmpty()) {
            LocalDateTime createTime = LocalDateTime.parse(gmtCreateTime, ALIPAY_TIME_FORMATTER);
            purchaseInfo.setCreateTime(createTime);
        }
        if (notifyTime != null && !notifyTime.isEmpty()) {
            LocalDateTime updateTime = LocalDateTime.parse(notifyTime, ALIPAY_TIME_FORMATTER);
            purchaseInfo.setUpdateTime(updateTime);
        }
        if (!updatePurchaseInfo(purchaseInfo)) {
            return false;
        }
        if (tradeStatus.equals("TRADE_SUCCESS") || tradeStatus.equals("TRADE_FINISHED")) {
            String paymentAmount = params.get("total_amount");
            String gmpaymentTime = params.get("gmt_payment");
            if (gmpaymentTime != null && !gmpaymentTime.isEmpty()) {
                LocalDateTime paymentTime = LocalDateTime.parse(gmpaymentTime, ALIPAY_TIME_FORMATTER);
                purchaseInfo.setUpdateTime(paymentTime);
            }

            //获取套餐实际金额
            RechargePackageEntity packageInfo = getPackageInfo(purchaseInfo.getId());
            String localAmount = AmountUtil.fenToYuan(packageInfo.getDiscountedPrice()).toString();

            boolean isValid = localAmount.equals(paymentAmount);
            if (!isValid) {
                return false; //金额不一致拒绝
            }

            //支付成功，发放Token
            if (!handleRechargeSuccess(purchaseInfo)) {
                //TODO 这里失败的时候没有标记订单的需人工处理状态。值得考虑
                String logInfo = String.format("[需要人工核查]异步回调订单给予Token失败|用户:%s,订单:%s", purchaseInfo.getUserId(), purchaseInfo.getOrderId());
                log.error(logInfo);
                userMapper.updatePayOrderNeedManual(purchaseInfo.getUserId(), true);
                return false;
            }
            log.info("支付宝回调成功，订单支付完成|用户:{},订单号:{}",
                    purchaseInfo.getUserId(), purchaseInfo.getOrderId());
            return true;
        }

        // 非成功状态（如 TRADE_CLOSED），关闭订单
        handleRechargeClosed(purchaseInfo);
        log.info("支付宝回调非成功状态，已关闭|订单号:{},状态:{}", purchaseInfo.getOrderId(), tradeStatus);
        return true;
    }


    /**
     * 处理充值关闭
     *
     * @param purchase 购买
     * @return boolean
     */
    @Override
    public boolean handleRechargeClosed(UserRechargeOrderEntity purchase){
        return userRechargeMapper.deleteById(purchase.getOrderId()) > 0;
    }

    /**
     * 处理充值成功
     *
     * @param purchase 购买
     * @return boolean
     */
    @Override
    public boolean handleRechargeSuccess(UserRechargeOrderEntity purchase){
        purchase.setTradeStatus("TRADE_SUCCESS");
        purchase.setTokenGranted(true);
        return aliPayServiceTransaction.handleRechargeSuccessTransactional(purchase);
    }
}
