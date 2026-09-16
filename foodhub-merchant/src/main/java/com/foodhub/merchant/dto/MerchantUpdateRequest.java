package com.foodhub.merchant.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record MerchantUpdateRequest(
        @Size(max = 80) String name,
        Long categoryId,
        @Size(max = 255) String address,
        @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
        @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        BusinessStatus businessStatus,
        ResourceStatus status) {
}
