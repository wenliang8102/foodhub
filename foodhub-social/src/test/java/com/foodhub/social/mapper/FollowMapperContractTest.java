package com.foodhub.social.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FollowMapperContractTest {

    @Test
    void insertIsIdempotentAndDeleteTargetsOneRelationship() throws Exception {
        assertTrue(BaseMapper.class.isAssignableFrom(FollowMapper.class));

        Insert insert = FollowMapper.class
                .getMethod("insertIfAbsent", long.class, long.class, LocalDateTime.class)
                .getAnnotation(Insert.class);
        String insertSql = String.join(" ", insert.value());
        assertTrue(insertSql.contains("INSERT INTO user_follow"));
        assertTrue(insertSql.contains("ON DUPLICATE KEY UPDATE id = id"));

        Delete delete = FollowMapper.class
                .getMethod("deleteRelationship", long.class, long.class)
                .getAnnotation(Delete.class);
        String deleteSql = String.join(" ", delete.value());
        assertTrue(deleteSql.contains("follower_id = #{followerId}"));
        assertTrue(deleteSql.contains("following_id = #{followingId}"));
    }

    @Test
    void followingUsesStableNewestFirstPagination() throws Exception {
        Select following = FollowMapper.class
                .getMethod("selectFollowingPage", long.class, long.class, int.class)
                .getAnnotation(Select.class);
        String followingSql = String.join(" ", following.value());
        assertTrue(followingSql.contains("WHERE follower_id = #{followerId}"));
        assertTrue(followingSql.contains("ORDER BY created_at DESC, id DESC"));
        assertTrue(followingSql.contains("LIMIT #{limit} OFFSET #{offset}"));
    }

    @Test
    void followersUsesStableNewestFirstPagination() throws Exception {
        Select followers = FollowMapper.class
                .getMethod("selectFollowersPage", long.class, long.class, int.class)
                .getAnnotation(Select.class);
        String followersSql = String.join(" ", followers.value());
        assertTrue(followersSql.contains("WHERE following_id = #{followingId}"));
        assertTrue(followersSql.contains("ORDER BY created_at DESC, id DESC"));
        assertTrue(followersSql.contains("LIMIT #{limit} OFFSET #{offset}"));
    }
}
