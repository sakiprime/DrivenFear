package com.sakiprime.DrivenFear.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitDelayConfig {


    public static final String DELAY_WAIT_QUEUE = "DELAY_WAIT_QUEUE";
    public static final String DELAY_CONSUMER_QUEUE = "DELAY_CONSUMER_QUEUE";
    public static final String DELAY_DLX = "DELAY_DLX";
    public static final String DELAY_DL_ROUTING_KEY = "DELAY_DL_ROUTING_KEY";

    @Bean
    public Queue delayConsumerQueue() {
        return QueueBuilder.durable(DELAY_CONSUMER_QUEUE).build();
    }

    @Bean
    public Queue delayWaitQueue() {
        return QueueBuilder.durable(DELAY_WAIT_QUEUE)
                .deadLetterExchange(DELAY_DLX)          // 超时后交给这个死信交换机
                .deadLetterRoutingKey(DELAY_DL_ROUTING_KEY) // 用这个路由键转发
                .build();
    }

    @Bean
    public DirectExchange delayDLX() {
        return ExchangeBuilder.directExchange(DELAY_DLX).durable(true).build();
    }

    @Bean
    public Binding delayBinding(Queue delayConsumerQueue, DirectExchange delayDLX) {
        return BindingBuilder.bind(delayConsumerQueue)
                .to(delayDLX)
                .with(DELAY_DL_ROUTING_KEY);
    }
}