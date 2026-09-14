package com.foodhub.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtTokenService {

    private final SecretKey signingKey;
    private final Duration tokenTtl;

    public JwtTokenService(
            @Value("${foodhub.security.jwt.secret}") String secret,
            @Value("${foodhub.security.jwt.ttl:PT2H}") Duration tokenTtl) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalArgumentException("foodhub.security.jwt.secret must contain at least 32 bytes");
        }
        if (tokenTtl.isZero() || tokenTtl.isNegative()) {
            throw new IllegalArgumentException("foodhub.security.jwt.ttl must be positive");
        }
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
        this.tokenTtl = tokenTtl;
    }

    public String issue(Long userId, String username, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("uid", userId)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(tokenTtl)))
                .signWith(signingKey)
                .compact();
    }

    public JwtPrincipal parsePrincipal(String token) {
        return principal(parse(token));
    }

    public Jws<Claims> parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token);
    }

    public JwtPrincipal principal(Jws<Claims> claims) {
        Claims body = claims.getPayload();
        Object rawUserId = body.get("uid");
        String role = body.get("role", String.class);
        if (!(rawUserId instanceof Number userId)
                || role == null
                || body.getSubject() == null) {
            throw new JwtException("Required claims are missing");
        }
        return new JwtPrincipal(userId.longValue(), body.getSubject(), role);
    }

    public Duration tokenTtl() {
        return tokenTtl;
    }
}
