-- GreenDeer 数据库初始化（可选）。
-- 应用默认 jdbc:mysql://127.0.0.1:3306/greendeer?createDatabaseIfNotExist=true
-- 若账号无建库权限，可手工执行下面脚本：
CREATE DATABASE IF NOT EXISTS greendeer
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
USE greendeer;

-- 表结构由 JPA ddl-auto=update 自动创建（live_session / chat_message）。
-- 如希望 JPA 不建表，可在此预置并改为 ddl-auto=validate。

-- 最小专用账号示例（请务必替换密码）：
-- CREATE USER 'greendeer'@'%' IDENTIFIED BY 'change-me';
-- GRANT ALL PRIVILEGES ON greendeer.* TO 'greendeer'@'%';
-- FLUSH PRIVILEGES;
