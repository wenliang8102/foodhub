package com.foodhub.social.vo;

import java.util.List;

public record PostPageView(
        int page,
        int pageSize,
        long total,
        List<PostView> items) {
}
