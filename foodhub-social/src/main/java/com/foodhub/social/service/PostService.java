package com.foodhub.social.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodhub.common.core.BusinessException;
import com.foodhub.social.dto.CreatePostRequest;
import com.foodhub.social.entity.PostEntity;
import com.foodhub.social.mapper.PostMapper;
import com.foodhub.social.vo.PostPageView;
import com.foodhub.social.vo.PostView;
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
    private final ObjectMapper objectMapper;

    public PostService(PostMapper postMapper, ObjectMapper objectMapper) {
        this.postMapper = postMapper;
        this.objectMapper = objectMapper;
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
        return toView(post);
    }

    public PostPageView list(int page, int pageSize) {
        validatePagination(page, pageSize);
        long total = postMapper.countVisible();
        if (total == 0) {
            return new PostPageView(page, pageSize, 0, List.of());
        }
        long offset = (long) (page - 1) * pageSize;
        List<PostView> items = postMapper.selectVisiblePage(offset, pageSize).stream()
                .map(this::toView)
                .toList();
        return new PostPageView(page, pageSize, total, items);
    }

    public PostView detail(long postId) {
        PostEntity post = postId > 0 ? postMapper.selectVisibleById(postId) : null;
        if (post == null) {
            throw new BusinessException("POST_NOT_FOUND", "帖子不存在或已删除");
        }
        return toView(post);
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
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException("INVALID_USER_ID", "X-User-Id 必须为正整数");
        }
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

    private PostView toView(PostEntity post) {
        if (post.getPublishedAt() == null) {
            throw new BusinessException("POST_DATA_INVALID", "帖子发布时间缺失");
        }
        return new PostView(
                post.getId(),
                post.getAuthorId(),
                post.getContent(),
                readImages(post.getImageUrls()),
                post.getMerchantId(),
                post.getPublishedAt().atZone(DATABASE_ZONE).toOffsetDateTime());
    }
}
