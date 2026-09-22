package com.foodhub.coupon.messaging;

import com.foodhub.common.messaging.RabbitMqContracts;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class RabbitMessageRetry {

    private final RabbitTemplate rabbitTemplate;

    public RabbitMessageRetry(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public boolean forward(Object payload, Message source, String routingKey) {
        int retryCount = retryCount(source);
        boolean deadLetter = retryCount >= RabbitMqContracts.MAX_CONSUMER_RETRIES;
        String exchange = deadLetter
                ? RabbitMqContracts.DEAD_LETTER_EXCHANGE
                : RabbitMqContracts.BUSINESS_EXCHANGE;
        String targetRoutingKey = deadLetter
                ? source.getMessageProperties().getConsumerQueue()
                : routingKey;
        CorrelationData correlation = new CorrelationData(UUID.randomUUID().toString());
        try {
            rabbitTemplate.convertAndSend(exchange, targetRoutingKey, payload, message -> {
                message.getMessageProperties().setHeader(
                        RabbitMqContracts.RETRY_COUNT_HEADER, retryCount + 1);
                message.getMessageProperties().setHeader("x-foodhub-original-queue",
                        source.getMessageProperties().getConsumerQueue());
                return message;
            }, correlation);
            CorrelationData.Confirm confirm = correlation.getFuture().get(3, TimeUnit.SECONDS);
            return confirm.isAck() && correlation.getReturned() == null;
        } catch (Exception exception) {
            return false;
        }
    }

    private int retryCount(Message message) {
        Object value = message.getMessageProperties().getHeaders()
                .get(RabbitMqContracts.RETRY_COUNT_HEADER);
        return value instanceof Number number ? number.intValue() : 0;
    }
}
