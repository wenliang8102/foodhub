package com.foodhub.social.migration;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialMigrationContractTest {

    @Test
    void initialMigrationDefinesOwnedPostTableAndVisibilityIndex() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(
                "/db/migration/V1__create_social_tables.sql")) {
            assertNotNull(stream, "Social 初始迁移文件必须存在");
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(sql.contains("CREATE TABLE post"));
            assertTrue(sql.contains("author_id BIGINT NOT NULL"));
            assertTrue(sql.contains("image_urls JSON NULL"));
            assertTrue(sql.contains("merchant_id BIGINT NULL"));
            assertTrue(sql.contains("status VARCHAR(16) NOT NULL"));
            assertTrue(sql.contains("INDEX idx_post_visibility_time (status, published_at, id)"));
        }
    }

    @Test
    void followMigrationDefinesConstraintsAndPaginationIndexes() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(
                "/db/migration/V2__create_user_follow_table.sql")) {
            assertNotNull(stream, "关注关系迁移文件必须存在");
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(sql.contains("CREATE TABLE user_follow"));
            assertTrue(sql.contains("follower_id BIGINT NOT NULL"));
            assertTrue(sql.contains("following_id BIGINT NOT NULL"));
            assertTrue(sql.contains(
                    "UNIQUE KEY uk_user_follow_pair (follower_id, following_id)"));
            assertTrue(sql.contains(
                    "INDEX idx_user_following_page (follower_id, created_at, id)"));
            assertTrue(sql.contains(
                    "INDEX idx_user_followers_page (following_id, created_at, id)"));
            assertTrue(sql.contains("CHECK (follower_id <> following_id)"));
        }
    }

    @Test
    void interactionMigrationDefinesFactsCountersAndIndexes() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(
                "/db/migration/V3__create_post_interaction_tables.sql")) {
            assertNotNull(stream, "互动迁移文件必须存在");
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(sql.contains("ADD COLUMN like_count BIGINT NOT NULL DEFAULT 0"));
            assertTrue(sql.contains("ADD COLUMN favorite_count BIGINT NOT NULL DEFAULT 0"));
            assertTrue(sql.contains("CREATE TABLE post_like"));
            assertTrue(sql.contains("UNIQUE KEY uk_post_like_user_post (user_id, post_id)"));
            assertTrue(sql.contains("CREATE TABLE post_favorite"));
            assertTrue(sql.contains("UNIQUE KEY uk_post_favorite_user_post (user_id, post_id)"));
            assertTrue(sql.contains(
                    "INDEX idx_post_favorite_user_page (user_id, created_at, id)"));
            assertTrue(sql.contains("CHECK (like_count >= 0)"));
            assertTrue(sql.contains("CHECK (favorite_count >= 0)"));
        }
    }
}
