package com.foodhub.order.messaging;

import com.foodhub.common.messaging.RabbitMqContracts;
import com.foodhub.common.messaging.SeckillOrderCreateCommand;
import com.foodhub.common.messaging.SeckillOrderCreatedEvent;
import com.foodhub.order.entity.OrderEntity;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class SeckillOrderCreatedPublisher {

    private final RabbitTemplate rabbitTemplate;

    public SeckillOrderCreatedPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public boolean publish(SeckillOrderCreateCommand command, OrderEntity order) {
        String messageId = "MSG" + UUID.randomUUID().toString().replace("-", "");
        SeckillOrderCreatedEvent event = new SeckillOrderCreatedEvent(
                messageId,
                command.messageId(),
                command.requestId(),
                order.getOrderNo(),
                order.getUserId(),
                order.getActivityId(),
                Instant.now());
        CorrelationData correlation = new CorrelationData(messageId);
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMqContracts.BUSINESS_EXCHANGE,
                    RabbitMqContracts.SECKILL_ORDER_CREATED_ROUTING_KEY,
                    event,
                    correlation);
            CorrelationData.Confirm confirm = correlation.getFuture().get(3, TimeUnit.SECONDS);
            return confirm.isAck() && correlation.getReturned() == null;
        } catch (Exception exception) {
            return false;
        }
    }
}
