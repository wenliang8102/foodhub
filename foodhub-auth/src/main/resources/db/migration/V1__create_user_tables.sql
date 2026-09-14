CREATE TABLE fh_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(32) NOT NULL,
    phone VARCHAR(32) NULL,
    email VARCHAR(254) NULL,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'USER',
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_fh_user_username (username),
    UNIQUE KEY uk_fh_user_phone (phone),
    UNIQUE KEY uk_fh_user_email (email),
    CONSTRAINT chk_fh_user_status CHECK (status IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
