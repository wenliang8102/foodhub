package com.foodhub.social.controller;

import com.foodhub.common.core.ApiResponse;
import com.foodhub.common.core.BusinessException;
import com.foodhub.social.service.FollowService;
import com.foodhub.social.vo.FollowPageView;
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
@RequestMapping("/api/follows")
public class FollowController {

    private final FollowService followService;

    public FollowController(FollowService followService) {
        this.followService = followService;
    }

    @PostMapping("/{userId}")
    public ApiResponse<Void> follow(
            @PathVariable String userId,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        followService.follow(
                SocialRequestIdentity.requireUserId(userIdHeader),
                SocialRequestIdentity.requireUserId(userId));
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{userId}")
    public ApiResponse<Void> unfollow(
            @PathVariable String userId,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        followService.unfollow(
                SocialRequestIdentity.requireUserId(userIdHeader),
                SocialRequestIdentity.requireUserId(userId));
        return ApiResponse.success(null);
    }

    @GetMapping("/following")
    public ApiResponse<FollowPageView> following(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        long currentUserId = SocialRequestIdentity.requireUserId(userIdHeader);
        validatePagination(page, pageSize);
        return ApiResponse.success(followService.following(currentUserId, page, pageSize));
    }

    @GetMapping("/followers")
    public ApiResponse<FollowPageView> followers(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        long currentUserId = SocialRequestIdentity.requireUserId(userIdHeader);
        validatePagination(page, pageSize);
        return ApiResponse.success(followService.followers(currentUserId, page, pageSize));
    }

    private void validatePagination(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(
                    "VALIDATION_ERROR",
                    "page 必须大于等于 1，pageSize 必须在 1 到 100 之间");
        }
    }
}
