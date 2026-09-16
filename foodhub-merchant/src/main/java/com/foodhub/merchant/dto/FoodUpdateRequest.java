package com.foodhub.merchant.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record FoodUpdateRequest(
        @Size(max = 80) String name,
        @DecimalMin("0.00") BigDecimal price,
        @Size(max = 512) String imageUrl,
        Boolean onSale,
        ResourceStatus status) {
}
