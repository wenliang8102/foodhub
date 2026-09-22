package com.foodhub.coupon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SeckillSubmitRequest(
        @NotBlank @Size(max = 64) String path) {
}
