package com.foodhub.social.vo;

import java.time.OffsetDateTime;

public record CommentView(
        Long id,
        Long postId,
        Long authorId,
        String content,
        OffsetDateTime createdAt) {
}
