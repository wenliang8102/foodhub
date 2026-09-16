package com.foodhub.merchant.vo;

import com.foodhub.merchant.dto.BusinessStatus;
import com.foodhub.merchant.dto.ResourceStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record MerchantView(
        Long id,
        Long ownerUserId,
        Long categoryId,
        String categoryName,
        String name,
        String address,
        BigDecimal longitude,
        BigDecimal latitude,
        BigDecimal rating,
        Long salesCount,
        BusinessStatus businessStatus,
        ResourceStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
