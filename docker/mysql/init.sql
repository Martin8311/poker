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
