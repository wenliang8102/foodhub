package com.foodhub.social.controller;

import com.foodhub.common.core.ApiResponse;
import com.foodhub.common.core.BusinessException;
import com.foodhub.social.dto.CreatePostRequest;
import com.foodhub.social.service.PostService;
import com.foodhub.social.vo.PostPageView;
import com.foodhub.social.vo.PostView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @PostMapping
    public ApiResponse<PostView> create(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @Valid @RequestBody CreatePostRequest request) {
        return ApiResponse.success(postService.create(resolveUserId(userIdHeader), request));
    }

    @GetMapping
    public ApiResponse<PostPageView> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        validatePagination(page, pageSize);
        return ApiResponse.success(postService.list(page, pageSize));
    }

    @GetMapping("/{postId}")
    public ApiResponse<PostView> detail(@PathVariable long postId) {
        return ApiResponse.success(postService.detail(postId));
    }

    @DeleteMapping("/{postId}")
    public ApiResponse<Void> delete(
            @PathVariable long postId,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        postService.delete(postId, resolveUserId(userIdHeader));
        return ApiResponse.success(null);
    }

    private Long resolveUserId(String userIdHeader) {
        if (userIdHeader == null || userIdHeader.isBlank()) {
            throw invalidUserId();
        }
        try {
            long userId = Long.parseLong(userIdHeader);
            if (userId <= 0) {
                throw invalidUserId();
            }
            return userId;
        } catch (NumberFormatException exception) {
            throw invalidUserId();
        }
    }

    private BusinessException invalidUserId() {
        return new BusinessException("INVALID_USER_ID", "X-User-Id 必须为正整数");
    }

    private void validatePagination(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(
                    "VALIDATION_ERROR", "page 必须大于等于 1，pageSize 必须在 1 到 100 之间");
        }
    }
}
