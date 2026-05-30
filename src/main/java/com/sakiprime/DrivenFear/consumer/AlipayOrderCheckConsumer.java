package com.sakiprime.DrivenFear.consumer;

import com.alipay.easysdk.factory.Factory;
import com.alipay.easysdk.kernel.util.ResponseChecker;
import com.alipay.easysdk.payment.common.models.AlipayTradeQueryResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.sakiprime.DrivenFear.entity.MessageCorrelationData;
import com.sakiprime.DrivenFear.entity.UserRechargeOrderEntity;
import com.sakiprime.DrivenFear.mapper.UserMapper;
import com.sakiprime.DrivenFear.service.alipay.AlipayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;


import static com.sakiprime.DrivenFear.config.RabbitDelayConfig.DELAY_CONSUMER_QUEUE;

@Component
@Slf4j
@RequiredArgsConstructor
public class AlipayOrderCheckConsumer {
    private final RabbitTemplate rabbitTemplate;
    private final AlipayService alipayService;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;
    @RabbitListener(queues = DELAY_CONSUMER_QUEUE)
    public void checkOrder(
            MessageCorrelationData correlation, Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        try{
            if (correlation == null || correlation.getMessage() == null) {
                channel.basicAck(deliveryTag, false);
                return;
            }
        }
        catch (Exception e){
            log.warn("空延时查单消息丢弃失败。correlation:{}",correlation);
        }

        UserRechargeOrderEntity purchaseMessage = objectMapper.convertValue(
                correlation.getMessage(),
                UserRechargeOrderEntity.class
        );
        UserRechargeOrderEntity purchase = alipayService.getPurchaseInfo(purchaseMessage.getOrderId());


        //TODO 如果订单确认失败导致的异常可能重复发送消息！
        AlipayTradeQueryResponse queryResponse;
        try {
            //如果订单已处理完成（trade_status=SUCCESS且token已发放），快速返回。
            if ("TRADE_SUCCESS".equals(purchase.getTradeStatus())
                    && Boolean.TRUE.equals(purchase.getTokenGranted())) {
                log.info("订单已处理完成，无需补偿，订单号:{}", purchase.getOrderId());
                channel.basicAck(deliveryTag, false);
                return;
            }
            queryResponse = Factory.Payment.Common()
                    .query(purchase.getOrderId().toString());
            if (!ResponseChecker.success(queryResponse)) {
                log.error(queryResponse.getMsg());
                throw new RuntimeException("支付宝查单失败");
            }
        }
        catch (Exception e) {
            try {
                channel.basicAck(deliveryTag, false);
                if(correlation.getConsumerRetryCount()<3){
                    correlation.incrementConsumerRetryCount();
                    log.error("AliPay主动查单异常，消息重新入队。订单号: {},第{}次重试",
                            purchase.getOrderId(),correlation.getConsumerRetryCount());
                    rabbitTemplate.convertAndSend(DELAY_CONSUMER_QUEUE,
                            correlation.getMessage(), correlation);
                }
                else{
                    log.error("消息发送重试次数用完：{},进入人工处理队列",correlation.getLogInfo());
                    //TODO 尚未处理好的人工处理rabbitTemplate.convertAndSend(FAILED_TASK_QUEUE, correlation.getMessage(), correlation);
                }
                return;
            } catch (Exception ex) {
                log.error("消息重新入队失败！订单号: {}", purchase.getOrderId(), ex);
                return;
            }
        }
        String tradeStatus = queryResponse.getTradeStatus();
        //TODO
        switch (tradeStatus) {
            case "TRADE_SUCCESS":
            case "TRADE_FINISHED":
                //当回查补偿触发时，异步回调没有正确录入支付时间,以补偿时间为准。
                log.info("订单支付成功，异步回调丢失，执行掉单补偿。订单号:{}"
                        , purchase.getOrderId());
                //回查补偿触发时，如果订单为需要人工则可以清除此状态。这和补偿成功是原子的。
                if (Boolean.TRUE.equals(purchase.getRequireManual())) {
                    purchase.setRequireManual(false);
                }
                if(!alipayService.handleRechargeSuccess(purchase)){
                    log.warn("[需要人工核查]支付订单回查补偿未成功。订单号:{},用户ID:{}",
                            purchase.getOrderId(),purchase.getUserId());
                    userMapper.updatePayOrderNeedManual(purchase.getUserId(),
                            true);
                }
                break;

            case "TRADE_CLOSED":
                log.info("支付订单超时关闭。订单号:{},用户ID:{}",
                        purchase.getOrderId(),purchase.getUserId());
                alipayService.handleRechargeClosed(purchase);
                break;

            //case "WAIT_BUYER_PAY": 已经设置30min超时，33min检测时必定无此情况。


            default:
                log.error("[需要人工核查]未知订单状态：{}。订单号:{},用户ID:{}",tradeStatus,
                        purchase.getOrderId(),purchase.getUserId());
                userMapper.updatePayOrderNeedManual(purchase.getUserId(),
                        true);

        }
        try {
            channel.basicAck(deliveryTag, false);
        }
        catch (Exception e) {
            log.warn("订单回查消息确认失败。{}",purchase);
        }


    }

}
