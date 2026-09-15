package com.foodhub.social.vo;

import java.time.OffsetDateTime;
import java.util.List;

public record FeedPageView(
        int limit,
        OffsetDateTime nextBeforePublishedAt,
        Long nextBeforePostId,
        List<PostView> items) {
}
