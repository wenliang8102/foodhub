package com.foodhub.coupon.vo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record SeckillActivityView(
        Long id,
        Long merchantId,
        String targetType,
        Long targetId,
        String title,
        BigDecimal seckillPrice,
        Integer totalStock,
        Integer remainingStock,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        String status,
        boolean stockReady) {
}
