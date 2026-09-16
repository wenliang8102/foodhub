package com.foodhub.merchant.vo;

import com.foodhub.merchant.dto.ResourceStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record FoodItemView(
        Long id,
        Long merchantId,
        String merchantName,
        String name,
        BigDecimal price,
        String imageUrl,
        Long salesCount,
        Boolean onSale,
        ResourceStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
