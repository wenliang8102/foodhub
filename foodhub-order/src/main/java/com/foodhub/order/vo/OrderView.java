package com.foodhub.order.vo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record OrderView(
        Long id,
        String orderNo,
        String requestId,
        Long userId,
        Long activityId,
        String targetType,
        Long targetId,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal totalAmount,
        String status,
        OffsetDateTime paymentExpiresAt,
        OffsetDateTime paidAt,
        OffsetDateTime cancelledAt,
        OffsetDateTime createdAt) {
}
