package com.foodhub.social.vo;

import java.time.OffsetDateTime;

public record FollowView(Long userId, OffsetDateTime followedAt) {
}
