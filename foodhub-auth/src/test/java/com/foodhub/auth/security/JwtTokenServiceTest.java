package com.foodhub.auth.security;

import com.foodhub.common.security.JwtPrincipal;
import com.foodhub.common.security.JwtTokenService;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {

    private static final String SECRET = "test-secret-with-at-least-32-characters-long";

    @Test
    void issueAndParsePreservesIdentityClaims() {
        JwtTokenService service = new JwtTokenService(SECRET, Duration.ofHours(2));

        String token = service.issue(42L, "alice", "USER");
        JwtPrincipal principal = service.parsePrincipal(token);

        assertThat(principal).isEqualTo(new JwtPrincipal(42L, "alice", "USER"));
        assertThat(service.tokenTtl()).isEqualTo(Duration.ofHours(2));
    }

    @Test
    void rejectsTamperedToken() {
        JwtTokenService service = new JwtTokenService(SECRET, Duration.ofHours(2));
        String token = service.issue(42L, "alice", "USER");
        String tampered = token.substring(0, token.length() - 1)
                + (token.endsWith("a") ? "b" : "a");

        assertThatThrownBy(() -> service.parsePrincipal(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsWeakSigningSecret() {
        assertThatThrownBy(() -> new JwtTokenService("too-short", Duration.ofHours(2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 32 bytes");
    }
}
