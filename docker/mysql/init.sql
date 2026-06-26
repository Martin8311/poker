-- MySQL 容器首次启动时自动执行（建库 + 建表）
CREATE DATABASE IF NOT EXISTS poker DEFAULT CHARACTER SET utf8mb4;
USE poker;

CREATE TABLE IF NOT EXISTS `user` (
  `id`          INT AUTO_INCREMENT PRIMARY KEY,
  `username`    VARCHAR(45)  NOT NULL UNIQUE,
  `password`    VARCHAR(200) NOT NULL,
  `nickname`    VARCHAR(45)  NOT NULL,
  `email`       VARCHAR(45)  DEFAULT NULL,
  `create_time` DATETIME     DEFAULT NULL,
  `total_games` INT          DEFAULT 0,
  `win_games`   INT          DEFAULT 0,
  `score`       INT          DEFAULT 0,
  `iconUrl`     VARCHAR(255) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 好友申请 / 好友关系：PENDING=申请中，ACCEPTED=好友（双向匹配，拒绝则删除记录）
CREATE TABLE IF NOT EXISTS `friend_request` (
  `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
  `requester`   VARCHAR(45)  NOT NULL,
  `addressee`   VARCHAR(45)  NOT NULL,
  `status`      VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
  `create_time` DATETIME     NOT NULL,
  `update_time` DATETIME     DEFAULT NULL,
  UNIQUE KEY `uk_pair` (`requester`, `addressee`),
  KEY `idx_addressee_status` (`addressee`, `status`),
  KEY `idx_requester_status` (`requester`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
