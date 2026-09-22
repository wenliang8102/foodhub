package com.foodhub.coupon.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record SeckillActivityCreateRequest(
        @NotNull @Positive Long merchantId,
        @NotBlank @Pattern(regexp = "FOOD_ITEM|COUPON") String targetType,
        @NotNull @Positive Long targetId,
        @NotBlank @Size(max = 100) String title,
        @NotNull @DecimalMin("0") BigDecimal seckillPrice,
        @NotNull @Positive Integer totalStock,
        @NotNull OffsetDateTime startAt,
        @NotNull OffsetDateTime endAt) {
}
