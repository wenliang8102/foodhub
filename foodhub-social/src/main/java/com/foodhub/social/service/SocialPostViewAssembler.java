package com.foodhub.social.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodhub.common.core.BusinessException;
import com.foodhub.social.entity.PostEntity;
import com.foodhub.social.mapper.InteractionMapper;
import com.foodhub.social.vo.PostView;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.List;

@Component
public class SocialPostViewAssembler {

    private static final ZoneId DATABASE_ZONE = ZoneId.of("Asia/Shanghai");
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    private final ObjectMapper objectMapper;
    private final InteractionMapper interactionMapper;

    public SocialPostViewAssembler(ObjectMapper objectMapper, InteractionMapper interactionMapper) {
        this.objectMapper = objectMapper;
        this.interactionMapper = interactionMapper;
    }

    public PostView toView(PostEntity post, Long viewerUserId) {
        if (post.getPublishedAt() == null) {
            throw new BusinessException("POST_DATA_INVALID", "帖子发布时间缺失");
        }
        Boolean liked = null;
        Boolean favorited = null;
        if (viewerUserId != null) {
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

    public PostView withViewerState(PostView view, Long viewerUserId) {
        if (viewerUserId == null) {
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

    private long count(Long value) {
        return value == null ? 0 : value;
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
