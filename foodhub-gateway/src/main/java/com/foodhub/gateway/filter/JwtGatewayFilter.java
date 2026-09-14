package com.foodhub.gateway.filter;

import com.foodhub.common.redis.RedisKeys;
import com.foodhub.common.security.JwtPrincipal;
import com.foodhub.common.security.JwtTokenService;
import io.jsonwebtoken.JwtException;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import reactor.core.publisher.Mono;

import java.util.Locale;

@Component
public class JwtGatewayFilter implements GlobalFilter, org.springframework.core.Ordered {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USERNAME_HEADER = "X-Username";
    private static final String USER_ROLE_HEADER = "X-User-Role";

    private final JwtTokenService jwtTokenService;
    private final ReactiveStringRedisTemplate redisTemplate;

    public JwtGatewayFilter(JwtTokenService jwtTokenService,
                            ReactiveStringRedisTemplate redisTemplate) {
        this.jwtTokenService = jwtTokenService;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        ServerWebExchange sanitizedExchange = removeIdentityHeaders(exchange);
        if (!requiresAuthentication(path, exchange.getRequest().getMethod())) {
            return chain.filter(sanitizedExchange);
        }

        String token = resolveToken(exchange.getRequest().getHeaders());
        if (token == null) {
            return unauthorized(exchange);
        }

        final JwtPrincipal principal;
        try {
            principal = jwtTokenService.parsePrincipal(token);
        } catch (JwtException | IllegalArgumentException exception) {
            return unauthorized(exchange);
        }

        return redisTemplate.hasKey(RedisKeys.loginToken(token))
                .flatMap(active -> active
                        ? chain.filter(withIdentityHeaders(sanitizedExchange, principal))
                        : unauthorized(exchange));
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private boolean requiresAuthentication(String path, HttpMethod method) {
        if (method == HttpMethod.OPTIONS || !path.startsWith("/api/")) {
            return false;
        }
        return !isPublicAuthenticationPath(path);
    }

    private boolean isPublicAuthenticationPath(String path) {
        return "/api/auth/login".equals(path)
                || "/api/auth/register".equals(path)
                || "/api/auth/login/".equals(path)
                || "/api/auth/register/".equals(path);
    }

    private String resolveToken(HttpHeaders headers) {
        String authorization = headers.getFirst(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization)
                || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = authorization.substring(7).trim();
        return StringUtils.hasText(token) ? token : null;
    }

    private ServerWebExchange removeIdentityHeaders(ServerWebExchange exchange) {
        var request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.remove(USERNAME_HEADER);
                    headers.remove(USER_ROLE_HEADER);
                })
                .build();
        return exchange.mutate().request(request).build();
    }

    private ServerWebExchange withIdentityHeaders(ServerWebExchange exchange, JwtPrincipal principal) {
        var request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.set(USER_ID_HEADER, principal.userId().toString());
                    headers.set(USERNAME_HEADER, principal.username());
                    headers.set(USER_ROLE_HEADER, principal.role().toUpperCase(Locale.ROOT));
                })
                .build();
        return exchange.mutate().request(request).build();
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}
