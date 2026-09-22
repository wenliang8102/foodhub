package com.foodhub.coupon.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CouponCreateRequest(
        @NotNull @Positive Long merchantId,
        @NotBlank @Size(max = 100) String name,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal faceValue,
        @NotNull @DecimalMin("0") BigDecimal minSpend,
        @NotNull @Positive Integer totalStock,
        @NotNull OffsetDateTime validFrom,
        @NotNull OffsetDateTime validUntil) {
}
