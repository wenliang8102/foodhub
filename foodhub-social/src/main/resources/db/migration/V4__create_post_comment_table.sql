ALTER TABLE post
    ADD COLUMN comment_count BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_post_comment_count CHECK (comment_count >= 0);

CREATE TABLE post_comment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    content VARCHAR(500) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'VISIBLE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    PRIMARY KEY (id),
    INDEX idx_post_comment_page (post_id, status, created_at, id),
    INDEX idx_post_comment_author (author_id, id),
    CONSTRAINT chk_post_comment_post_id CHECK (post_id > 0),
    CONSTRAINT chk_post_comment_author_id CHECK (author_id > 0),
    CONSTRAINT chk_post_comment_content CHECK (CHAR_LENGTH(TRIM(content)) BETWEEN 1 AND 500),
    CONSTRAINT chk_post_comment_status CHECK (status IN ('VISIBLE', 'DELETED')),
    CONSTRAINT fk_post_comment_post FOREIGN KEY (post_id) REFERENCES post (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
