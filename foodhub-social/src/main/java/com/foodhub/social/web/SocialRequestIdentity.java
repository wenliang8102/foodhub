package com.foodhub.social.web;

import com.foodhub.common.core.BusinessException;

public final class SocialRequestIdentity {

    private SocialRequestIdentity() {
    }

    public static long requireUserId(String value) {
        if (value == null || value.isBlank()) {
            throw invalidUserId();
        }
        return parseUserId(value);
    }

    public static Long optionalUserId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return parseUserId(value);
    }

    public static boolean isAdmin(String value) {
        return "ADMIN".equals(value);
    }

    public static void requireAdminRole(String value) {
        if (!isAdmin(value)) {
            throw new BusinessException("MODERATION_FORBIDDEN", "只有管理员可以执行内容治理操作");
        }
    }

    private static long parseUserId(String value) {
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
