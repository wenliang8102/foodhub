CREATE TABLE coupon (
    id BIGINT NOT NULL AUTO_INCREMENT,
    merchant_id BIGINT NOT NULL,
    creator_user_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    face_value DECIMAL(10, 2) NOT NULL,
    min_spend DECIMAL(10, 2) NOT NULL DEFAULT 0,
    total_stock INT NOT NULL,
    remaining_stock INT NOT NULL,
    valid_from DATETIME NOT NULL,
    valid_until DATETIME NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_coupon_public (status, valid_from, valid_until, remaining_stock),
    INDEX idx_coupon_merchant (merchant_id, created_at, id),
    INDEX idx_coupon_creator (creator_user_id, created_at, id),
    CONSTRAINT chk_coupon_merchant_id CHECK (merchant_id > 0),
    CONSTRAINT chk_coupon_creator_user_id CHECK (creator_user_id > 0),
    CONSTRAINT chk_coupon_face_value CHECK (face_value > 0),
    CONSTRAINT chk_coupon_min_spend CHECK (min_spend >= face_value),
    CONSTRAINT chk_coupon_stock CHECK (
        total_stock > 0 AND remaining_stock >= 0 AND remaining_stock <= total_stock
    ),
    CONSTRAINT chk_coupon_validity CHECK (valid_until > valid_from),
    CONSTRAINT chk_coupon_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE user_coupon (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    coupon_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'UNUSED',
    claimed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    used_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_coupon_user_coupon (user_id, coupon_id),
    INDEX idx_user_coupon_user_page (user_id, claimed_at, id),
    INDEX idx_user_coupon_coupon (coupon_id, id),
    CONSTRAINT fk_user_coupon_coupon FOREIGN KEY (coupon_id) REFERENCES coupon (id),
    CONSTRAINT chk_user_coupon_user_id CHECK (user_id > 0),
    CONSTRAINT chk_user_coupon_status CHECK (status IN ('UNUSED', 'USED', 'EXPIRED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
