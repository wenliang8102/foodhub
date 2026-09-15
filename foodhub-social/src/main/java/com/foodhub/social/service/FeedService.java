package com.foodhub.social.service;

import com.foodhub.common.core.BusinessException;
import com.foodhub.social.entity.PostEntity;
import com.foodhub.social.mapper.PostMapper;
import com.foodhub.social.vo.FeedPageView;
import com.foodhub.social.vo.PostView;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class FeedService {

    private static final ZoneId DATABASE_ZONE = ZoneId.of("Asia/Shanghai");

    private final PostMapper postMapper;
    private final SocialPostViewAssembler assembler;
    private final SocialMetrics metrics;

    public FeedService(PostMapper postMapper,
                       SocialPostViewAssembler assembler,
                       SocialMetrics metrics) {
        this.postMapper = postMapper;
        this.assembler = assembler;
        this.metrics = metrics;
    }

    public FeedPageView feed(Long currentUserId,
                             int limit,
                             OffsetDateTime beforePublishedAt,
                             Long beforePostId) {
        long viewerUserId = requireUserId(currentUserId);
        validateCursor(beforePublishedAt, beforePostId);
        int normalizedLimit = validateLimit(limit);
        long start = System.nanoTime();
        List<PostEntity> rows = beforePublishedAt == null
                ? postMapper.selectFeedFirstPage(viewerUserId, normalizedLimit + 1)
                : postMapper.selectFeedAfterCursor(
                        viewerUserId,
                        beforePublishedAt.atZoneSameInstant(DATABASE_ZONE).toLocalDateTime(),
                        beforePostId,
                        normalizedLimit + 1);
        boolean hasMore = rows.size() > normalizedLimit;
        List<PostEntity> pageRows = hasMore ? rows.subList(0, normalizedLimit) : rows;
        List<PostView> items = pageRows.stream()
                .map(post -> assembler.toView(post, viewerUserId))
                .toList();
        metrics.recordTime("social.feed.query", Duration.ofNanos(System.nanoTime() - start),
                "result", items.isEmpty() ? "empty" : "ok");
        if (!hasMore || items.isEmpty()) {
            return new FeedPageView(normalizedLimit, null, null, items);
        }
        PostEntity cursor = pageRows.getLast();
        return new FeedPageView(
                normalizedLimit,
                cursor.getPublishedAt().atZone(DATABASE_ZONE).toOffsetDateTime(),
                cursor.getId(),
                items);
    }

    private long requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException("INVALID_USER_ID", "用户 ID 必须为正整数");
        }
        return userId;
    }

    private int validateLimit(int limit) {
        if (limit < 1 || limit > 50) {
            throw new BusinessException("VALIDATION_ERROR", "limit 必须在 1 到 50 之间");
        }
        return limit;
    }

    private void validateCursor(OffsetDateTime beforePublishedAt, Long beforePostId) {
        if ((beforePublishedAt == null) != (beforePostId == null)) {
            throw new BusinessException(
                    "VALIDATION_ERROR", "Feed 游标时间和动态 ID 必须同时提供或同时省略");
        }
        if (beforePostId != null && beforePostId <= 0) {
            throw new BusinessException("VALIDATION_ERROR", "Feed 游标动态 ID 必须为正整数");
        }
    }
}
