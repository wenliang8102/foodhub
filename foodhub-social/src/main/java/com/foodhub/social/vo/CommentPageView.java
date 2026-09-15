package com.foodhub.social.vo;

import java.util.List;

public record CommentPageView(
        int page,
        int pageSize,
        long total,
        List<CommentView> items) {
}
