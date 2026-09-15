package com.foodhub.social.service;

import com.foodhub.common.core.BusinessException;
import com.foodhub.social.dto.CreateCommentRequest;
import com.foodhub.social.entity.CommentEntity;
import com.foodhub.social.mapper.CommentMapper;
import com.foodhub.social.mapper.PostMapper;
import com.foodhub.social.vo.CommentPageView;
import com.foodhub.social.vo.CommentView;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

@Service
public class CommentService {

    private static final String VISIBLE = "VISIBLE";
    private static final String DELETED = "DELETED";
    private static final ZoneId DATABASE_ZONE = ZoneId.of("Asia/Shanghai");

    private final PostMapper postMapper;
    private final CommentMapper commentMapper;
    private final SocialPostCache postCache;
    private final SocialMetrics metrics;

    @Autowired
    public CommentService(PostMapper postMapper,
                          CommentMapper commentMapper,
                          ObjectProvider<SocialPostCache> postCache,
                          SocialMetrics metrics) {
        this.postMapper = postMapper;
        this.commentMapper = commentMapper;
        this.postCache = postCache.getIfAvailable();
        this.metrics = metrics;
    }

    public CommentService(PostMapper postMapper, CommentMapper commentMapper) {
        this.postMapper = postMapper;
        this.commentMapper = commentMapper;
        this.postCache = null;
        this.metrics = null;
    }

    @Transactional
    public CommentView create(Long currentUserId, long postId, CreateCommentRequest request) {
        long authorId = requireUserId(currentUserId);
        requireVisiblePost(postId);
        String content = normalizeContent(request == null ? null : request.content());
        LocalDateTime now = LocalDateTime.now(DATABASE_ZONE);
        CommentEntity comment = new CommentEntity();
        comment.setPostId(postId);
        comment.setAuthorId(authorId);
        comment.setContent(content);
        comment.setStatus(VISIBLE);
        comment.setCreatedAt(now);
        comment.setUpdatedAt(now);
        commentMapper.insertVisible(comment);
        commentMapper.incrementCommentCount(postId);
        evict(postId);
        incrementMetric("social.comment.write", "action", "create");
        return toView(comment);
    }

    public CommentPageView list(long postId, int page, int pageSize) {
        requireVisiblePost(postId);
        validatePagination(page, pageSize);
        long total = commentMapper.countVisibleByPostId(postId);
        if (total == 0) {
            return new CommentPageView(page, pageSize, 0, List.of());
        }
        long offset = (long) (page - 1) * pageSize;
        List<CommentView> items = commentMapper
                .selectVisiblePage(postId, offset, pageSize).stream()
                .map(this::toView)
                .toList();
        return new CommentPageView(page, pageSize, total, items);
    }

    @Transactional
    public void delete(Long currentUserId, long postId, long commentId) {
        long authorId = requireUserId(currentUserId);
        requireVisiblePost(postId);
        CommentEntity comment = postId > 0 && commentId > 0
                ? commentMapper.selectByPostAndId(postId, commentId)
                : null;
        if (comment == null) {
            throw new BusinessException("COMMENT_NOT_FOUND", "评论不存在或已删除");
        }
        if (!Objects.equals(comment.getAuthorId(), authorId)) {
            throw new BusinessException("COMMENT_FORBIDDEN", "只能删除自己发布的评论");
        }
        if (DELETED.equals(comment.getStatus())) {
            return;
        }
        int updated = commentMapper.softDeleteVisible(
                postId, commentId, authorId, LocalDateTime.now(DATABASE_ZONE));
        if (updated == 1) {
            commentMapper.decrementCommentCount(postId);
            evict(postId);
            incrementMetric("social.comment.write", "action", "delete");
        }
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

    private String normalizeContent(String content) {
        String value = content == null ? "" : content.trim();
        if (value.isEmpty() || value.length() > 500) {
            throw new BusinessException("VALIDATION_ERROR", "评论内容必须为 1 到 500 个字符");
        }
        return value;
    }

    private CommentView toView(CommentEntity comment) {
        if (comment.getCreatedAt() == null) {
            throw new BusinessException("COMMENT_DATA_INVALID", "评论创建时间缺失");
        }
        return new CommentView(
                comment.getId(),
                comment.getPostId(),
                comment.getAuthorId(),
                comment.getContent(),
                comment.getCreatedAt().atZone(DATABASE_ZONE).toOffsetDateTime());
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
}
