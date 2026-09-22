package com.foodhub.coupon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CouponStatusUpdateRequest(
        @NotBlank
        @Pattern(regexp = "ACTIVE|INACTIVE", message = "must be ACTIVE or INACTIVE")
        String status) {
}
