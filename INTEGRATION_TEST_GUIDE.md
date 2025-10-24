# Seata 集成测试指南

## 概述

本文档提供完整的Seata分布式事务集成测试指南，包括AT模式和TCC模式的各种测试场景。

## 测试架构

```
seata-demo/
├── seata-service-a/src/test/
│   ├── java/com/example/seata/order/
│   │   ├── ATModeIntegrationTest.java          # AT模式集成测试
│   │   ├── TCCModeIntegrationTest.java         # TCC模式集成测试
│   │   └── EndToEndIntegrationTest.java        # 端到端集成测试
│   └── resources/
│       └── application-test.yml                # 测试配置
│
└── seata-service-b/src/test/
    ├── java/com/example/seata/storage/
    │   └── StorageServiceIntegrationTest.java  # 库存服务集成测试
    └── resources/
        └── application-test.yml                # 测试配置
```

## 测试覆盖场景

### 订单服务 - AT模式测试 (ATModeIntegrationTest)

| 场景编号 | 测试场景 | 验证点 |
|---------|---------|--------|
| 场景1 | AT模式正常提交流程 | 订单创建、库存扣减、undo_log清理 |
| 场景2 | AT模式回滚流程 | 订单回滚、库存回滚、undo_log清理 |
| 场景3 | 库存不足异常处理 | 异常捕获、订单未创建、库存不变 |
| 场景4 | 数据一致性验证 | total = used + residue |

### 订单服务 - TCC模式测试 (TCCModeIntegrationTest)

| 场景编号 | 测试场景 | 验证点 |
|---------|---------|--------|
| 场景5 | TCC Try-Confirm正常流程 | Try冻结、Confirm确认、状态变更 |
| 场景6 | TCC Try-Cancel回滚流程 | Try冻结、Cancel释放、状态变更 |
| 场景7 | TCC库存不足异常 | Try阶段失败、库存不变 |
| 场景8 | TCC数据一致性 | total = used + frozen + residue |

### 库存服务测试 (StorageServiceIntegrationTest)

| 场景编号 | 测试场景 | 验证点 |
|---------|---------|--------|
| 场景1 | AT模式库存扣减 | 库存正确扣减 |
| 场景2 | AT模式库存不足 | 异常处理、数据不变 |
| 场景3 | TCC Try阶段库存冻结 | residue减少、frozen增加 |
| 场景4 | TCC Confirm阶段 | frozen转used |
| 场景5 | TCC Cancel阶段 | frozen释放回residue |
| 场景6 | TCC库存不足 | Try失败、数据不变 |
| 场景7 | 数据一致性 | AT和TCC恒等式验证 |

### 端到端测试 (EndToEndIntegrationTest)

| 场景编号 | 测试场景 | 验证点 |
|---------|---------|--------|
| 场景9 | 跨服务AT提交 | 订单库和库存库数据一致性 |
| 场景10 | 跨服务AT回滚 | 两个库同时回滚 |
| 场景11 | 跨服务TCC提交 | 订单Confirm、库存Confirm |
| 场景12 | 跨服务TCC回滚 | 订单Cancel、库存Cancel |
| 场景13 | 并发事务一致性 | 多线程并发执行、数据正确 |
| 场景14 | 全局数据一致性 | 整体数据检查、undo_log清理 |

## 测试前准备

### 1. 启动MySQL数据库

```bash
# 使用Docker启动MySQL（如果尚未启动）
./start-all.sh

# 或者手动启动
docker start seata-mysql
```

### 2. 验证数据库连接

```bash
# 连接MySQL
docker exec -it seata-mysql mysql -uroot -proot123

# 验证数据库存在
SHOW DATABASES;

# 验证表结构
USE seata_order;
SHOW TABLES;

USE seata_storage;
SHOW TABLES;
```

### 3. 初始化测试数据

```sql
-- 清空历史数据
USE seata_order;
TRUNCATE TABLE t_order;
TRUNCATE TABLE t_order_tcc;
TRUNCATE TABLE undo_log;

USE seata_storage;
TRUNCATE TABLE undo_log;

-- 重置AT模式测试数据
UPDATE t_storage SET used = 0, residue = 100 WHERE product_id = 'P001';

-- 重置TCC模式测试数据
UPDATE t_storage_tcc SET used = 0, frozen = 0, residue = 100 WHERE product_id = 'P002';
```

### 4. 启动Seata Server

```bash
# 检查Seata Server是否运行
docker ps | grep seata-server

# 如果未运行，启动Seata Server
docker start seata-server

# 查看Seata Server日志
docker logs -f seata-server
```

### 5. 启动微服务

**启动订单服务（Service A）：**
```bash
cd seata-service-a
mvn spring-boot:run
```

**启动库存服务（Service B）：**
```bash
cd seata-service-b
mvn spring-boot:run
```

## 运行测试

### 方式1：使用Maven运行所有测试

**测试订单服务：**
```bash
cd seata-service-a
mvn test
```

**测试库存服务：**
```bash
cd seata-service-b
mvn test
```

### 方式2：运行指定测试类

**运行AT模式测试：**
```bash
cd seata-service-a
mvn test -Dtest=ATModeIntegrationTest
```

**运行TCC模式测试：**
```bash
cd seata-service-a
mvn test -Dtest=TCCModeIntegrationTest
```

**运行端到端测试：**
```bash
cd seata-service-a
mvn test -Dtest=EndToEndIntegrationTest
```

**运行库存服务测试：**
```bash
cd seata-service-b
mvn test -Dtest=StorageServiceIntegrationTest
```

### 方式3：运行单个测试方法

```bash
# 示例：只运行AT模式正常提交测试
cd seata-service-a
mvn test -Dtest=ATModeIntegrationTest#testATModeCommit
```

### 方式4：使用IDE运行

1. 在IDEA中打开测试类
2. 点击类名左侧的绿色运行按钮运行所有测试
3. 或点击单个测试方法左侧的绿色运行按钮运行单个测试

## 测试结果验证

### 查看测试报告

测试完成后，Maven会在控制台输出测试结果：

```
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

### 查看详细测试日志

测试日志包含详细的执行信息：

```
========================================
场景1：AT模式正常提交流程验证
========================================
初始库存状态 - residue: 100, used: 0
发起订单创建请求...
订单创建成功，订单ID: 1
✓ 订单数据验证通过
最终库存状态 - residue: 90, used: 10
✓ 库存扣减验证通过
✓ undo_log清理验证通过
========================================
场景1：AT模式正常提交流程验证 - 通过 ✓
========================================
```

### 手动验证数据库

```sql
-- 查看订单数据
SELECT * FROM seata_order.t_order;
SELECT * FROM seata_order.t_order_tcc;

-- 查看库存数据
SELECT * FROM seata_storage.t_storage WHERE product_id = 'P001';
SELECT * FROM seata_storage.t_storage_tcc WHERE product_id = 'P002';

-- 验证undo_log清理
SELECT * FROM seata_order.undo_log;
SELECT * FROM seata_storage.undo_log;

-- 验证数据一致性
SELECT 
    product_id,
    total,
    used,
    residue,
    (used + residue) AS calculated_total,
    CASE WHEN total = (used + residue) THEN 'PASS' ELSE 'FAIL' END AS consistency_check
FROM seata_storage.t_storage;
```

## 常见问题排查

### 1. 测试连接数据库失败

**问题：** `Connection refused` 或 `Unknown database`

**解决方案：**
```bash
# 检查MySQL容器状态
docker ps | grep seata-mysql

# 如果未运行，启动容器
docker start seata-mysql

# 检查数据库是否存在
docker exec -it seata-mysql mysql -uroot -proot123 -e "SHOW DATABASES;"
```

### 2. Seata Server连接失败

**问题：** `No available service found in cluster 'default'`

**解决方案：**
```bash
# 检查Seata Server状态
docker ps | grep seata-server

# 启动Seata Server
docker start seata-server

# 查看日志确认启动成功
docker logs seata-server | tail -50
```

### 3. Feign调用失败

**问题：** 端到端测试失败，提示 `Connection refused`

**解决方案：**
- 确保订单服务（8081）和库存服务（8082）都已启动
- 检查服务健康状态：
  - http://localhost:8081/swagger-ui/index.html
  - http://localhost:8082/swagger-ui/index.html

### 4. 测试数据冲突

**问题：** 重复运行测试失败

**解决方案：**
```sql
-- 清空测试数据
USE seata_order;
TRUNCATE TABLE t_order;
TRUNCATE TABLE t_order_tcc;
TRUNCATE TABLE undo_log;

USE seata_storage;
UPDATE t_storage SET used = 0, residue = 100 WHERE product_id = 'P001';
UPDATE t_storage_tcc SET used = 0, frozen = 0, residue = 100 WHERE product_id = 'P002';
TRUNCATE TABLE undo_log;
```

### 5. undo_log未清理

**问题：** 测试发现undo_log记录未被清理

**原因：** Seata异步清理需要时间

**解决方案：**
- 测试中已增加等待时间（2-3秒）
- 如果仍未清理，检查Seata Server日志
- 手动清理：`DELETE FROM undo_log;`

## 性能测试建议

### 并发测试参数调整

在 `EndToEndIntegrationTest` 中的场景13，可以调整并发参数：

```java
// 增加并发数量
int concurrentCount = 10;  // 默认5
int eachOrderCount = 5;    // 默认3
```

### 压力测试

```bash
# 使用JMeter或其他工具对Swagger接口进行压力测试
# 测试端点：
# - POST http://localhost:8081/order/create-at
# - POST http://localhost:8081/order/create-tcc
```

## 测试最佳实践

1. **测试隔离**：每个测试使用不同的用户ID，避免数据冲突
2. **等待时间**：分布式事务需要时间完成，适当增加等待
3. **数据清理**：测试前重置数据，确保初始状态一致
4. **日志分析**：出现问题时查看详细日志定位原因
5. **循序渐进**：先运行单元测试，再运行集成测试

## 测试覆盖率目标

- 代码覆盖率：> 80%
- 分支覆盖率：> 75%
- 场景覆盖率：100%（所有文档中的场景都已实现）

## 持续集成

### CI/CD集成示例

```yaml
# .github/workflows/test.yml
name: Integration Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      
      - name: Set up JDK 11
        uses: actions/setup-java@v2
        with:
          java-version: '11'
          
      - name: Start MySQL
        run: |
          docker run -d --name seata-mysql \
            -p 3306:3306 \
            -e MYSQL_ROOT_PASSWORD=root123 \
            mysql:5.7
          
      - name: Start Seata Server
        run: |
          docker run -d --name seata-server \
            -p 8091:8091 \
            seataio/seata-server:1.4.2
          
      - name: Run Tests
        run: |
          cd seata-service-a && mvn test
          cd seata-service-b && mvn test
```

## 总结

本测试套件提供了完整的Seata分布式事务验证，包括：

✓ **4个测试类**，共14个测试场景
✓ **AT模式**：提交、回滚、异常处理
✓ **TCC模式**：Try-Confirm、Try-Cancel、异常处理
✓ **跨服务事务**：分布式一致性验证
✓ **并发场景**：多线程并发安全性
✓ **数据一致性**：全局数据检查

所有测试都包含详细的日志输出和断言验证，确保Seata分布式事务的正确性和可靠性。
