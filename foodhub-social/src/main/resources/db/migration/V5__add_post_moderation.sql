ALTER TABLE post
    DROP CHECK chk_post_status;

ALTER TABLE post
    ADD COLUMN hidden_at DATETIME NULL,
    ADD COLUMN hidden_by BIGINT NULL,
    ADD COLUMN hidden_reason VARCHAR(500) NULL,
    ADD CONSTRAINT chk_post_status CHECK (status IN ('VISIBLE', 'HIDDEN', 'DELETED')),
    ADD CONSTRAINT chk_post_hidden_by CHECK (hidden_by IS NULL OR hidden_by > 0),
    ADD CONSTRAINT chk_post_hidden_reason CHECK (
        hidden_reason IS NULL OR CHAR_LENGTH(TRIM(hidden_reason)) BETWEEN 1 AND 500
    );

CREATE TABLE post_moderation_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    operator_id BIGINT NOT NULL,
    from_status VARCHAR(16) NOT NULL,
    to_status VARCHAR(16) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_post_moderation_log_post (post_id, created_at, id),
    CONSTRAINT chk_post_moderation_operator_id CHECK (operator_id > 0),
    CONSTRAINT chk_post_moderation_status CHECK (
        from_status IN ('VISIBLE', 'HIDDEN', 'DELETED')
        AND to_status IN ('VISIBLE', 'HIDDEN', 'DELETED')
    ),
    CONSTRAINT chk_post_moderation_reason CHECK (CHAR_LENGTH(TRIM(reason)) BETWEEN 1 AND 500),
    CONSTRAINT fk_post_moderation_log_post FOREIGN KEY (post_id) REFERENCES post (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
