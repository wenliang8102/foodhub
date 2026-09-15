package com.foodhub.social.service;

import com.foodhub.common.core.BusinessException;
import com.foodhub.social.dto.UpdatePostVisibilityRequest;
import com.foodhub.social.entity.ModerationLogEntity;
import com.foodhub.social.entity.PostEntity;
import com.foodhub.social.mapper.ModerationLogMapper;
import com.foodhub.social.mapper.PostMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;

@Service
public class ModerationService {

    private static final String VISIBLE = "VISIBLE";
    private static final String HIDDEN = "HIDDEN";
    private static final String DELETED = "DELETED";
    private static final ZoneId DATABASE_ZONE = ZoneId.of("Asia/Shanghai");

    private final PostMapper postMapper;
    private final ModerationLogMapper moderationLogMapper;
    private final SocialPostCache postCache;
    private final SocialMetrics metrics;

    @Autowired
    public ModerationService(PostMapper postMapper,
                             ModerationLogMapper moderationLogMapper,
                             ObjectProvider<SocialPostCache> postCache,
                             SocialMetrics metrics) {
        this.postMapper = postMapper;
        this.moderationLogMapper = moderationLogMapper;
        this.postCache = postCache.getIfAvailable();
        this.metrics = metrics;
    }

    public ModerationService(PostMapper postMapper, ModerationLogMapper moderationLogMapper) {
        this.postMapper = postMapper;
        this.moderationLogMapper = moderationLogMapper;
        this.postCache = null;
        this.metrics = null;
    }

    @Transactional
    public void updateVisibility(Long operatorId, long postId, UpdatePostVisibilityRequest request) {
        long adminId = requireUserId(operatorId);
        String toStatus = normalizeStatus(request == null ? null : request.status());
        String reason = normalizeReason(request == null ? null : request.reason());
        PostEntity post = postId > 0 ? postMapper.selectAnyById(postId) : null;
        if (post == null || DELETED.equals(post.getStatus())) {
            throw new BusinessException("POST_NOT_FOUND", "帖子不存在或已删除");
        }
        if (post.getStatus().equals(toStatus)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(DATABASE_ZONE);
        int updated = postMapper.updateVisibility(
                postId, post.getStatus(), toStatus, adminId, reason, now);
        if (updated != 1) {
            throw new BusinessException("POST_NOT_FOUND", "帖子不存在或已删除");
        }
        ModerationLogEntity log = new ModerationLogEntity();
        log.setPostId(postId);
        log.setOperatorId(adminId);
        log.setFromStatus(post.getStatus());
        log.setToStatus(toStatus);
        log.setReason(reason);
        log.setCreatedAt(now);
        moderationLogMapper.insertLog(log);
        evict(postId);
        if (metrics != null) {
            metrics.increment("social.moderation.visibility", "to", toStatus);
        }
    }

    private long requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException("INVALID_USER_ID", "用户 ID 必须为正整数");
        }
        return userId;
    }

    private String normalizeStatus(String status) {
        String value = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!VISIBLE.equals(value) && !HIDDEN.equals(value)) {
            throw new BusinessException("VALIDATION_ERROR", "目标状态只能是 VISIBLE 或 HIDDEN");
        }
        return value;
    }

    private String normalizeReason(String reason) {
        String value = reason == null ? "" : reason.trim();
        if (value.isEmpty() || value.length() > 500) {
            throw new BusinessException("VALIDATION_ERROR", "治理原因必须为 1 到 500 个字符");
        }
        return value;
    }

    private void evict(long postId) {
        if (postCache != null) {
            postCache.evictPost(postId);
        }
    }
}
