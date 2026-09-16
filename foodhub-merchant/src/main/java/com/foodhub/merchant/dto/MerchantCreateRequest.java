package com.foodhub.merchant.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record MerchantCreateRequest(
        @NotBlank @Size(max = 80) String name,
        @NotNull Long categoryId,
        @NotBlank @Size(max = 255) String address,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude) {
}
