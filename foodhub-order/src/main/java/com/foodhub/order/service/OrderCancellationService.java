package com.foodhub.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodhub.common.messaging.OrderCancelledEvent;
import com.foodhub.common.messaging.SeckillTargetType;
import com.foodhub.order.entity.OrderEntity;
import com.foodhub.order.entity.OrderOutboxEntity;
import com.foodhub.order.mapper.OrderMapper;
import com.foodhub.order.mapper.OrderOutboxMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Service
public class OrderCancellationService {

    private static final ZoneId DATABASE_ZONE = ZoneId.of("Asia/Shanghai");

    private final OrderMapper orderMapper;
    private final OrderOutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;

    public OrderCancellationService(OrderMapper orderMapper,
                                    OrderOutboxMapper outboxMapper,
                                    ObjectMapper objectMapper) {
        this.orderMapper = orderMapper;
        this.outboxMapper = outboxMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public boolean cancelExpired(OrderEntity order, LocalDateTime now) {
        if (orderMapper.cancelIfExpired(order.getId(), now) != 1) {
            return false;
        }
        String messageId = "MSG" + UUID.randomUUID().toString().replace("-", "");
        OrderCancelledEvent event = new OrderCancelledEvent(
                messageId,
                order.getOrderNo(),
                order.getUserId(),
                order.getActivityId(),
                SeckillTargetType.valueOf(order.getTargetType()),
                order.getTargetId(),
                order.getQuantity(),
                "PAYMENT_TIMEOUT",
                now.atZone(DATABASE_ZONE).toInstant());

        OrderOutboxEntity outbox = new OrderOutboxEntity();
        outbox.setMessageId(messageId);
        outbox.setAggregateKey(order.getOrderNo());
        outbox.setEventType("ORDER_CANCELLED");
        outbox.setPayload(write(event));
        outbox.setStatus("PENDING");
        outbox.setAttempts(0);
        outbox.setNextRetryAt(now);
        outbox.setCreatedAt(now);
        outbox.setUpdatedAt(now);
        outboxMapper.insert(outbox);
        return true;
    }

    private String write(OrderCancelledEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize order cancellation", exception);
        }
    }
}
