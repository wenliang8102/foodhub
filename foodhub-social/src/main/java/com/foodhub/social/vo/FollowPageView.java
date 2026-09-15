package com.foodhub.social.vo;

import java.util.List;

public record FollowPageView(
        int page,
        int pageSize,
        long total,
        List<FollowView> items) {
}
