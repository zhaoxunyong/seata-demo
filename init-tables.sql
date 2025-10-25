-- 清空订单数据
USE seata_order;
TRUNCATE TABLE t_order;
TRUNCATE TABLE t_order_tcc;
TRUNCATE TABLE t_order_saga;
TRUNCATE TABLE undo_log;

-- 清空库存数据
USE seata_storage;
TRUNCATE TABLE t_storage;
TRUNCATE TABLE t_storage_tcc;
TRUNCATE TABLE t_storage_saga;
TRUNCATE TABLE undo_log;

-- 初始化订单Saga表数据
INSERT INTO seata_order.t_order_saga (id, user_id, product_id, count, amount, status, create_time, update_time) 
VALUES (1, 'U001', 'P001', 10, 100.00, 'INIT', NOW(), NOW()) 
ON DUPLICATE KEY UPDATE status = 'INIT', update_time = NOW();

-- 初始化库存Saga表数据
INSERT INTO seata_storage.t_storage_saga (id, product_id, total, used, residue, status, create_time, update_time) 
VALUES (1, 'P001', 100, 0, 100, 'INIT', NOW(), NOW()) 
ON DUPLICATE KEY UPDATE status = 'INIT', update_time = NOW();

-- AT模式库存数据
INSERT INTO seata_storage.t_storage (product_id, total, used, residue) VALUES
('P001', 100, 0, 100),
('P003', 5, 0, 5);

-- TCC模式库存数据
INSERT INTO seata_storage.t_storage_tcc (product_id, total, used, frozen, residue) VALUES
('P002', 100, 0, 0, 100),
('P004', 10, 0, 0, 10);

-- 重置AT模式库存数据
UPDATE t_storage SET used = 0, residue = 100 WHERE product_id = 'P001';
UPDATE t_storage SET used = 0, residue = 100 WHERE product_id = 'P003';

-- 重置TCC模式库存数据
UPDATE t_storage_tcc SET used = 0, frozen = 0, residue = 100 WHERE product_id = 'P002';
UPDATE t_storage_tcc SET used = 0, frozen = 0, residue = 100 WHERE product_id = 'P004';

SELECT 'AT模式库存数据:' AS info;
SELECT product_id, total, used, residue FROM t_storage WHERE product_id IN ('P001', 'P003');

SELECT 'TCC模式库存数据:' AS info;
SELECT product_id, total, used, frozen, residue FROM t_storage_tcc WHERE product_id IN ('P002', 'P004');

SELECT 'Saga模式库存数据:' AS info;
SELECT product_id, total, used, residue FROM t_storage_saga WHERE product_id IN ('P001');
