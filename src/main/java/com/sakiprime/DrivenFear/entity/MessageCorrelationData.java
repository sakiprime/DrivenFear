package com.sakiprime.DrivenFear.entity;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.amqp.rabbit.connection.CorrelationData;

import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = true)
public class MessageCorrelationData extends CorrelationData {
    public static final String AI_TASK = "AITask";
    public static final String ALIPAY_TASK = "AliPayTask";
    private final String queue;
    private final Object message;
    private final String logInfo;
    private final String messageType;
    private int retryCount;
    private int consumerRetryCount;
    public MessageCorrelationData(String queue, Object message, String logInfo, String messageType) {
        super(UUID.randomUUID().toString());
        this.queue = queue;
        this.message = message;
        this.logInfo = logInfo;
        this.retryCount = 0;
        this.consumerRetryCount = 0;
        this.messageType = messageType;
    }
    public void incrementRetry() {
        this.retryCount ++;
    }
    public void incrementConsumerRetryCount() {
        this.consumerRetryCount ++;
    }
}

