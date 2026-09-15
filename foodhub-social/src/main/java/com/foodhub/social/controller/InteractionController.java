package com.foodhub.social.controller;

import com.foodhub.common.core.ApiResponse;
import com.foodhub.common.core.BusinessException;
import com.foodhub.social.service.InteractionService;
import com.foodhub.social.vo.PostPageView;
import com.foodhub.social.web.SocialRequestIdentity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts")
public class InteractionController {

    private final InteractionService interactionService;

    public InteractionController(InteractionService interactionService) {
        this.interactionService = interactionService;
    }

    @PostMapping("/{postId}/likes")
    public ApiResponse<Void> like(
            @PathVariable long postId,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        interactionService.like(SocialRequestIdentity.requireUserId(userIdHeader), postId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{postId}/likes")
    public ApiResponse<Void> unlike(
            @PathVariable long postId,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        interactionService.unlike(SocialRequestIdentity.requireUserId(userIdHeader), postId);
        return ApiResponse.success(null);
    }

    @PostMapping("/{postId}/favorite")
    public ApiResponse<Void> favorite(
            @PathVariable long postId,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        interactionService.favorite(SocialRequestIdentity.requireUserId(userIdHeader), postId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{postId}/favorite")
    public ApiResponse<Void> unfavorite(
            @PathVariable long postId,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        interactionService.unfavorite(
                SocialRequestIdentity.requireUserId(userIdHeader), postId);
        return ApiResponse.success(null);
    }

    @GetMapping("/favorites")
    public ApiResponse<PostPageView> favorites(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        validatePagination(page, pageSize);
        return ApiResponse.success(interactionService.favorites(
                SocialRequestIdentity.requireUserId(userIdHeader), page, pageSize));
    }

    private void validatePagination(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(
                    "VALIDATION_ERROR", "page 必须大于等于 1，pageSize 必须在 1 到 100 之间");
        }
    }
}
