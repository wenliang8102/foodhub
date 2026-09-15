package com.foodhub.social.mapper;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ModerationAndFeedMapperContractTest {

    @Test
    void moderationUpdateIsStateTransitionWithExpectedStatus() throws Exception {
        Update update = PostMapper.class
                .getMethod("updateVisibility", long.class, String.class, String.class,
                        long.class, String.class, LocalDateTime.class)
                .getAnnotation(Update.class);
        String sql = String.join(" ", update.value());

        assertTrue(sql.contains("status = #{toStatus}"));
        assertTrue(sql.contains("status = #{fromStatus}"));
        assertTrue(sql.contains("hidden_by = CASE WHEN #{toStatus} = 'HIDDEN'"));
    }

    @Test
    void feedOnlyReturnsFollowedVisiblePostsInStableKeysetOrder() throws Exception {
        Select firstPage = PostMapper.class
                .getMethod("selectFeedFirstPage", long.class, int.class)
                .getAnnotation(Select.class);
        String firstSql = String.join(" ", firstPage.value());
        assertTrue(firstSql.contains("JOIN user_follow f ON f.following_id = p.author_id"));
        assertTrue(firstSql.contains("f.follower_id = #{viewerUserId}"));
        assertTrue(firstSql.contains("p.status = 'VISIBLE'"));
        assertTrue(firstSql.contains("ORDER BY p.published_at DESC, p.id DESC"));

        Select afterCursor = PostMapper.class
                .getMethod("selectFeedAfterCursor", long.class, LocalDateTime.class, long.class, int.class)
                .getAnnotation(Select.class);
        String cursorSql = String.join(" ", afterCursor.value());
        assertTrue(cursorSql.contains("p.published_at < #{beforePublishedAt}"));
        assertTrue(cursorSql.contains("p.id < #{beforePostId}"));
    }
}
