package com.foodhub.social.web;

import com.foodhub.common.core.BusinessException;

public final class SocialRequestIdentity {

    private SocialRequestIdentity() {
    }

    public static long requireUserId(String value) {
        if (value == null || value.isBlank()) {
            throw invalidUserId();
        }
        try {
            long userId = Long.parseLong(value.trim());
            if (userId <= 0) {
                throw invalidUserId();
            }
            return userId;
        } catch (NumberFormatException exception) {
            throw invalidUserId();
        }
    }

    private static BusinessException invalidUserId() {
        return new BusinessException("INVALID_USER_ID", "用户 ID 必须为正整数");
    }
}
