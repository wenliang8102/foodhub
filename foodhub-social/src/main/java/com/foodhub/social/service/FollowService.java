package com.foodhub.social.service;

import com.foodhub.common.core.BusinessException;
import com.foodhub.social.entity.FollowEntity;
import com.foodhub.social.mapper.FollowMapper;
import com.foodhub.social.vo.FollowPageView;
import com.foodhub.social.vo.FollowView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class FollowService {

    private static final ZoneId DATABASE_ZONE = ZoneId.of("Asia/Shanghai");

    private final FollowMapper followMapper;

    public FollowService(FollowMapper followMapper) {
        this.followMapper = followMapper;
    }

    @Transactional
    public void follow(Long currentUserId, long followingId) {
        long followerId = requireUserId(currentUserId);
        validateTargetUserId(followingId);
        if (followerId == followingId) {
            throw new BusinessException("FOLLOW_SELF_NOT_ALLOWED", "不能关注自己");
        }
        followMapper.insertIfAbsent(
                followerId,
                followingId,
                LocalDateTime.now(DATABASE_ZONE));
    }

    @Transactional
    public void unfollow(Long currentUserId, long followingId) {
        long followerId = requireUserId(currentUserId);
        validateTargetUserId(followingId);
        followMapper.deleteRelationship(followerId, followingId);
    }

    public FollowPageView following(Long currentUserId, int page, int pageSize) {
        long followerId = requireUserId(currentUserId);
        validatePagination(page, pageSize);
        long total = followMapper.countFollowing(followerId);
        if (total == 0) {
            return new FollowPageView(page, pageSize, 0, List.of());
        }
        long offset = (long) (page - 1) * pageSize;
        List<FollowView> items = followMapper
                .selectFollowingPage(followerId, offset, pageSize).stream()
                .map(follow -> toView(follow.getFollowingId(), follow))
                .toList();
        return new FollowPageView(page, pageSize, total, items);
    }

    public FollowPageView followers(Long currentUserId, int page, int pageSize) {
        long followingId = requireUserId(currentUserId);
        validatePagination(page, pageSize);
        long total = followMapper.countFollowers(followingId);
        if (total == 0) {
            return new FollowPageView(page, pageSize, 0, List.of());
        }
        long offset = (long) (page - 1) * pageSize;
        List<FollowView> items = followMapper
                .selectFollowersPage(followingId, offset, pageSize).stream()
                .map(follow -> toView(follow.getFollowerId(), follow))
                .toList();
        return new FollowPageView(page, pageSize, total, items);
    }

    private long requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException("INVALID_USER_ID", "用户 ID 必须为正整数");
        }
        return userId;
    }

    private void validateTargetUserId(long userId) {
        if (userId <= 0) {
            throw new BusinessException("INVALID_USER_ID", "用户 ID 必须为正整数");
        }
    }

    private void validatePagination(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(
                    "VALIDATION_ERROR",
                    "page 必须大于等于 1，pageSize 必须在 1 到 100 之间");
        }
    }

    private FollowView toView(Long relatedUserId, FollowEntity follow) {
        return new FollowView(
                relatedUserId,
                follow.getCreatedAt().atZone(DATABASE_ZONE).toOffsetDateTime());
    }
}
