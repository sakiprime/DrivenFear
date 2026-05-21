package com.sakiprime.DrivenFear.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.sakiprime.DrivenFear.entity.AICallTaskEntity;
import com.sakiprime.DrivenFear.entity.MessageCorrelationData;
import com.sakiprime.DrivenFear.service.taskcosumer.image.ImageConsumerService;
import com.sakiprime.DrivenFear.service.taskcosumer.text.TextTaskConsumerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import static com.sakiprime.DrivenFear.config.RabbitConfig.FAILED_TASK_QUEUE;

@Component
@Slf4j
@RequiredArgsConstructor
public class FailedTaskConsumer {
    private final TextTaskConsumerService textTaskConsumerService;
    private final ImageConsumerService imageConsumerService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = FAILED_TASK_QUEUE)
    public void failedTaskConsumer(
            MessageCorrelationData correlation,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        try {
            if (correlation == null || correlation.getMessage() == null) {
                log.error("死信队列消息体为空，直接丢弃，correlation:{}", correlation);
                channel.basicAck(deliveryTag, false);
                return;
            }

            AICallTaskEntity task = resolveTask(correlation);
            if (task == null) {
                log.error("传入死信队列的任务消息体类型出错。correlation:{}", correlation);
                channel.basicAck(deliveryTag, false);
                return;
            }

            boolean isSuccess;
            String taskType = task.getTaskType();
            if ("TEXT".equals(taskType)) {
                isSuccess = textTaskConsumerService.markFailedTask(task);
            } else if ("IMAGE".equals(taskType)) {
                isSuccess = imageConsumerService.markFailedTask(task);
            } else {
                log.warn("未知任务类型:{},处理为TEXT", taskType);
                isSuccess = textTaskConsumerService.markFailedTask(task);
            }

            if (!isSuccess) {
                log.error("死信任务标记失效，订单号:{},用户:{},原始correlation:{}",
                        task.getOrderId(), task.getUserId(), correlation);
            }

            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.warn("死信队列消息确认失败。correlation:{}", correlation);
        }
    }

    private AICallTaskEntity resolveTask(MessageCorrelationData correlation) {

        return objectMapper.convertValue(correlation.getMessage(), AICallTaskEntity.class);
    }
}
