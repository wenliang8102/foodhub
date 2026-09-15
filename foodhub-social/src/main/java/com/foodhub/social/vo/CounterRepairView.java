package com.foodhub.social.vo;

public record CounterRepairView(
        long postId,
        long likeCount,
        long favoriteCount,
        long commentCount) {
}
