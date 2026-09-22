CREATE TABLE seckill_activity (
    id BIGINT NOT NULL AUTO_INCREMENT,
    merchant_id BIGINT NOT NULL,
    creator_user_id BIGINT NOT NULL,
    target_type VARCHAR(16) NOT NULL,
    target_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    seckill_price DECIMAL(10, 2) NOT NULL,
    total_stock INT NOT NULL,
    remaining_stock INT NOT NULL,
    start_at DATETIME NOT NULL,
    end_at DATETIME NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_seckill_activity_public (status, start_at, end_at, id),
    INDEX idx_seckill_activity_creator (creator_user_id, created_at, id),
    INDEX idx_seckill_activity_target (target_type, target_id),
    CONSTRAINT chk_seckill_activity_merchant CHECK (merchant_id > 0),
    CONSTRAINT chk_seckill_activity_creator CHECK (creator_user_id > 0),
    CONSTRAINT chk_seckill_activity_target_id CHECK (target_id > 0),
    CONSTRAINT chk_seckill_activity_target_type CHECK (target_type IN ('FOOD_ITEM', 'COUPON')),
    CONSTRAINT chk_seckill_activity_price CHECK (seckill_price >= 0),
    CONSTRAINT chk_seckill_activity_stock CHECK (
        total_stock > 0 AND remaining_stock >= 0 AND remaining_stock <= total_stock
    ),
    CONSTRAINT chk_seckill_activity_time CHECK (end_at > start_at),
    CONSTRAINT chk_seckill_activity_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE seckill_request (
    id BIGINT NOT NULL AUTO_INCREMENT,
    request_id VARCHAR(64) NOT NULL,
    activity_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    order_no VARCHAR(64) NULL,
    failure_code VARCHAR(64) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_seckill_request_request_id (request_id),
    UNIQUE KEY uk_seckill_request_user_activity (user_id, activity_id),
    INDEX idx_seckill_request_user_time (user_id, created_at, id),
    CONSTRAINT fk_seckill_request_activity FOREIGN KEY (activity_id) REFERENCES seckill_activity (id),
    CONSTRAINT chk_seckill_request_user CHECK (user_id > 0),
    CONSTRAINT chk_seckill_request_status CHECK (
        status IN ('STOCK_RESERVED', 'MESSAGE_SENT', 'ORDER_CREATED', 'FAILED')
    )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
