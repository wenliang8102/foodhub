CREATE TABLE post (
    id BIGINT NOT NULL AUTO_INCREMENT,
    author_id BIGINT NOT NULL,
    content VARCHAR(2000) NOT NULL,
    image_urls JSON NULL,
    merchant_id BIGINT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'VISIBLE',
    published_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    PRIMARY KEY (id),
    INDEX idx_post_visibility_time (status, published_at, id),
    CONSTRAINT chk_post_author_id CHECK (author_id > 0),
    CONSTRAINT chk_post_merchant_id CHECK (merchant_id IS NULL OR merchant_id > 0),
    CONSTRAINT chk_post_status CHECK (status IN ('VISIBLE', 'DELETED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
