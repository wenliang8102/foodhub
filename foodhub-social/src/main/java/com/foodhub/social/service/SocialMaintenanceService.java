package com.foodhub.social.service;

import com.foodhub.common.core.BusinessException;
import com.foodhub.social.entity.PostEntity;
import com.foodhub.social.mapper.CommentMapper;
import com.foodhub.social.mapper.InteractionMapper;
import com.foodhub.social.mapper.PostMapper;
import com.foodhub.social.vo.CounterRepairView;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class SocialMaintenanceService {

    private static final ZoneId DATABASE_ZONE = ZoneId.of("Asia/Shanghai");

    private final PostMapper postMapper;
    private final InteractionMapper interactionMapper;
    private final CommentMapper commentMapper;
    private final SocialPostCache postCache;
    private final SocialMetrics metrics;

    public SocialMaintenanceService(PostMapper postMapper,
                                    InteractionMapper interactionMapper,
                                    CommentMapper commentMapper,
                                    ObjectProvider<SocialPostCache> postCache,
                                    SocialMetrics metrics) {
        this.postMapper = postMapper;
        this.interactionMapper = interactionMapper;
        this.commentMapper = commentMapper;
        this.postCache = postCache.getIfAvailable();
        this.metrics = metrics;
    }

    @Transactional
    public CounterRepairView repairPostCounters(Long operatorId, long postId) {
        requireUserId(operatorId);
        PostEntity post = postId > 0 ? postMapper.selectAnyById(postId) : null;
        if (post == null) {
            throw new BusinessException("POST_NOT_FOUND", "帖子不存在或已删除");
        }
        long likeCount = interactionMapper.countLikesByPostId(postId);
        long favoriteCount = interactionMapper.countFavoritesByPostId(postId);
        long commentCount = commentMapper.countVisibleByPostId(postId);
        postMapper.updateCounters(
                postId, likeCount, favoriteCount, commentCount, LocalDateTime.now(DATABASE_ZONE));
        if (postCache != null) {
            postCache.evictPost(postId);
        }
        metrics.increment("social.counter.repair", "result", "updated");
        return new CounterRepairView(postId, likeCount, favoriteCount, commentCount);
    }

    private void requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException("INVALID_USER_ID", "用户 ID 必须为正整数");
        }
    }
}
