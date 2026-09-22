CREATE TABLE fh_order (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_no VARCHAR(64) NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    activity_id BIGINT NOT NULL,
    target_type VARCHAR(16) NOT NULL,
    target_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(10, 2) NOT NULL,
    total_amount DECIMAL(10, 2) NOT NULL,
    status VARCHAR(24) NOT NULL,
    payment_expires_at DATETIME NOT NULL,
    paid_at DATETIME NULL,
    cancelled_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_fh_order_order_no (order_no),
    UNIQUE KEY uk_fh_order_request_id (request_id),
    UNIQUE KEY uk_fh_order_user_activity (user_id, activity_id),
    INDEX idx_fh_order_user_page (user_id, created_at, id),
    INDEX idx_fh_order_status_expiry (status, payment_expires_at, id),
    CONSTRAINT chk_fh_order_user CHECK (user_id > 0),
    CONSTRAINT chk_fh_order_activity CHECK (activity_id > 0),
    CONSTRAINT chk_fh_order_target CHECK (target_id > 0),
    CONSTRAINT chk_fh_order_target_type CHECK (target_type IN ('FOOD_ITEM', 'COUPON')),
    CONSTRAINT chk_fh_order_quantity CHECK (quantity > 0),
    CONSTRAINT chk_fh_order_amount CHECK (unit_price >= 0 AND total_amount >= 0),
    CONSTRAINT chk_fh_order_status CHECK (
        status IN ('PENDING_PAY', 'PAID', 'CANCELLED', 'COMPLETED')
    )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE order_consumed_message (
    id BIGINT NOT NULL AUTO_INCREMENT,
    message_id VARCHAR(64) NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    consumed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_consumed_message_id (message_id),
    UNIQUE KEY uk_order_consumed_request_id (request_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
