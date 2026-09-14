package com.foodhub.auth.controller;

import com.foodhub.auth.dto.LoginRequest;
import com.foodhub.auth.dto.RegisterRequest;
import com.foodhub.common.security.JwtPrincipal;
import com.foodhub.auth.service.AuthService;
import com.foodhub.auth.vo.AuthResponse;
import com.foodhub.auth.vo.UserProfile;
import com.foodhub.common.core.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.logout(resolveToken(authorization));
        return ApiResponse.success(null);
    }

    @GetMapping("/me")
    public ApiResponse<UserProfile> currentUser(@AuthenticationPrincipal JwtPrincipal principal) {
        return ApiResponse.success(authService.currentUser(principal.userId()));
    }

    private String resolveToken(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        return authorization.substring(7).trim();
    }
}
