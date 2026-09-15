package com.foodhub.social.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCommentRequest(
        @NotNull(message = "评论内容不能为空")
        @Size(max = 500, message = "评论内容最多 500 个字符")
        String content) {
}
