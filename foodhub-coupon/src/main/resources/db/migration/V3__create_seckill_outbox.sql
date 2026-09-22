CREATE TABLE seckill_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT,
    message_id VARCHAR(64) NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    next_retry_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_seckill_outbox_message_id (message_id),
    UNIQUE KEY uk_seckill_outbox_request_id (request_id),
    INDEX idx_seckill_outbox_retry (status, next_retry_at, id),
    CONSTRAINT chk_seckill_outbox_status CHECK (status IN ('PENDING', 'SENT')),
    CONSTRAINT chk_seckill_outbox_attempts CHECK (attempts >= 0),
    CONSTRAINT fk_seckill_outbox_request FOREIGN KEY (request_id)
        REFERENCES seckill_request (request_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
