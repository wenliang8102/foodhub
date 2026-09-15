package com.foodhub.social.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodhub.common.core.BusinessException;
import com.foodhub.social.dto.CreatePostRequest;
import com.foodhub.social.entity.PostEntity;
import com.foodhub.social.mapper.InteractionMapper;
import com.foodhub.social.mapper.PostMapper;
import com.foodhub.social.vo.PostPageView;
import com.foodhub.social.vo.PostView;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class PostService {

    private static final String VISIBLE = "VISIBLE";
    private static final ZoneId DATABASE_ZONE = ZoneId.of("Asia/Shanghai");
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    private final PostMapper postMapper;
    private final InteractionMapper interactionMapper;
    private final ObjectMapper objectMapper;
    private final SocialPostCache postCache;
    private final SocialMetrics metrics;

    @Autowired
    public PostService(PostMapper postMapper,
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

    public PostService(PostMapper postMapper,
                       InteractionMapper interactionMapper,
                       ObjectMapper objectMapper) {
        this.postMapper = postMapper;
        this.interactionMapper = interactionMapper;
        this.objectMapper = objectMapper;
        this.postCache = null;
        this.metrics = null;
    }

    public PostService(PostMapper postMapper, ObjectMapper objectMapper) {
        this(postMapper, null, objectMapper);
    }

    @Transactional
    public PostView create(Long authorId, CreatePostRequest request) {
        validateUserId(authorId);
        List<String> imageUrls = normalizeAndValidateImages(request.imageUrls());
        LocalDateTime now = LocalDateTime.now(DATABASE_ZONE);

        PostEntity post = new PostEntity();
        post.setAuthorId(authorId);
        post.setContent(request.content().trim());
        post.setImageUrls(writeImages(imageUrls));
        post.setMerchantId(request.merchantId());
        post.setStatus(VISIBLE);
        post.setPublishedAt(now);
        post.setUpdatedAt(now);
        postMapper.insert(post);
        incrementMetric("social.post.write", "action", "create");
        return toView(post, null);
    }

    public PostPageView list(int page, int pageSize) {
        return list(page, pageSize, null);
    }

    public PostPageView list(int page, int pageSize, Long viewerUserId) {
        validatePagination(page, pageSize);
        Long normalizedViewerId = optionalUserId(viewerUserId);
        long total = postMapper.countVisible();
        if (total == 0) {
            return new PostPageView(page, pageSize, 0, List.of());
        }
        long offset = (long) (page - 1) * pageSize;
        List<PostView> items = postMapper.selectVisiblePage(offset, pageSize).stream()
                .map(post -> toView(post, normalizedViewerId))
                .toList();
        return new PostPageView(page, pageSize, total, items);
    }

    public PostView detail(long postId) {
        return detail(postId, null);
    }

    public PostView detail(long postId, Long viewerUserId) {
        Long normalizedViewerId = optionalUserId(viewerUserId);
        if (postId <= 0) {
            throw new BusinessException("POST_NOT_FOUND", "帖子不存在或已删除");
        }
        if (postCache != null && postCache.isNullCached(postId)) {
            throw new BusinessException("POST_NOT_FOUND", "帖子不存在或已删除");
        }
        if (postCache != null) {
            PostView cached = postCache.getPostDetail(postId).orElse(null);
            if (cached != null) {
                return withViewerState(cached, normalizedViewerId);
            }
        }
        PostEntity post = postId > 0 ? postMapper.selectVisibleById(postId) : null;
        if (post == null) {
            if (postCache != null) {
                postCache.putNull(postId);
            }
            throw new BusinessException("POST_NOT_FOUND", "帖子不存在或已删除");
        }
        PostView anonymousView = toView(post, null);
        if (postCache != null) {
            postCache.putPostDetail(anonymousView);
        }
        return withViewerState(anonymousView, normalizedViewerId);
    }

    @Transactional
    public void delete(long postId, Long currentUserId) {
        validateUserId(currentUserId);
        PostEntity post = postId > 0 ? postMapper.selectVisibleById(postId) : null;
        if (post == null) {
            throw new BusinessException("POST_NOT_FOUND", "帖子不存在或已删除");
        }
        if (!Objects.equals(post.getAuthorId(), currentUserId)) {
            throw new BusinessException("POST_FORBIDDEN", "只能删除自己发布的帖子");
        }
        int updated = postMapper.softDelete(
                postId, currentUserId, LocalDateTime.now(DATABASE_ZONE));
        if (updated != 1) {
            throw new BusinessException("POST_NOT_FOUND", "帖子不存在或已删除");
        }
        if (postCache != null) {
            postCache.evictPost(postId);
        }
        incrementMetric("social.post.write", "action", "delete");
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException("INVALID_USER_ID", "X-User-Id 必须为正整数");
        }
    }

    private Long optionalUserId(Long userId) {
        if (userId == null) {
            return null;
        }
        validateUserId(userId);
        return userId;
    }

    private void validatePagination(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(
                    "VALIDATION_ERROR", "page 必须大于等于 1，pageSize 必须在 1 到 100 之间");
        }
    }

    private List<String> normalizeAndValidateImages(List<String> imageUrls) {
        if (imageUrls == null) {
            return List.of();
        }
        return imageUrls.stream()
                .map(String::trim)
                .peek(this::validateImageUrl)
                .toList();
    }

    private void validateImageUrl(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme() == null
                    ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!(scheme.equals("http") || scheme.equals("https"))
                    || !uri.isAbsolute() || uri.getHost() == null) {
                throw invalidImageUrl();
            }
        } catch (IllegalArgumentException exception) {
            throw invalidImageUrl();
        }
    }

    private BusinessException invalidImageUrl() {
        return new BusinessException(
                "INVALID_IMAGE_URL", "图片地址必须是绝对 HTTP(S) URL");
    }

    private String writeImages(List<String> imageUrls) {
        try {
            return objectMapper.writeValueAsString(imageUrls);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("POST_DATA_INVALID", "无法保存帖子图片数据");
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

    private PostView toView(PostEntity post, Long viewerUserId) {
        if (post.getPublishedAt() == null) {
            throw new BusinessException("POST_DATA_INVALID", "帖子发布时间缺失");
        }
        Boolean liked = null;
        Boolean favorited = null;
        if (viewerUserId != null && interactionMapper != null) {
            liked = interactionMapper.existsLike(viewerUserId, post.getId());
            favorited = interactionMapper.existsFavorite(viewerUserId, post.getId());
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
                liked,
                favorited);
    }

    private PostView withViewerState(PostView view, Long viewerUserId) {
        if (viewerUserId == null || interactionMapper == null) {
            return view;
        }
        return new PostView(
                view.id(),
                view.authorId(),
                view.content(),
                view.imageUrls(),
                view.merchantId(),
                view.publishedAt(),
                view.likeCount(),
                view.favoriteCount(),
                view.commentCount(),
                interactionMapper.existsLike(viewerUserId, view.id()),
                interactionMapper.existsFavorite(viewerUserId, view.id()));
    }

    private void incrementMetric(String name, String... tags) {
        if (metrics != null) {
            metrics.increment(name, tags);
        }
    }

    private long count(Long value) {
        return value == null ? 0 : value;
    }
}
