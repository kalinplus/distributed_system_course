-- 电商系统数据库初始化脚本
-- 创建数据库
CREATE DATABASE IF NOT EXISTS ecommerce DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE ecommerce;

-- 用户表
CREATE TABLE IF NOT EXISTS t_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希',
    phone VARCHAR(20) COMMENT '手机号',
    email VARCHAR(100) COMMENT '邮箱',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1正常，0禁用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 商品表
CREATE TABLE IF NOT EXISTS t_product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL COMMENT '商品名称',
    description TEXT COMMENT '商品描述',
    price DECIMAL(10, 2) NOT NULL COMMENT '价格',
    stock INT NOT NULL DEFAULT 0 COMMENT '库存数量',
    category VARCHAR(50) COMMENT '分类',
    image_url VARCHAR(500) COMMENT '图片URL',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1上架，0下架',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_category (category),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';

-- 库存表
CREATE TABLE IF NOT EXISTS t_inventory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL COMMENT '商品ID',
    stock INT NOT NULL DEFAULT 0 COMMENT '可用库存',
    reserved_stock INT NOT NULL DEFAULT 0 COMMENT '预留库存',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存表';

-- 订单表
CREATE TABLE IF NOT EXISTS t_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(50) NOT NULL UNIQUE COMMENT '订单号',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    product_id BIGINT NOT NULL COMMENT '商品ID',
    quantity INT NOT NULL DEFAULT 1 COMMENT '数量',
    total_amount DECIMAL(10, 2) NOT NULL COMMENT '总金额',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '订单状态：PENDING/PAID/SHIPPED/COMPLETED/CANCELLED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_id (user_id),
    INDEX idx_order_no (order_no),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- 插入测试数据
INSERT INTO t_user (username, password_hash, phone, email, status) VALUES
('testuser', '$2a$10$YourHashedPasswordHere', '13800138000', 'test@example.com', 1);

INSERT INTO t_product (name, description, price, stock, category, image_url, status) VALUES
('iPhone 15 Pro', 'Apple iPhone 15 Pro 256GB', 8999.00, 100, '手机', 'https://example.com/iphone15.jpg', 1),
('MacBook Pro', 'Apple MacBook Pro 14英寸 M3', 16999.00, 50, '电脑', 'https://example.com/macbook.jpg', 1),
('AirPods Pro', 'Apple AirPods Pro 第二代', 1899.00, 200, '耳机', 'https://example.com/airpods.jpg', 1),
('iPad Air', 'Apple iPad Air 10.9英寸', 4599.00, 80, '平板', 'https://example.com/ipad.jpg', 1),
('Apple Watch', 'Apple Watch Series 9', 2999.00, 120, '手表', 'https://example.com/watch.jpg', 1);

INSERT INTO t_inventory (product_id, stock, reserved_stock) VALUES
(1, 100, 0),
(2, 50, 0),
(3, 200, 0),
(4, 80, 0),
(5, 120, 0);

INSERT INTO t_order (order_no, user_id, product_id, quantity, total_amount, status) VALUES
('ORD202403180001', 1, 1, 1, 8999.00, 'COMPLETED'),
('ORD202403180002', 1, 2, 1, 16999.00, 'PAID');
