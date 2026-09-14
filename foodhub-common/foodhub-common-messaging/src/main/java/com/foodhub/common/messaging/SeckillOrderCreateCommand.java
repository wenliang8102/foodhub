package com.foodhub.common.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** Command published by coupon/seckill and consumed by order to create a pending order. */
public record SeckillOrderCreateCommand(
        String messageId,
        String requestId,
        Long userId,
        Long activityId,
        SeckillTargetType targetType,
        Long targetId,
        int quantity,
        BigDecimal unitPrice,
        Instant occurredAt,
        Instant paymentExpiresAt) {

    public SeckillOrderCreateCommand {
        requireText(messageId, "messageId");
        requireText(requestId, "requestId");
        requirePositive(userId, "userId");
        requirePositive(activityId, "activityId");
        Objects.requireNonNull(targetType, "targetType must not be null");
        requirePositive(targetId, "targetId");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new IllegalArgumentException("unitPrice must be non-negative");
        }
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(paymentExpiresAt, "paymentExpiresAt must not be null");
        if (!paymentExpiresAt.isAfter(occurredAt)) {
            throw new IllegalArgumentException("paymentExpiresAt must be after occurredAt");
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
