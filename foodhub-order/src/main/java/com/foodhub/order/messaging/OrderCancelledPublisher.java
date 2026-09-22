package com.foodhub.order.messaging;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodhub.common.messaging.OrderCancelledEvent;
import com.foodhub.common.messaging.RabbitMqContracts;
import com.foodhub.order.entity.OrderOutboxEntity;
import com.foodhub.order.mapper.OrderOutboxMapper;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class OrderCancelledPublisher {

    private static final ZoneId DATABASE_ZONE = ZoneId.of("Asia/Shanghai");

    private final OrderOutboxMapper outboxMapper;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public OrderCancelledPublisher(OrderOutboxMapper outboxMapper,
                                   RabbitTemplate rabbitTemplate,
                                   ObjectMapper objectMapper) {
        this.outboxMapper = outboxMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${foodhub.order.outbox-delay-ms:3000}")
    public void publishPending() {
        LocalDateTime now = now();
        List<OrderOutboxEntity> pending = outboxMapper.selectList(
                new LambdaQueryWrapper<OrderOutboxEntity>()
                        .eq(OrderOutboxEntity::getStatus, "PENDING")
                        .le(OrderOutboxEntity::getNextRetryAt, now)
                        .orderByAsc(OrderOutboxEntity::getId)
                        .last("LIMIT 50"));
        pending.forEach(this::publish);
    }

    private void publish(OrderOutboxEntity outbox) {
        try {
            OrderCancelledEvent event = objectMapper.readValue(
                    outbox.getPayload(), OrderCancelledEvent.class);
            CorrelationData correlation = new CorrelationData(outbox.getMessageId());
            rabbitTemplate.convertAndSend(
                    RabbitMqContracts.BUSINESS_EXCHANGE,
                    RabbitMqContracts.ORDER_CANCELLED_ROUTING_KEY,
                    event,
                    correlation);
            CorrelationData.Confirm confirm = correlation.getFuture().get(3, TimeUnit.SECONDS);
            if (confirm.isAck() && correlation.getReturned() == null) {
                outboxMapper.markSent(outbox.getId(), now());
            } else {
                markFailed(outbox, confirm.getReason());
            }
        } catch (Exception exception) {
            markFailed(outbox, error(exception));
        }
    }

    private void markFailed(OrderOutboxEntity outbox, String error) {
        LocalDateTime now = now();
        int attempts = outbox.getAttempts() == null ? 0 : outbox.getAttempts();
        long delaySeconds = Math.min(60, 1L << Math.min(attempts, 6));
        String safe = error == null ? "Unknown publish failure" : error;
        outboxMapper.markFailed(outbox.getId(), now.plusSeconds(delaySeconds),
                safe.length() <= 500 ? safe : safe.substring(0, 500), now);
    }

    private String error(Exception exception) {
        if (exception instanceof JsonProcessingException) {
            return "Invalid outbox payload";
        }
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(DATABASE_ZONE);
    }
}
