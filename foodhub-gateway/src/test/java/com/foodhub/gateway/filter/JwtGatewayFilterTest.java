package com.foodhub.gateway.filter;

import com.foodhub.common.redis.RedisKeys;
import com.foodhub.common.security.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtGatewayFilterTest {

    private static final String SECRET = "test-secret-with-at-least-32-characters-long";

    @Test
    void allowsPublicLoginWithoutToken() {
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        JwtGatewayFilter filter = new JwtGatewayFilter(new JwtTokenService(SECRET, java.time.Duration.ofHours(2)), redis);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
        MockServerWebExchange exchange = exchange("/api/auth/login", null, "spoofed");

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(any());
        assertThat(exchange.getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("spoofed");
        verify(redis, never()).hasKey(any());
    }

    @Test
    void allowsNonApiHealthEndpointWithoutToken() {
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        JwtGatewayFilter filter = new JwtGatewayFilter(
                new JwtTokenService(SECRET, java.time.Duration.ofHours(2)), redis);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange("/actuator/health", null, null), chain))
                .verifyComplete();

        verify(chain).filter(any());
        verify(redis, never()).hasKey(any());
    }

    @Test
    void rejectsMissingTokenForProtectedPath() {
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        JwtGatewayFilter filter = new JwtGatewayFilter(new JwtTokenService(SECRET, java.time.Duration.ofHours(2)), redis);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        MockServerWebExchange exchange = exchange("/api/orders", null, "spoofed");

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
        verify(redis, never()).hasKey(any());
    }

    @Test
    void allowsPublicCatalogSocialCouponAndSeckillReadsWithoutToken() {
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        JwtGatewayFilter filter = new JwtGatewayFilter(
                new JwtTokenService(SECRET, java.time.Duration.ofHours(2)), redis);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        for (String path : new String[]{
                "/api/categories", "/api/merchants/1", "/api/foods",
                "/api/coupons", "/api/coupons/1", "/api/seckill/activities",
                "/api/seckill/activities/1", "/api/posts", "/api/posts/1",
                "/api/posts/1/comments"}) {
            StepVerifier.create(filter.filter(exchange(path, null, "spoofed"), chain))
                    .verifyComplete();
        }

        verify(chain, org.mockito.Mockito.times(10)).filter(any());
        verify(redis, never()).hasKey(any());
    }

    @Test
    void keepsPrivateReadsAndWriteRequestsProtected() {
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        JwtGatewayFilter filter = new JwtGatewayFilter(
                new JwtTokenService(SECRET, java.time.Duration.ofHours(2)), redis);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        StepVerifier.create(filter.filter(exchange("/api/posts/favorites", null, null), chain))
                .verifyComplete();
        StepVerifier.create(filter.filter(
                        exchange("/api/seckill/activities/1/result", null, null), chain))
                .verifyComplete();
        StepVerifier.create(filter.filter(postExchange("/api/posts", null), chain))
                .verifyComplete();

        verify(chain, never()).filter(any());
    }

    @Test
    void injectsIdentityOnPublicReadWhenActiveTokenIsPresent() {
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        JwtTokenService tokenService = new JwtTokenService(SECRET, java.time.Duration.ofHours(2));
        String token = tokenService.issue(7L, "alice", "USER");
        when(redis.hasKey(RedisKeys.loginToken(token))).thenReturn(Mono.just(true));
        JwtGatewayFilter filter = new JwtGatewayFilter(tokenService, redis);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenAnswer(invocation -> {
            ServerWebExchange forwarded = invocation.getArgument(0);
            assertThat(forwarded.getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("7");
            return Mono.empty();
        });

        StepVerifier.create(filter.filter(exchange("/api/posts/1", token, "spoofed"), chain))
                .verifyComplete();

        verify(chain).filter(any());
    }

    @Test
    void rejectsValidButLoggedOutToken() {
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        JwtTokenService tokenService = new JwtTokenService(SECRET, java.time.Duration.ofHours(2));
        String token = tokenService.issue(7L, "alice", "USER");
        when(redis.hasKey(RedisKeys.loginToken(token))).thenReturn(Mono.just(false));
        JwtGatewayFilter filter = new JwtGatewayFilter(tokenService, redis);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        StepVerifier.create(filter.filter(exchange("/api/orders", token, null), chain)).verifyComplete();

        verify(chain, never()).filter(any());
    }

    @Test
    void injectsTrustedIdentityHeadersForActiveTokenAndRemovesSpoofedValues() {
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        JwtTokenService tokenService = new JwtTokenService(SECRET, java.time.Duration.ofHours(2));
        String token = tokenService.issue(7L, "alice", "USER");
        when(redis.hasKey(RedisKeys.loginToken(token))).thenReturn(Mono.just(true));
        JwtGatewayFilter filter = new JwtGatewayFilter(tokenService, redis);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenAnswer(invocation -> {
            ServerWebExchange forwarded = invocation.getArgument(0);
            assertThat(forwarded.getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("7");
            assertThat(forwarded.getRequest().getHeaders().getFirst("X-Username")).isEqualTo("alice");
            assertThat(forwarded.getRequest().getHeaders().getFirst("X-User-Role")).isEqualTo("USER");
            return Mono.empty();
        });

        StepVerifier.create(filter.filter(exchange("/api/orders", token, "spoofed"), chain)).verifyComplete();

        verify(chain).filter(any());
    }

    private MockServerWebExchange exchange(String path, String token, String spoofedUserId) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.get(path);
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (spoofedUserId != null) {
            builder.header("X-User-Id", spoofedUserId);
        }
        return MockServerWebExchange.from(builder.build());
    }

    private MockServerWebExchange postExchange(String path, String token) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.post(path);
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return MockServerWebExchange.from(builder.build());
    }
}
