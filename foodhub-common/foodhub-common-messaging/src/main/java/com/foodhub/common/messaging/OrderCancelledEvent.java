package com.foodhub.common.messaging;

import java.time.Instant;

/** Event published by order when a reserved seckill item must be returned to coupon inventory. */
public record OrderCancelledEvent(
        String messageId,
        String orderNo,
        Long userId,
        Long activityId,
        SeckillTargetType targetType,
        Long targetId,
        int quantity,
        String reason,
        Instant occurredAt) {

    public OrderCancelledEvent {
        requireText(messageId, "messageId");
        requireText(orderNo, "orderNo");
        requirePositive(userId, "userId");
        requirePositive(activityId, "activityId");
        if (targetType == null) {
            throw new IllegalArgumentException("targetType must not be null");
        }
        requirePositive(targetId, "targetId");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        requireText(reason, "reason");
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
