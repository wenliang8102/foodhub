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
}
