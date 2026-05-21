package com.sakiprime.DrivenFear.config;

import com.sakiprime.DrivenFear.entity.MessageCorrelationData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class RabbitConfig {

    public static final String TEXT_TASK_QUEUE = "AITaskQueue";
    public static final String IMAGE_TASK_QUEUE = "ImageTaskQueue";
    public static final String FAILED_TASK_QUEUE = "FailedTaskQueue";
    public static final String ALIPAY_QUEUE = "AliPayQueue";
    @Bean
    public Queue AITaskQueue() {
        return new Queue(TEXT_TASK_QUEUE, true);
    }
    @Bean
    public Queue ImageTaskQueue() {
        return new Queue(IMAGE_TASK_QUEUE, true);
    }
    @Bean
    public Queue FailedTaskQueue() {
        return new Queue(FAILED_TASK_QUEUE, true);
    }
    @Bean
    public Queue AliPayQueue() {
        return new Queue(ALIPAY_QUEUE, true);
    }
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory factory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(factory);
        template.setMessageConverter(messageConverter);
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (correlationData == null) return;//空值驳回
            if (!(correlationData instanceof MessageCorrelationData data)) {//检测Correlation类型是否正确
                log.error("消息Correlation类型异常,ID: {}", correlationData.getId());
                return;
            }
            String logInfo = data.getLogInfo();
            String queue = data.getQueue();
            Object message = data.getMessage();
            int retryCount = data.getRetryCount();
            if (ack) {
                log.info("消息发送成功：{}", logInfo);
            }
            else if(retryCount <3){
                log.error("消息发送失败：{},第{}次重试", logInfo,retryCount);
                data.incrementRetry();
                template.convertAndSend(queue,message,data);
            }
            else if(retryCount == 3){
                log.error("消息发送重试次数用完：{},进入人工处理队列",logInfo);
                //template.convertAndSend(FAILED_TASK_QUEUE,message,data);
            }
        });
        return template;
    }
}