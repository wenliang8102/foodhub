package com.foodhub.merchant.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record FoodCreateRequest(
        @NotNull Long merchantId,
        @NotBlank @Size(max = 80) String name,
        @NotNull @DecimalMin("0.00") BigDecimal price,
        @Size(max = 512) String imageUrl) {
}
