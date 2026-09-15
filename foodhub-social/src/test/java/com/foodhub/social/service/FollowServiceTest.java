package com.foodhub.social.service;

import com.foodhub.common.core.BusinessException;
import com.foodhub.social.entity.FollowEntity;
import com.foodhub.social.mapper.FollowMapper;
import com.foodhub.social.vo.FollowPageView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

    @Mock
    private FollowMapper followMapper;

    private FollowService followService;

    @BeforeEach
    void setUp() {
        followService = new FollowService(followMapper);
    }

    @Test
    void followCreatesRelationship() {
        when(followMapper.insertIfAbsent(eq(7L), eq(9L), any(LocalDateTime.class)))
                .thenReturn(1);

        followService.follow(7L, 9L);

        verify(followMapper).insertIfAbsent(eq(7L), eq(9L), any(LocalDateTime.class));
    }

    @Test
    void duplicateFollowIsStillSuccess() {
        when(followMapper.insertIfAbsent(eq(7L), eq(9L), any(LocalDateTime.class)))
                .thenReturn(0);

        followService.follow(7L, 9L);

        verify(followMapper).insertIfAbsent(eq(7L), eq(9L), any(LocalDateTime.class));
    }

    @Test
    void followRejectsSelf() {
        BusinessException self = assertThrows(BusinessException.class,
                () -> followService.follow(7L, 7L));
        assertEquals("FOLLOW_SELF_NOT_ALLOWED", self.getCode());

        verify(followMapper, never()).insertIfAbsent(anyLong(), anyLong(), any());
    }

    @Test
    void followRejectsInvalidTarget() {
        BusinessException invalid = assertThrows(BusinessException.class,
                () -> followService.follow(7L, 0L));
        assertEquals("INVALID_USER_ID", invalid.getCode());

        verify(followMapper, never()).insertIfAbsent(anyLong(), anyLong(), any());
    }

    @Test
    void unfollowIsIdempotent() {
        when(followMapper.deleteRelationship(7L, 9L)).thenReturn(0);

        followService.unfollow(7L, 9L);

        verify(followMapper).deleteRelationship(7L, 9L);
    }

    @Test
    void followingReturnsTargetUsersWithOffsetTime() {
        when(followMapper.countFollowing(7L)).thenReturn(1L);
        when(followMapper.selectFollowingPage(7L, 20L, 20))
                .thenReturn(List.of(follow(1L, 7L, 9L)));

        FollowPageView result = followService.following(7L, 2, 20);

        assertEquals(1L, result.total());
        assertEquals(9L, result.items().getFirst().userId());
        assertEquals("+08:00", result.items().getFirst()
                .followedAt().getOffset().toString());
    }

    @Test
    void followersReturnsSourceUsers() {
        when(followMapper.countFollowers(9L)).thenReturn(1L);
        when(followMapper.selectFollowersPage(9L, 0L, 20))
                .thenReturn(List.of(follow(1L, 7L, 9L)));

        FollowPageView result = followService.followers(9L, 1, 20);

        assertEquals(7L, result.items().getFirst().userId());
    }

    @Test
    void emptyListSkipsPageQuery() {
        when(followMapper.countFollowing(7L)).thenReturn(0L);

        FollowPageView result = followService.following(7L, 1, 20);

        assertEquals(List.of(), result.items());
        verify(followMapper, never()).selectFollowingPage(anyLong(), anyLong(), anyInt());
    }

    @Test
    void listRejectsInvalidPagination() {
        BusinessException pagination = assertThrows(BusinessException.class,
                () -> followService.followers(7L, 0, 101));
        assertEquals("VALIDATION_ERROR", pagination.getCode());
    }

    @Test
    void listRejectsInvalidIdentity() {
        BusinessException identity = assertThrows(BusinessException.class,
                () -> followService.following(null, 1, 20));
        assertEquals("INVALID_USER_ID", identity.getCode());
    }

    private FollowEntity follow(long id, long followerId, long followingId) {
        FollowEntity follow = new FollowEntity();
        follow.setId(id);
        follow.setFollowerId(followerId);
        follow.setFollowingId(followingId);
        follow.setCreatedAt(LocalDateTime.of(2026, 9, 15, 12, 0));
        return follow;
    }
}
