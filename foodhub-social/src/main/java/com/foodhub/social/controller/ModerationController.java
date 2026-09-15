package com.foodhub.social.controller;

import com.foodhub.common.core.ApiResponse;
import com.foodhub.social.dto.UpdatePostVisibilityRequest;
import com.foodhub.social.service.ModerationService;
import com.foodhub.social.web.SocialRequestIdentity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts")
public class ModerationController {

    private final ModerationService moderationService;

    public ModerationController(ModerationService moderationService) {
        this.moderationService = moderationService;
    }

    @PatchMapping("/{postId}/visibility")
    public ApiResponse<Void> updateVisibility(
            @PathVariable long postId,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader,
            @Valid @RequestBody UpdatePostVisibilityRequest request) {
        SocialRequestIdentity.requireAdminRole(roleHeader);
        moderationService.updateVisibility(
                SocialRequestIdentity.requireUserId(userIdHeader), postId, request);
        return ApiResponse.success(null);
    }
}
