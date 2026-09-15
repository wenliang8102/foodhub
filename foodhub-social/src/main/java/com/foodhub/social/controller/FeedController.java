package com.foodhub.social.controller;

import com.foodhub.common.core.ApiResponse;
import com.foodhub.social.service.FeedService;
import com.foodhub.social.vo.FeedPageView;
import com.foodhub.social.web.SocialRequestIdentity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/feed")
public class FeedController {

    private final FeedService feedService;

    public FeedController(FeedService feedService) {
        this.feedService = feedService;
    }

    @GetMapping
    public ApiResponse<FeedPageView> feed(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) OffsetDateTime beforePublishedAt,
            @RequestParam(required = false) Long beforePostId) {
        return ApiResponse.success(feedService.feed(
                SocialRequestIdentity.requireUserId(userIdHeader),
                limit,
                beforePublishedAt,
                beforePostId));
    }
}
