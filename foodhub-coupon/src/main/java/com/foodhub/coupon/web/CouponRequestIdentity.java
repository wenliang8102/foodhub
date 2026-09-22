package com.foodhub.coupon.web;

import com.foodhub.common.core.BusinessException;

import java.util.Locale;

public record CouponRequestIdentity(long userId, String role) {

    public static CouponRequestIdentity require(String rawUserId, String rawRole) {
        if (rawUserId == null || rawUserId.isBlank()) {
            throw new BusinessException("UNAUTHORIZED", "X-User-Id header is required");
        }
        try {
            long userId = Long.parseLong(rawUserId.trim());
            if (userId <= 0) {
                throw invalidUserId();
            }
            String role = rawRole == null ? "" : rawRole.trim().toUpperCase(Locale.ROOT);
            return new CouponRequestIdentity(userId, role);
        } catch (NumberFormatException exception) {
            throw invalidUserId();
        }
    }

    public void requireMerchantOrAdmin() {
        if (!isMerchant() && !isAdmin()) {
            throw new BusinessException("COUPON_FORBIDDEN", "MERCHANT or ADMIN role is required");
        }
    }

    public boolean isMerchant() {
        return "MERCHANT".equals(role);
    }

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    private static BusinessException invalidUserId() {
        return new BusinessException("UNAUTHORIZED", "X-User-Id header must be a positive number");
    }
}
