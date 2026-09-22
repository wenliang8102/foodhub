package com.foodhub.coupon.vo;

import java.time.OffsetDateTime;

public record UserCouponView(
        Long id,
        String status,
        OffsetDateTime claimedAt,
        CouponView coupon) {
}
