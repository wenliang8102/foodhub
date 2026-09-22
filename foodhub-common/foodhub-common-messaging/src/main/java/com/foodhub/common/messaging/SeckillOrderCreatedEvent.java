package com.foodhub.common.messaging;

import java.time.Instant;

/** Event published after order idempotently creates a pending seckill order. */
public record SeckillOrderCreatedEvent(
        String messageId,
        String causationMessageId,
        String requestId,
        String orderNo,
        Long userId,
        Long activityId,
        Instant occurredAt) {

    public SeckillOrderCreatedEvent {
        requireText(messageId, "messageId");
        requireText(causationMessageId, "causationMessageId");
        requireText(requestId, "requestId");
        requireText(orderNo, "orderNo");
        requirePositive(userId, "userId");
        requirePositive(activityId, "activityId");
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt must not be null");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private static void requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }
}
