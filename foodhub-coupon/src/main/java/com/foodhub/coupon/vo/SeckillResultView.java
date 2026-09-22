package com.foodhub.coupon.vo;

import java.time.OffsetDateTime;

public record SeckillResultView(
        String requestId,
        Long activityId,
        String status,
        String orderNo,
        String failureCode,
        OffsetDateTime createdAt) {
}
