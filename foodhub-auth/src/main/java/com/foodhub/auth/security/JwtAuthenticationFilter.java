package com.foodhub.auth.security;

import com.foodhub.common.redis.RedisKeys;
import com.foodhub.common.security.JwtPrincipal;
import com.foodhub.common.security.JwtTokenService;
import io.jsonwebtoken.JwtException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final StringRedisTemplate redisTemplate;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService, StringRedisTemplate redisTemplate) {
        this.jwtTokenService = jwtTokenService;
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                JwtPrincipal principal = jwtTokenService.parsePrincipal(token);
                String storedUserId = redisTemplate.opsForValue().get(RedisKeys.loginToken(token));
                if (principal.userId().toString().equals(storedUserId)) {
                    var authority = new SimpleGrantedAuthority(
                            "ROLE_" + principal.role().toUpperCase(Locale.ROOT));
                    var authentication = new UsernamePasswordAuthenticationToken(
                            principal, null, java.util.List.of(authority));
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (JwtException | IllegalArgumentException ignored) {
                // Invalid credentials are treated as anonymous; Spring Security returns 401.
            }
        }
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (!StringUtils.hasText(header) || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = header.substring(7).trim();
        return StringUtils.hasText(token) ? token : null;
    }
}
