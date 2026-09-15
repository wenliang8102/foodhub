package com.foodhub.social.controller;

import com.foodhub.common.core.ApiResponse;
import com.foodhub.common.core.BusinessException;
import com.foodhub.social.dto.CreateCommentRequest;
import com.foodhub.social.service.CommentService;
import com.foodhub.social.vo.CommentPageView;
import com.foodhub.social.vo.CommentView;
import com.foodhub.social.web.SocialRequestIdentity;
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
@RequestMapping("/api/posts/{postId}/comments")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @PostMapping
    public ApiResponse<CommentView> create(
            @PathVariable long postId,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @Valid @RequestBody CreateCommentRequest request) {
        return ApiResponse.success(commentService.create(
                SocialRequestIdentity.requireUserId(userIdHeader), postId, request));
    }

    @GetMapping
    public ApiResponse<CommentPageView> list(
            @PathVariable long postId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        validatePagination(page, pageSize);
        return ApiResponse.success(commentService.list(postId, page, pageSize));
    }

    @DeleteMapping("/{commentId}")
    public ApiResponse<Void> delete(
            @PathVariable long postId,
            @PathVariable long commentId,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        commentService.delete(SocialRequestIdentity.requireUserId(userIdHeader), postId, commentId);
        return ApiResponse.success(null);
    }

    private void validatePagination(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(
                    "VALIDATION_ERROR", "page 必须大于等于 1，pageSize 必须在 1 到 100 之间");
        }
    }
}
