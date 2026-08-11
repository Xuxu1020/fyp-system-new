-- CarMart Database Initialization Script
-- Run this once after creating the fyp_auth database

USE fyp_auth;

-- Users table (already exists, kept for reference)
CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    role ENUM('admin', 'user') DEFAULT 'user',
    failed_attempts INT DEFAULT 0,
    is_locked BOOLEAN DEFAULT FALSE,
    locked_until TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Login logs table
CREATE TABLE IF NOT EXISTS login_logs (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(100),
    ip_address VARCHAR(50),
    success BOOLEAN,
    defence_triggered VARCHAR(100),
    attempted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Rate limit table
CREATE TABLE IF NOT EXISTS rate_limit (
    ip_address VARCHAR(50) PRIMARY KEY,
    attempt_count INT DEFAULT 0,
    window_start TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Security config table
CREATE TABLE IF NOT EXISTS security_config (
    config_key VARCHAR(100) PRIMARY KEY,
    config_value VARCHAR(255) NOT NULL
);

-- Default security config
INSERT IGNORE INTO security_config (config_key, config_value) VALUES
    ('rate_limiting_enabled', 'true'),
    ('account_lockout_enabled', 'true'),
    ('captcha_enabled', 'true'),
    ('rate_limit_per_minute', '10'),
    ('max_attempts', '5'),
    ('lockout_duration_minutes', '15'),
    ('captcha_trigger_attempts', '3');

-- Car listings table (NEW)
CREATE TABLE IF NOT EXISTS car_listings (
    id INT AUTO_INCREMENT PRIMARY KEY,
    owner_username VARCHAR(100) NOT NULL,
    make VARCHAR(100) NOT NULL,
    model VARCHAR(100) NOT NULL,
    year INT NOT NULL,
    price DECIMAL(12,2) NOT NULL,
    mileage INT DEFAULT 0,
    color VARCHAR(50),
    fuel_type VARCHAR(50),
    transmission VARCHAR(50),
    description TEXT,
    image_filename VARCHAR(255),
    status ENUM('active','sold','draft') DEFAULT 'active',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (owner_username) REFERENCES users(username) ON DELETE CASCADE
);
