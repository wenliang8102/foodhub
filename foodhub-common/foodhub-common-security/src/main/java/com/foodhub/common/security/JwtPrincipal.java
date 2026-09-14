package com.foodhub.common.security;

/** Identity claims shared by edge and business services. */
public record JwtPrincipal(Long userId, String username, String role) {
}
