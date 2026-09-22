package com.foodhub.coupon.vo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CouponView(
        Long id,
        Long merchantId,
        String name,
        BigDecimal faceValue,
        BigDecimal minSpend,
        Integer totalStock,
        Integer remainingStock,
        OffsetDateTime validFrom,
        OffsetDateTime validUntil,
        String status,
        boolean claimable) {
}
