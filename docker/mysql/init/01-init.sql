-- 创建应用专用用户
-- MySQL 中 '%' 不匹配 'localhost'，需要单独建一个 @'localhost' 条目
CREATE USER IF NOT EXISTS 'ecommerce'@'%'         IDENTIFIED WITH mysql_native_password BY 'ecommerce123';
CREATE USER IF NOT EXISTS 'ecommerce'@'localhost' IDENTIFIED WITH mysql_native_password BY 'ecommerce123';
CREATE USER IF NOT EXISTS 'ecommerce'@'127.0.0.1' IDENTIFIED WITH mysql_native_password BY 'ecommerce123';

GRANT ALL PRIVILEGES ON ecommerce.* TO 'ecommerce'@'%';
GRANT ALL PRIVILEGES ON ecommerce.* TO 'ecommerce'@'localhost';
GRANT ALL PRIVILEGES ON ecommerce.* TO 'ecommerce'@'127.0.0.1';
FLUSH PRIVILEGES;

-- 建表
USE ecommerce;

CREATE TABLE IF NOT EXISTS t_user (
    id          BIGINT       PRIMARY KEY AUTO_INCREMENT,
    username    VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    phone       VARCHAR(20)  UNIQUE,
    email       VARCHAR(100),
    status      TINYINT      DEFAULT 1,
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
