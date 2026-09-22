ALTER TABLE seckill_request
    DROP CHECK chk_seckill_request_status,
    ADD CONSTRAINT chk_seckill_request_status CHECK (
        status IN ('STOCK_RESERVED', 'MESSAGE_SENT', 'ORDER_CREATED', 'CANCELLED', 'FAILED')
    );

CREATE TABLE seckill_cancellation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    message_id VARCHAR(64) NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    activity_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    processed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_seckill_cancellation_message_id (message_id),
    UNIQUE KEY uk_seckill_cancellation_order_no (order_no),
    UNIQUE KEY uk_seckill_cancellation_request_id (request_id),
    CONSTRAINT fk_seckill_cancellation_request FOREIGN KEY (request_id)
        REFERENCES seckill_request (request_id),
    CONSTRAINT chk_seckill_cancellation_quantity CHECK (quantity > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
