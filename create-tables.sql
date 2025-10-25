USE seata_storage;

-- AT模式库存表
CREATE TABLE IF NOT EXISTS t_storage (
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
CREATE TABLE IF NOT EXISTS t_storage_tcc (
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
CREATE TABLE IF NOT EXISTS undo_log (
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

-- 创建订单Saga表
USE seata_order;

CREATE TABLE IF NOT EXISTS `t_order_saga` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  `user_id` varchar(50) NOT NULL COMMENT '用户ID',
  `product_id` varchar(50) NOT NULL COMMENT '商品ID',
  `count` int(11) NOT NULL COMMENT '购买数量',
  `amount` decimal(10,2) NOT NULL COMMENT '订单金额',
  `status` varchar(20) NOT NULL DEFAULT 'INIT' COMMENT '订单状态：INIT-初始化，PROCESSING-处理中，SUCCESS-成功，FAIL-失败',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Saga模式订单表';

-- 创建库存Saga表
USE seata_storage;

CREATE TABLE IF NOT EXISTS `t_storage_saga` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '库存ID',
  `product_id` varchar(50) NOT NULL COMMENT '商品ID',
  `total` int(11) NOT NULL COMMENT '总库存',
  `used` int(11) NOT NULL DEFAULT '0' COMMENT '已用库存',
  `residue` int(11) NOT NULL COMMENT '剩余可用库存',
  `status` varchar(20) NOT NULL DEFAULT 'INIT' COMMENT '订单状态：INIT-初始化，PROCESSING-处理中，SUCCESS-成功，FAIL-失败',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_id` (`product_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Saga模式库存表';