package com.foodhub.social.vo;

import java.time.OffsetDateTime;
import java.util.List;

public record PostView(
        Long id,
        Long authorId,
        String content,
        List<String> imageUrls,
        Long merchantId,
        OffsetDateTime publishedAt,
        long likeCount,
        long favoriteCount,
        long commentCount,
        Boolean liked,
        Boolean favorited) {

    public PostView(Long id,
                    Long authorId,
                    String content,
                    List<String> imageUrls,
                    Long merchantId,
                    OffsetDateTime publishedAt) {
        this(id, authorId, content, imageUrls, merchantId, publishedAt, 0, 0, 0, null, null);
    }

    public PostView(Long id,
                    Long authorId,
                    String content,
                    List<String> imageUrls,
                    Long merchantId,
                    OffsetDateTime publishedAt,
                    long likeCount,
                    long favoriteCount,
                    Boolean liked,
                    Boolean favorited) {
        this(id, authorId, content, imageUrls, merchantId, publishedAt,
                likeCount, favoriteCount, 0, liked, favorited);
    }
}
