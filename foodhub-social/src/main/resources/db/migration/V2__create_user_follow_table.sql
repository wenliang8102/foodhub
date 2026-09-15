CREATE TABLE user_follow (
    id BIGINT NOT NULL AUTO_INCREMENT,
    follower_id BIGINT NOT NULL,
    following_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_follow_pair (follower_id, following_id),
    INDEX idx_user_following_page (follower_id, created_at, id),
    INDEX idx_user_followers_page (following_id, created_at, id),
    CONSTRAINT chk_user_follow_follower_id CHECK (follower_id > 0),
    CONSTRAINT chk_user_follow_following_id CHECK (following_id > 0),
    CONSTRAINT chk_user_follow_not_self CHECK (follower_id <> following_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
