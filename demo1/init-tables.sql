-- 创建订单数据库

DROP DATABASE IF EXISTS seata_order;
CREATE DATABASE seata_order DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

-- 创建库存数据库
DROP DATABASE IF EXISTS seata_storage;
CREATE DATABASE seata_storage DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;


USE seata_order;

-- AT模式订单表
CREATE TABLE t_order (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  user_id VARCHAR(50) NOT NULL COMMENT '用户ID',
  product_id VARCHAR(50) NOT NULL COMMENT '商品ID',
  count INT NOT NULL COMMENT '购买数量',
  amount DECIMAL(10,2) NOT NULL COMMENT '订单金额',
  status VARCHAR(20) NOT NULL DEFAULT 'INIT' COMMENT '订单状态',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_id (user_id),
  KEY idx_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- TCC模式订单表
CREATE TABLE t_order_tcc (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  user_id VARCHAR(50) NOT NULL COMMENT '用户ID',
  product_id VARCHAR(50) NOT NULL COMMENT '商品ID',
  count INT NOT NULL COMMENT '购买数量',
  amount DECIMAL(10,2) NOT NULL COMMENT '订单金额',
  status VARCHAR(20) NOT NULL DEFAULT 'INIT' COMMENT '订单状态',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_id (user_id),
  KEY idx_product_id (product_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- AT模式回滚日志表
CREATE TABLE undo_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  branch_id BIGINT NOT NULL,
  xid VARCHAR(100) NOT NULL,
  context VARCHAR(128) NOT NULL,
  rollback_info LONGBLOB NOT NULL,
  log_status INT NOT NULL,
  log_created DATETIME NOT NULL,
  log_modified DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY ux_undo_log (xid, branch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

USE seata_storage;

-- AT模式库存表
CREATE TABLE t_storage (
  id BIGINT NOT NULL AUTO_INCREMENT,
  product_id VARCHAR(50) NOT NULL,
  total INT NOT NULL,
  used INT NOT NULL DEFAULT 0,
  residue INT NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- TCC模式库存表
CREATE TABLE t_storage_tcc (
  id BIGINT NOT NULL AUTO_INCREMENT,
  product_id VARCHAR(50) NOT NULL,
  total INT NOT NULL,
  used INT NOT NULL DEFAULT 0,
  frozen INT NOT NULL DEFAULT 0,
  residue INT NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- AT模式回滚日志表
CREATE TABLE undo_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  branch_id BIGINT NOT NULL,
  xid VARCHAR(100) NOT NULL,
  context VARCHAR(128) NOT NULL,
  rollback_info LONGBLOB NOT NULL,
  log_status INT NOT NULL,
  log_created DATETIME NOT NULL,
  log_modified DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY ux_undo_log (xid, branch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 清空订单数据
USE seata_order;
TRUNCATE TABLE t_order;
TRUNCATE TABLE t_order_tcc;
TRUNCATE TABLE undo_log;

-- 清空库存数据
USE seata_storage;
TRUNCATE TABLE t_storage;
TRUNCATE TABLE t_storage_tcc;
TRUNCATE TABLE undo_log;

-- AT模式库存数据
INSERT INTO seata_storage.t_storage (product_id, total, used, residue) VALUES
('P001', 100, 0, 100),
('P003', 5, 0, 5);

-- TCC模式库存数据
INSERT INTO seata_storage.t_storage_tcc (product_id, total, used, frozen, residue) VALUES
('P002', 100, 0, 0, 100),
('P004', 10, 0, 0, 10);

SELECT 'AT模式库存数据:' AS info;
SELECT product_id, total, used, residue FROM t_storage WHERE product_id IN ('P001', 'P003');

SELECT 'TCC模式库存数据:' AS info;
SELECT product_id, total, used, frozen, residue FROM t_storage_tcc WHERE product_id IN ('P002', 'P004');
