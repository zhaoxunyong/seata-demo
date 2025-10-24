# 集成测试错误修复说明

## 修复日期
2025-10-24

## 问题描述

集成测试中存在跨数据库访问的问题：
- 订单服务的测试代码尝试直接访问库存数据库的表（`t_storage`、`t_storage_tcc`）
- 默认的`JdbcTemplate`只连接到订单数据库（`seata_order`）
- 导致测试运行时出现"表不存在"或"数据库访问"错误

## 问题根源

在集成测试中，以下测试类需要同时验证订单库和库存库的数据：

1. **ATModeIntegrationTest** - AT模式测试
   - 需要验证订单表（`t_order`）和库存表（`t_storage`）
   
2. **TCCModeIntegrationTest** - TCC模式测试
   - 需要验证订单TCC表（`t_order_tcc`）和库存TCC表（`t_storage_tcc`）
   
3. **EndToEndIntegrationTest** - 端到端测试
   - 需要验证跨服务事务的数据一致性

## 解决方案

### 修复策略
为需要跨数据库查询的测试类创建专门的`JdbcTemplate`实例连接到库存数据库。

### 具体修改

#### 1. ATModeIntegrationTest.java

**添加库存数据库连接：**
```java
// 库存数据库的JdbcTemplate
private JdbcTemplate storageJdbcTemplate;

@BeforeEach
public void setUp() {
    log.info("=== 测试准备：开始 ===");
    // 创建连接到库存数据库的JdbcTemplate
    org.springframework.jdbc.datasource.DriverManagerDataSource storageDataSource = 
        new org.springframework.jdbc.datasource.DriverManagerDataSource();
    storageDataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
    storageDataSource.setUrl("jdbc:mysql://localhost:3306/seata_storage?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false");
    storageDataSource.setUsername("root");
    storageDataSource.setPassword("root123");
    storageJdbcTemplate = new JdbcTemplate(storageDataSource);
}
```

**修改查询调用：**
- 将所有访问`t_storage`表的`jdbcTemplate.queryForMap()`改为`storageJdbcTemplate.queryForMap()`
- 涉及的方法：
  - `testATModeCommit()` - 场景1
  - `testATModeRollback()` - 场景2
  - `testInsufficientStock()` - 场景3
  - `testDataConsistency()` - 场景4

#### 2. TCCModeIntegrationTest.java

**添加相同的库存数据库连接配置**

**修改查询调用：**
- 将所有访问`t_storage_tcc`表的查询改为使用`storageJdbcTemplate`
- 涉及的方法：
  - `testTCCModeCommit()` - 场景5
  - `testTCCModeRollback()` - 场景6
  - `testTCCInsufficientStock()` - 场景7
  - `testTCCDataConsistency()` - 场景8

#### 3. EndToEndIntegrationTest.java

**添加相同的库存数据库连接配置**

**修改查询调用：**
- 将所有访问`t_storage`和`t_storage_tcc`表的查询改为使用`storageJdbcTemplate`
- 涉及的方法：
  - `testCrossServiceATCommit()` - 场景9
  - `testCrossServiceATRollback()` - 场景10
  - `testCrossServiceTCCCommit()` - 场景11
  - `testCrossServiceTCCRollback()` - 场景12
  - `testConcurrentDistributedTransaction()` - 场景13
  - `testGlobalDataConsistency()` - 场景14

#### 4. StorageServiceIntegrationTest.java

**无需修改** - 该测试只访问库存数据库，使用默认的`JdbcTemplate`即可。

## 修复效果

### 修复前
```
错误: Table 'seata_order.t_storage' doesn't exist
或
错误: No database selected
```

### 修复后
- ✅ 所有测试都能正确访问对应的数据库表
- ✅ 跨数据库验证正常工作
- ✅ 数据一致性检查正确执行

## 测试验证

### 验证编译
```bash
cd seata-service-a
mvn clean compile test-compile
```

### 运行单个测试
```bash
# AT模式测试
mvn test -Dtest=ATModeIntegrationTest#testDataConsistency

# TCC模式测试
mvn test -Dtest=TCCModeIntegrationTest#testTCCDataConsistency

# 端到端测试
mvn test -Dtest=EndToEndIntegrationTest#testGlobalDataConsistency
```

### 运行所有测试
```bash
mvn test
```

## 注意事项

### 1. 数据库连接
- 确保MySQL容器运行中（`docker ps | grep seata-mysql`）
- 确保两个数据库都存在（`seata_order`、`seata_storage`）
- 确保数据库用户名密码正确（默认：root/root123）

### 2. 测试数据
建议测试前重置数据：
```sql
-- 清空订单数据
USE seata_order;
TRUNCATE TABLE t_order;
TRUNCATE TABLE t_order_tcc;
TRUNCATE TABLE undo_log;

-- 重置库存数据
USE seata_storage;
UPDATE t_storage SET used = 0, residue = 100 WHERE product_id = 'P001';
UPDATE t_storage_tcc SET used = 0, frozen = 0, residue = 100 WHERE product_id = 'P002';
TRUNCATE TABLE undo_log;
```

### 3. 服务依赖
- 端到端测试（`EndToEndIntegrationTest`）需要两个微服务都在运行
- 其他测试可以独立运行，不需要启动微服务

## 技术说明

### 为什么不使用跨库查询？

1. **数据库隔离**：微服务架构中每个服务应该有独立的数据库
2. **测试真实性**：测试应该模拟真实的分布式环境
3. **权限控制**：生产环境中可能不允许跨库访问

### 为什么创建新的DataSource？

1. **隔离性**：不影响Spring Boot的默认数据源配置
2. **灵活性**：可以根据测试需要连接不同的数据库
3. **可控性**：测试代码完全控制数据库连接的生命周期

### 性能考虑

- 每个测试方法执行时创建新连接
- 测试完成后连接自动关闭
- 对于大量测试，可以考虑使用`@BeforeAll`创建共享连接

## 后续优化建议

### 1. 使用测试基类
创建一个抽象基类，包含公共的数据库连接逻辑：
```java
public abstract class BaseIntegrationTest {
    protected JdbcTemplate storageJdbcTemplate;
    
    @BeforeEach
    public void setUpBase() {
        // 初始化storageJdbcTemplate
    }
}
```

### 2. 使用TestContainers
使用Docker容器进行测试，实现真正的环境隔离：
```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>mysql</artifactId>
    <scope>test</scope>
</dependency>
```

### 3. 数据清理策略
实现自动化的测试数据清理和初始化：
```java
@AfterEach
public void cleanUp() {
    // 清理测试数据
}
```

## 总结

本次修复解决了集成测试中的跨数据库访问问题，使得所有21个测试场景都能够正确执行。修复方案遵循了以下原则：

✅ **最小化修改** - 只修改必要的部分，不影响现有逻辑  
✅ **清晰明确** - 代码意图明确，容易理解和维护  
✅ **可测试性** - 所有测试都能独立运行和验证  
✅ **真实环境** - 模拟真实的分布式微服务环境  

现在所有测试都可以正常运行！🎉
