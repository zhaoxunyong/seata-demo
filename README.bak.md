# Seata 分布式事务技术验证项目

## 项目概述

本项目是基于Seata框架的分布式事务技术验证Demo，实现了AT模式、TCC模式和Saga模式三种分布式事务解决方案，通过订单-库存的经典业务场景，验证Seata在微服务架构下的分布式事务一致性保障能力。

## 技术栈

| 组件 | 版本 | 说明 |
|------|------|------|
| Java | 11 | 运行时环境 |
| Spring Boot | 2.3.12.RELEASE | 应用框架 |
| Spring Cloud Alibaba | 2.2.8.RELEASE | 微服务框架 |
| Seata | 对应版本 | 分布式事务框架 |
| MyBatis Plus | 3.4.3 | ORM框架 |
| Swagger | 3.0.0 | API文档 |
| MySQL | 5.7+ | 关系型数据库 |
| Druid | 1.2.6 | 数据源 |

## 项目结构

```
seata-demo/
├── seata-service-a/          # 订单服务（端口8081）
│   ├── src/main/java/
│   │   └── com/example/seata/order/
│   │       ├── controller/    # 控制器层
│   │       ├── service/       # 服务层（AT/TCC业务逻辑）
│   │       ├── mapper/        # 数据访问层
│   │       ├── entity/        # 实体类
│   │       ├── dto/           # 数据传输对象
│   │       ├── feign/         # Feign客户端
│   │       ├── config/        # 配置类
│   │       └── exception/     # 异常类
│   └── pom.xml
├── seata-service-b/          # 库存服务（端口8082）
│   ├── src/main/java/
│   │   └── com/example/seata/storage/
│   │       ├── controller/    # 控制器层
│   │       ├── service/       # 服务层（AT/TCC业务逻辑）
│   │       ├── mapper/        # 数据访问层
│   │       ├── entity/        # 实体类
│   │       ├── dto/           # 数据传输对象
│   │       ├── config/        # 配置类
│   │       └── exception/     # 异常类
│   └── pom.xml
└── README.md
```

## 核心功能

### AT模式验证

**原理**：基于两阶段提交协议，通过undo_log实现自动回滚

**验证场景**：
- ✅ 正常提交流程：订单创建 + 库存扣减成功
- ✅ 回滚流程：模拟异常触发全局事务回滚

**接口**：
- `POST /order/create-at` - AT模式正常提交
- `POST /order/create-at-rollback` - AT模式回滚场景

### TCC模式验证

**原理**：Try-Confirm-Cancel三阶段手动控制事务

**验证场景**：
- ✅ Try-Confirm流程：订单创建（INIT → SUCCESS）+ 库存冻结 → 确认
- ✅ Try-Cancel流程：模拟异常触发Cancel，释放冻结库存

**接口**：
- `POST /order/create-tcc` - TCC模式正常提交
- `POST /order/create-tcc-rollback` - TCC模式回滚场景

### Saga模式验证

**原理**：基于状态机引擎的长事务解决方案，通过服务编排实现正向操作和补偿操作

**验证场景**：
- ✅ 正向流程：订单创建（PROCESSING → SUCCESS）+ 库存扣减 → 完成
- ✅ 补偿流程：模拟异常触发补偿，订单状态转为FAIL，库存回滚

**接口**：
- `POST /order-saga/create` - Saga模式正常提交
- `POST /order-saga/create-rollback` - Saga模式回滚场景

## 快速开始

### 方式一：一键启动（推荐）

使用提供的脚本一键启动所有基础环境：

```bash
# 赋予执行权限
chmod +x start-all.sh stop-all.sh

# 启动MySQL和Seata Server
./start-all.sh

# 按照提示启动微服务
```

**停止服务**：
```bash
./stop-all.sh
```

### 方式二：手动启动

#### 1. 环境准备

##### 启动MySQL数据库（Docker）

```bash
docker run -d \
  --name seata-mysql \
  -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=root123 \
  -e MYSQL_CHARACTER_SET_SERVER=utf8mb4 \
  -v seata-mysql-data:/var/lib/mysql \
  mysql:5.7
```

##### 初始化数据库

数据库和表已经在环境中创建完成：
- 数据库：`seata_order`、`seata_storage`
- AT模式表：`t_order`、`t_storage`、`undo_log`
- TCC模式表：`t_order_tcc`、`t_storage_tcc`

#### 2. 启动Seata Server

#### 方式一：Docker启动（推荐）

```bash
# 拉取Seata Server镜像
docker pull seataio/seata-server:1.4.2

# 启动Seata Server容器
docker run -d \
  --name seata-server \
  -p 8091:8091 \
  -e SEATA_IP=127.0.0.1 \
  -e SEATA_PORT=8091 \
  seataio/seata-server:1.4.2

# 验证启动
docker logs -f seata-server
```

**预期日志输出**：`Server started, listen port: 8091`

#### 方式二：本地启动

```bash
# 下载Seata Server（版本需与Spring Cloud Alibaba 2.2.8匹配）
wget https://github.com/seata/seata/releases/download/v1.4.2/seata-server-1.4.2.tar.gz
tar -zxvf seata-server-1.4.2.tar.gz
cd seata-server-1.4.2

# 启动
sh bin/seata-server.sh -p 8091 -h 127.0.0.1
```

> ⚠️ **注意**：详细的Seata Server配置和部署说明，请查阅 [DEPLOYMENT.md](./DEPLOYMENT.md)

#### 3. 启动微服务

##### 启动库存服务（Service B）

```bash
cd seata-service-b
mvn spring-boot:run
```

访问：http://localhost:8082/swagger-ui/index.html

##### 启动订单服务（Service A）

```bash
cd seata-service-a
mvn spring-boot:run
```

访问：http://localhost:8081/swagger-ui/index.html

## 测试指南

### AT模式测试

#### 场景1：AT模式正常提交

1. 打开Swagger UI：http://localhost:8081/swagger-ui/index.html
2. 找到 `POST /order/create-at` 接口
3. 填写请求参数：

```json
{
  "userId": "U001",
  "productId": "P001",
  "count": 10,
  "amount": 100.00
}
```

4. 执行请求
5. 验证结果：
   - 订单表新增记录
   - 库存表 `residue` 减少10，`used` 增加10
   - undo_log被异步清理

#### 场景2：AT模式回滚

1. 找到 `POST /order/create-at-rollback` 接口
2. 填写请求参数（同上）
3. 执行请求
4. 验证结果：
   - 接口返回异常信息
   - 订单表无新增记录
   - 库存数据未变化

### TCC模式测试

#### 场景3：TCC模式Try-Confirm

1. 找到 `POST /order/create-tcc` 接口
2. 填写请求参数：

```json
{
  "userId": "U003",
  "productId": "P002",
  "count": 15,
  "amount": 150.00
}
```

3. 执行请求
4. 验证结果：
   - Try阶段：订单状态=INIT，库存frozen增加、residue减少
   - Confirm阶段：订单状态=SUCCESS，frozen转为used

#### 场景4：TCC模式Try-Cancel

1. 找到 `POST /order/create-tcc-rollback` 接口
2. 填写请求参数（同上）
3. 执行请求
4. 验证结果：
   - 接口返回异常信息
   - 订单状态=CANCEL
   - 冻结库存释放回residue

### Saga模式测试

#### 场景5：Saga模式正常提交

1. 打开Swagger UI：http://localhost:8081/swagger-ui/index.html
2. 找到 `POST /order-saga/create` 接口
3. 填写请求参数：

```json
{
  "userId": "U001",
  "productId": "P001",
  "count": 10,
  "amount": 100.00
}
```

4. 执行请求
5. 验证结果：
   - Saga订单表新增记录，状态从PROCESSING转为SUCCESS
   - Saga库存表used增加、residue减少，状态从PROCESSING转为SUCCESS

#### 场景6：Saga模式补偿回滚

1. 找到 `POST /order-saga/create-rollback` 接口
2. 填写请求参数（同上）
3. 执行请求
4. 验证结果：
   - 接口返回异常信息
   - Saga订单表状态转为FAIL
   - Saga库存表数据回滚，used减少、residue增加

### 数据验证SQL

```
-- 查看AT模式订单
SELECT * FROM seata_order.t_order ORDER BY id DESC LIMIT 5;

-- 查看AT模式库存
SELECT * FROM seata_storage.t_storage WHERE product_id = 'P001';

-- 查看TCC模式订单
SELECT * FROM seata_order.t_order_tcc ORDER BY id DESC LIMIT 5;

-- 查看TCC模式库存
SELECT * FROM seata_storage.t_storage_tcc WHERE product_id = 'P002';

-- 查看undo_log（AT模式专用）
SELECT * FROM seata_order.undo_log ORDER BY id DESC LIMIT 5;
SELECT * FROM seata_storage.undo_log ORDER BY id DESC LIMIT 5;

-- 查看Saga模式订单
SELECT * FROM seata_order.t_order_saga ORDER BY id DESC LIMIT 5;

-- 查看Saga模式库存
SELECT * FROM seata_storage.t_storage_saga WHERE product_id = 'P001';
```

## 关键配置说明

### Seata配置（application.yml）

```yaml
seata:
  enabled: true
  application-id: ${spring.application.name}
  tx-service-group: my_test_tx_group
  enable-auto-data-source-proxy: true
  data-source-proxy-mode: AT  # AT模式数据源代理
  service:
    vgroup-mapping:
      my_test_tx_group: default
    grouplist:
      default: 127.0.0.1:8091  # Seata Server地址
```

### 全局事务注解

```java
@GlobalTransactional(name = "事务名称", rollbackFor = Exception.class)
public void businessMethod() {
    // 业务逻辑
}
```

### TCC注解

```java
@LocalTCC
public interface XXXTCCService {
    @TwoPhaseBusinessAction(
        name = "XXXTCCService",
        commitMethod = "confirm",
        rollbackMethod = "cancel"
    )
    boolean tryMethod(...);
    
    boolean confirm(BusinessActionContext context);
    boolean cancel(BusinessActionContext context);
}
```

## 架构设计

### Seata角色

- **TM (Transaction Manager)**：事务管理器，位于订单服务，负责开启和提交/回滚全局事务
- **RM (Resource Manager)**：资源管理器，位于订单和库存服务，负责分支事务的注册和执行
- **TC (Transaction Coordinator)**：事务协调器，即Seata Server，负责全局事务的协调

### 服务职责

| 服务 | 端口 | 数据库 | 职责 |
|------|------|--------|------|
| seata-service-a | 8081 | seata_order | 订单服务，TM事务发起方 |
| seata-service-b | 8082 | seata_storage | 库存服务，RM分支事务参与方 |

## 验证要点

### AT模式

- ✅ undo_log生成与清理机制
- ✅ 全局锁防止脏写
- ✅ 两阶段提交流程
- ✅ 自动回滚机制

### TCC模式

- ✅ Try-Confirm-Cancel三阶段流程
- ✅ 库存冻结与释放
- ✅ 订单状态流转（INIT → SUCCESS/CANCEL）
- ✅ 幂等性处理
- ✅ 空回滚处理

### Saga模式

- ✅ 状态机编排流程
- ✅ 正向服务调用
- ✅ 补偿服务调用
- ✅ 订单状态流转（PROCESSING → SUCCESS/FAIL）
- ✅ 库存扣减与补偿回滚

## 常见问题

### 1. Seata Server连接失败

**现象**：服务启动时报错 "can not connect to seata server"

**解决**：
1. 确认Seata Server已启动，端口8091可访问
2. 检查配置文件中 `seata.service.grouplist.default` 是否正确

### 2. undo_log未清理

**现象**：事务提交后，undo_log表仍有数据

**解决**：
1. 检查Seata Server是否正常运行
2. 查看服务日志，确认二阶段提交是否执行
3. undo_log异步清理可能有延迟，等待几秒后再查询

### 3. TCC幂等性问题

**现象**：重复调用Confirm导致数据异常

**解决**：
1. 在Confirm/Cancel方法中增加幂等性判断
2. 使用业务状态字段控制（如订单status）
3. 返回true避免TC重试

## 参考文档

- [设计文档](/.qoder/quests/seata-function-verification.md)
- [Seata官方文档](https://seata.io/zh-cn/)
- [Spring Cloud Alibaba文档](https://spring-cloud-alibaba-group.github.io/github-pages/hoxton/zh-cn/index.html)

## 作者

Seata Demo Project

## 许可证

MIT License
