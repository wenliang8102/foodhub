package com.foodhub.auth.vo;

public record AuthResponse(String token, UserProfile user) {
}

