package com.foodhub.social.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodhub.common.core.BusinessException;
import com.foodhub.social.entity.PostEntity;
import com.foodhub.social.mapper.InteractionMapper;
import com.foodhub.social.mapper.PostMapper;
import com.foodhub.social.vo.PostPageView;
import com.foodhub.social.vo.PostView;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class InteractionService {

    private static final ZoneId DATABASE_ZONE = ZoneId.of("Asia/Shanghai");
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    private final PostMapper postMapper;
    private final InteractionMapper interactionMapper;
    private final ObjectMapper objectMapper;
    private final SocialPostCache postCache;
    private final SocialMetrics metrics;

    @Autowired
    public InteractionService(PostMapper postMapper,
                              InteractionMapper interactionMapper,
                              ObjectMapper objectMapper,
                              ObjectProvider<SocialPostCache> postCache,
                              SocialMetrics metrics) {
        this.postMapper = postMapper;
        this.interactionMapper = interactionMapper;
        this.objectMapper = objectMapper;
        this.postCache = postCache.getIfAvailable();
        this.metrics = metrics;
    }

    public InteractionService(PostMapper postMapper,
                              InteractionMapper interactionMapper,
                              ObjectMapper objectMapper) {
        this.postMapper = postMapper;
        this.interactionMapper = interactionMapper;
        this.objectMapper = objectMapper;
        this.postCache = null;
        this.metrics = null;
    }

    @Transactional
    public void like(Long currentUserId, long postId) {
        long userId = requireUserId(currentUserId);
        requireVisiblePost(postId);
        int inserted = interactionMapper.insertLikeIfAbsent(
                userId, postId, LocalDateTime.now(DATABASE_ZONE));
        if (inserted == 1) {
            interactionMapper.incrementLikeCount(postId);
            evict(postId);
            incrementMetric("social.interaction.write", "action", "like", "result", "created");
        } else {
            incrementMetric("social.interaction.write", "action", "like", "result", "idempotent");
        }
    }

    @Transactional
    public void unlike(Long currentUserId, long postId) {
        long userId = requireUserId(currentUserId);
        requireVisiblePost(postId);
        int deleted = interactionMapper.deleteLike(userId, postId);
        if (deleted == 1) {
            interactionMapper.decrementLikeCount(postId);
            evict(postId);
            incrementMetric("social.interaction.write", "action", "unlike", "result", "deleted");
        } else {
            incrementMetric("social.interaction.write", "action", "unlike", "result", "idempotent");
        }
    }

    @Transactional
    public void favorite(Long currentUserId, long postId) {
        long userId = requireUserId(currentUserId);
        requireVisiblePost(postId);
        int inserted = interactionMapper.insertFavoriteIfAbsent(
                userId, postId, LocalDateTime.now(DATABASE_ZONE));
        if (inserted == 1) {
            interactionMapper.incrementFavoriteCount(postId);
            evict(postId);
            incrementMetric("social.interaction.write", "action", "favorite", "result", "created");
        } else {
            incrementMetric("social.interaction.write", "action", "favorite", "result", "idempotent");
        }
    }

    @Transactional
    public void unfavorite(Long currentUserId, long postId) {
        long userId = requireUserId(currentUserId);
        requireVisiblePost(postId);
        int deleted = interactionMapper.deleteFavorite(userId, postId);
        if (deleted == 1) {
            interactionMapper.decrementFavoriteCount(postId);
            evict(postId);
            incrementMetric("social.interaction.write", "action", "unfavorite", "result", "deleted");
        } else {
            incrementMetric("social.interaction.write", "action", "unfavorite", "result", "idempotent");
        }
    }

    public PostPageView favorites(Long currentUserId, int page, int pageSize) {
        long userId = requireUserId(currentUserId);
        validatePagination(page, pageSize);
        long total = interactionMapper.countVisibleFavorites(userId);
        if (total == 0) {
            return new PostPageView(page, pageSize, 0, List.of());
        }
        long offset = (long) (page - 1) * pageSize;
        List<PostView> items = interactionMapper
                .selectVisibleFavoritePage(userId, offset, pageSize).stream()
                .map(post -> toView(post, userId, true))
                .toList();
        return new PostPageView(page, pageSize, total, items);
    }

    private void requireVisiblePost(long postId) {
        if (postId <= 0 || postMapper.selectVisibleById(postId) == null) {
            throw new BusinessException("POST_NOT_FOUND", "帖子不存在或已删除");
        }
    }

    private long requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException("INVALID_USER_ID", "用户 ID 必须为正整数");
        }
        return userId;
    }

    private void validatePagination(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(
                    "VALIDATION_ERROR",
                    "page 必须大于等于 1，pageSize 必须在 1 到 100 之间");
        }
    }

    private PostView toView(PostEntity post, long viewerUserId, boolean favorited) {
        if (post.getPublishedAt() == null) {
            throw new BusinessException("POST_DATA_INVALID", "帖子发布时间缺失");
        }
        return new PostView(
                post.getId(),
                post.getAuthorId(),
                post.getContent(),
                readImages(post.getImageUrls()),
                post.getMerchantId(),
                post.getPublishedAt().atZone(DATABASE_ZONE).toOffsetDateTime(),
                count(post.getLikeCount()),
                count(post.getFavoriteCount()),
                count(post.getCommentCount()),
                interactionMapper.existsLike(viewerUserId, post.getId()),
                favorited);
    }

    private long count(Long value) {
        return value == null ? 0 : value;
    }

    private void evict(long postId) {
        if (postCache != null) {
            postCache.evictPost(postId);
        }
    }

    private void incrementMetric(String name, String... tags) {
        if (metrics != null) {
            metrics.increment(name, tags);
        }
    }

    private List<String> readImages(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return List.copyOf(objectMapper.readValue(json, STRING_LIST));
        } catch (JsonProcessingException | NullPointerException exception) {
            throw new BusinessException("POST_DATA_INVALID", "帖子图片数据格式错误");
        }
    }
}
