ALTER TABLE post
    ADD COLUMN like_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN favorite_count BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_post_like_count CHECK (like_count >= 0),
    ADD CONSTRAINT chk_post_favorite_count CHECK (favorite_count >= 0);

CREATE TABLE post_like (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_post_like_user_post (user_id, post_id),
    INDEX idx_post_like_post (post_id, id),
    CONSTRAINT chk_post_like_user_id CHECK (user_id > 0),
    CONSTRAINT chk_post_like_post_id CHECK (post_id > 0),
    CONSTRAINT fk_post_like_post FOREIGN KEY (post_id) REFERENCES post (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE post_favorite (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_post_favorite_user_post (user_id, post_id),
    INDEX idx_post_favorite_user_page (user_id, created_at, id),
    INDEX idx_post_favorite_post (post_id, id),
    CONSTRAINT chk_post_favorite_user_id CHECK (user_id > 0),
    CONSTRAINT chk_post_favorite_post_id CHECK (post_id > 0),
    CONSTRAINT fk_post_favorite_post FOREIGN KEY (post_id) REFERENCES post (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
