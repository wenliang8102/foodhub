CREATE TABLE fh_category (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(80) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_fh_category_name (name),
    CONSTRAINT chk_fh_category_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE fh_merchant (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_user_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    name VARCHAR(80) NOT NULL,
    address VARCHAR(255) NOT NULL,
    longitude DECIMAL(10, 6) NOT NULL,
    latitude DECIMAL(10, 6) NOT NULL,
    rating DECIMAL(3, 2) NOT NULL DEFAULT 5.00,
    sales_count BIGINT NOT NULL DEFAULT 0,
    business_status VARCHAR(16) NOT NULL DEFAULT 'CLOSED',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_fh_merchant_category_status (category_id, status),
    KEY idx_fh_merchant_owner_status (owner_user_id, status),
    KEY idx_fh_merchant_rating (rating),
    KEY idx_fh_merchant_sales_count (sales_count),
    CONSTRAINT chk_fh_merchant_business_status CHECK (business_status IN ('OPEN', 'CLOSED')),
    CONSTRAINT chk_fh_merchant_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE fh_food_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    merchant_id BIGINT NOT NULL,
    name VARCHAR(80) NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    image_url VARCHAR(512) NULL,
    sales_count BIGINT NOT NULL DEFAULT 0,
    on_sale BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_fh_food_item_merchant_status (merchant_id, status, on_sale),
    KEY idx_fh_food_item_sales_count (sales_count),
    KEY idx_fh_food_item_price (price),
    CONSTRAINT chk_fh_food_item_price CHECK (price >= 0),
    CONSTRAINT chk_fh_food_item_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO fh_category (id, name, sort_order, status) VALUES
    (1, 'Sichuan Hunan', 10, 'ACTIVE'),
    (2, 'Cantonese Tea', 20, 'ACTIVE'),
    (3, 'Coffee Dessert', 30, 'ACTIVE');

INSERT INTO fh_merchant (
    id, owner_user_id, category_id, name, address, longitude, latitude,
    rating, sales_count, business_status, status
) VALUES
    (101, 7, 1, 'Pepper Alley', 'River Road 18', 116.397500, 39.908700, 4.80, 1286, 'OPEN', 'ACTIVE'),
    (102, 8, 2, 'South Tea House', 'South Lake Street 88', 116.410200, 39.901100, 4.60, 932, 'OPEN', 'ACTIVE'),
    (103, 7, 3, 'Riverside Coffee', 'Star Bridge Lane 6', 116.385900, 39.917200, 4.70, 521, 'CLOSED', 'ACTIVE');

INSERT INTO fh_food_item (
    id, merchant_id, name, price, image_url, sales_count, on_sale, status
) VALUES
    (1001, 101, 'Signature Spicy Chicken', 42.00, NULL, 420, TRUE, 'ACTIVE'),
    (1002, 101, 'Clay Pot Crispy Pork', 36.00, NULL, 315, TRUE, 'ACTIVE'),
    (1003, 102, 'Shrimp Dumpling', 29.00, NULL, 508, TRUE, 'ACTIVE'),
    (1004, 103, 'Caramel Latte', 25.00, NULL, 198, TRUE, 'ACTIVE');
