# 🧪 Seata 集成测试快速开始

## 📋 概述

本项目已创建完整的Seata分布式事务集成测试套件，包含**21个测试场景**，覆盖AT模式和TCC模式的各种情况。

## 🎯 测试文件列表

```
seata-demo/
├── 📄 INTEGRATION_TEST_GUIDE.md          # 详细测试指南
├── 📄 TEST_SUMMARY.md                    # 测试总结报告
├── 🔧 run-integration-tests.sh           # 自动化测试脚本
│
├── seata-service-a/src/test/
│   ├── java/com/example/seata/order/
│   │   ├── ✅ ATModeIntegrationTest.java          # AT模式测试（4个场景）
│   │   ├── ✅ TCCModeIntegrationTest.java         # TCC模式测试（4个场景）
│   │   └── ✅ EndToEndIntegrationTest.java        # 端到端测试（6个场景）
│   └── resources/
│       └── application-test.yml                   # 测试配置
│
└── seata-service-b/src/test/
    ├── java/com/example/seata/storage/
    │   └── ✅ StorageServiceIntegrationTest.java  # 库存服务测试（7个场景）
    └── resources/
        └── application-test.yml                   # 测试配置
```

## 🚀 快速开始

### 方法1️⃣：使用自动化脚本（推荐）

```bash
# 进入项目目录
cd seata-demo

# 运行所有集成测试（包含环境检查和数据重置）
./run-integration-tests.sh
```

脚本会自动：
- ✅ 检查MySQL和Seata Server状态
- ✅ 检查微服务运行状态
- ✅ 提示是否重置测试数据
- ✅ 批量运行所有测试
- ✅ 显示测试统计和数据状态

### 方法2️⃣：使用Maven运行测试

```bash
# 运行订单服务的所有测试
cd seata-service-a
mvn test

# 运行库存服务的所有测试
cd seata-service-b
mvn test
```

### 方法3️⃣：运行单个测试类

```bash
cd seata-service-a

# AT模式测试
mvn test -Dtest=ATModeIntegrationTest

# TCC模式测试
mvn test -Dtest=TCCModeIntegrationTest

# 端到端测试（需要两个服务都运行）
mvn test -Dtest=EndToEndIntegrationTest
```

### 方法4️⃣：运行单个测试场景

```bash
cd seata-service-a

# 只运行AT模式正常提交测试
mvn test -Dtest=ATModeIntegrationTest#testATModeCommit

# 只运行TCC模式回滚测试
mvn test -Dtest=TCCModeIntegrationTest#testTCCModeRollback
```

## 📊 测试场景一览

| 测试类 | 场景数 | 主要验证点 |
|--------|-------|-----------|
| **ATModeIntegrationTest** | 4 | AT模式提交、回滚、异常处理、一致性 |
| **TCCModeIntegrationTest** | 4 | TCC Try-Confirm、Try-Cancel、一致性 |
| **StorageServiceIntegrationTest** | 7 | 库存扣减、冻结、释放、异常处理 |
| **EndToEndIntegrationTest** | 6 | 跨服务事务、并发安全、全局一致性 |
| **总计** | **21** | **100%场景覆盖** |

## ⚙️ 测试前准备

### 必需步骤

1. **启动MySQL容器**
```bash
# 使用启动脚本
./start-all.sh

# 或手动启动
docker start seata-mysql
```

2. **验证数据库连接**
```bash
docker exec -it seata-mysql mysql -uroot -proot123 -e "SHOW DATABASES;"
```

3. **启动Seata Server**（推荐）
```bash
docker start seata-server
```

### 可选步骤（端到端测试需要）

4. **启动订单服务**
```bash
cd seata-service-a
mvn spring-boot:run
```

5. **启动库存服务**
```bash
cd seata-service-b
mvn spring-boot:run
```

### 数据重置（推荐每次测试前执行）

```bash
# 使用自动化脚本会自动提示
./run-integration-tests.sh

# 或手动重置
docker exec -i seata-mysql mysql -uroot -proot123 << EOF
USE seata_order;
TRUNCATE TABLE t_order;
TRUNCATE TABLE t_order_tcc;
TRUNCATE TABLE undo_log;

USE seata_storage;
UPDATE t_storage SET used = 0, residue = 100 WHERE product_id = 'P001';
UPDATE t_storage_tcc SET used = 0, frozen = 0, residue = 100 WHERE product_id = 'P002';
TRUNCATE TABLE undo_log;
EOF
```

## 📖 测试场景详情

### AT模式测试（ATModeIntegrationTest）

✅ **场景1：AT模式正常提交流程**
- 验证订单创建 → 库存扣减 → undo_log清理

✅ **场景2：AT模式回滚流程**
- 验证异常触发 → 数据回滚 → undo_log清理

✅ **场景3：库存不足异常处理**
- 验证异常捕获 → 订单未创建 → 数据不变

✅ **场景4：数据一致性验证**
- 验证：total = used + residue

### TCC模式测试（TCCModeIntegrationTest）

✅ **场景5：TCC Try-Confirm流程**
- Try：库存冻结 → Confirm：frozen转used

✅ **场景6：TCC Try-Cancel流程**
- Try：库存冻结 → Cancel：frozen释放

✅ **场景7：TCC库存不足异常**
- Try阶段失败 → 数据不变

✅ **场景8：TCC数据一致性**
- 验证：total = used + frozen + residue

### 库存服务测试（StorageServiceIntegrationTest）

✅ **场景1-2：AT模式库存操作**
✅ **场景3-6：TCC模式库存操作**
✅ **场景7：数据一致性验证**

### 端到端测试（EndToEndIntegrationTest）

✅ **场景9-10：跨服务AT模式**（提交/回滚）
✅ **场景11-12：跨服务TCC模式**（提交/回滚）
✅ **场景13：并发场景一致性**
✅ **场景14：全局数据一致性**

## 🔍 测试结果示例

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

[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
```

## 🛠️ 常见问题

### 1. 数据库连接失败
```bash
# 检查MySQL状态
docker ps | grep seata-mysql

# 启动MySQL
docker start seata-mysql
```

### 2. Seata Server连接失败
```bash
# 检查Seata Server状态
docker ps | grep seata-server

# 启动Seata Server
docker start seata-server
```

### 3. 端到端测试失败
确保两个服务都在运行：
```bash
# 检查端口占用
lsof -i :8081  # 订单服务
lsof -i :8082  # 库存服务
```

### 4. 测试数据冲突
```bash
# 重置测试数据
./run-integration-tests.sh
# 选择 'y' 重置数据
```

## 📚 文档索引

- **[INTEGRATION_TEST_GUIDE.md](INTEGRATION_TEST_GUIDE.md)** - 详细的测试指南
  - 测试环境准备
  - 数据库初始化
  - 测试执行方法
  - 问题排查指南

- **[TEST_SUMMARY.md](TEST_SUMMARY.md)** - 测试总结报告
  - 测试覆盖率统计
  - 场景详细说明
  - 已知问题和注意事项
  - 后续改进建议

- **[seata-function-verification.md](.qoder/quests/seata-function-verification.md)** - 原始设计文档
  - Seata技术验证设计
  - AT/TCC模式原理
  - 业务场景设计

## 💡 提示

1. **首次运行建议**：先运行单个简单测试熟悉流程
   ```bash
   cd seata-service-a
   mvn test -Dtest=ATModeIntegrationTest#testDataConsistency
   ```

2. **完整测试建议**：使用自动化脚本
   ```bash
   ./run-integration-tests.sh
   ```

3. **CI/CD集成**：可以将Maven测试命令集成到CI/CD流程

4. **性能测试**：可以调整并发测试参数进行压力测试

## ✨ 特性

✅ **完整覆盖** - 21个测试场景，100%覆盖设计文档  
✅ **自动化** - 一键运行所有测试  
✅ **详细日志** - 每个步骤都有清晰的日志输出  
✅ **数据验证** - 使用断言和SQL检查数据一致性  
✅ **独立运行** - 每个测试类可以单独运行  
✅ **易于维护** - 清晰的代码结构和注释  

## 📞 支持

如有问题，请查看：
1. [INTEGRATION_TEST_GUIDE.md](INTEGRATION_TEST_GUIDE.md) - 详细指南
2. [TEST_SUMMARY.md](TEST_SUMMARY.md) - 测试总结
3. 项目代码中的注释和日志

---

**开始测试吧！** 🚀

```bash
./run-integration-tests.sh
```
