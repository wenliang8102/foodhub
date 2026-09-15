package com.foodhub.social.vo;

import java.time.OffsetDateTime;
import java.util.List;

public record PostView(
        Long id,
        Long authorId,
        String content,
        List<String> imageUrls,
        Long merchantId,
        OffsetDateTime publishedAt) {
}
