package com.foodhub.auth.controller;

import com.foodhub.common.security.JwtPrincipal;
import com.foodhub.auth.service.AuthService;
import com.foodhub.auth.vo.UserProfile;
import com.foodhub.common.core.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/me")
    public ApiResponse<UserProfile> currentUser(@AuthenticationPrincipal JwtPrincipal principal) {
        return ApiResponse.success(authService.currentUser(principal.userId()));
    }
}
