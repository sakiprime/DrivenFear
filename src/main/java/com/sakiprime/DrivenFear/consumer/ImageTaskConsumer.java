package com.sakiprime.DrivenFear.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.sakiprime.DrivenFear.common.util.Result;
import com.sakiprime.DrivenFear.entity.AICallRequestDTO;
import com.sakiprime.DrivenFear.entity.MessageCorrelationData;
import com.sakiprime.DrivenFear.service.taskcosumer.image.ImageConsumerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import static com.sakiprime.DrivenFear.config.RabbitConfig.FAILED_TASK_QUEUE;
import static com.sakiprime.DrivenFear.config.RabbitConfig.IMAGE_TASK_QUEUE;

@Component
@Slf4j
@RequiredArgsConstructor
public class ImageTaskConsumer {
    private final ImageConsumerService imageConsumerService;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = IMAGE_TASK_QUEUE)
    public void taskConsumer(
            MessageCorrelationData correlation,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag
    ) {
        if (correlation == null || correlation.getMessage() == null) {
            log.error("图片任务消息体为空，直接丢弃，correlation:{}", correlation);
            try {
                channel.basicAck(deliveryTag, false);
            } catch (Exception e) {
                log.warn("空图片任务消息丢弃失败。correlation:{}", correlation);
            }
            return;
        }

        AICallRequestDTO request = objectMapper.convertValue(
                correlation.getMessage(),
                AICallRequestDTO.class
        );
        log.info("处理图片生成任务: 用户={}, 订单={}", request.getUserId(), request.getOrderId());

        try {
            boolean isSuccess = imageConsumerService.saveOrderAndDeduction(request);

            if (!isSuccess) {
                channel.basicAck(deliveryTag, false);
                log.warn("图片任务扣款存单失败，已转存到人工队列。订单号: {}", request.getOrderId());
                return;
            }

            Result<Void> handleResult = imageConsumerService.sendTaskToApi(request.getOrderId());
            if (handleResult.getCode() != 200) {
                log.warn("图片任务处理失败，重新入队。订单号: {}", request.getOrderId());
                throw new Exception(handleResult.getMsg());
            }
            channel.basicAck(deliveryTag, false);

            log.info("图片任务处理成功，已确认消息。订单号: {}", request.getOrderId());

        } catch (Exception e) {
            try {
                channel.basicAck(deliveryTag, false);
                if (correlation.getConsumerRetryCount() < 3) {
                    correlation.incrementConsumerRetryCount();
                    log.warn("图片任务系统异常，消息重新入队。订单号: {},第{}次重试",
                            request.getOrderId(), correlation.getConsumerRetryCount(), e);
                    rabbitTemplate.convertAndSend(correlation.getQueue(), correlation);
                } else {
                    log.error("消息发送重试次数用完：{},进入人工处理队列", correlation.getLogInfo());
                    rabbitTemplate.convertAndSend(FAILED_TASK_QUEUE, correlation);
                }
            } catch (Exception ex) {
                log.error("消息重新入队失败！订单号: {}", request.getOrderId(), ex);
            }
        }
    }
}
