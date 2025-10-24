# Seata 技术验证设计文档

## 1. 概述

### 1.1 验证目标
本技术验证旨在通过实际的微服务场景，验证Seata分布式事务框架的两种核心事务模式的功能可用性和正确性：
- **AT模式**：自动化的分布式事务模式，基于本地ACID事务
- **TCC模式**：手动编程控制的补偿型事务模式

### 1.2 验证范围
- AT模式的两阶段提交机制
- TCC模式的Try-Confirm-Cancel三阶段操作
- 分布式事务的提交场景
- 分布式事务的回滚场景
- 全局锁机制验证
- 服务间事务传播验证

### 1.3 技术栈
| 组件 | 版本 | 说明 |
|------|------|------|
| Java | 11 | 运行时环境 |
| Spring Boot | 2.3.12.RELEASE | 应用框架 |
| Spring Cloud Alibaba | 2.2.8.RELEASE | 微服务框架 |
| Seata | 与Spring Cloud Alibaba 2.2.8对应版本 | 分布式事务框架 |
| Swagger | 3.x | API文档与测试界面 |
| MySQL | 5.7+ | 关系型数据库 |

## 2. 系统架构

### 2.1 整体架构

```
graph TB
    Client[Swagger UI测试客户端]
    
    subgraph 微服务集群
        ServiceA[seata-service-a<br/>订单服务]
        ServiceB[seata-service-b<br/>库存服务]
    end
    
    subgraph 基础设施
        SeataServer[Seata Server<br/>TC事务协调器]
        DBA[(MySQL-A<br/>订单数据库)]
        DBB[(MySQL-B<br/>库存数据库)]
    end
    
    Client -->|HTTP请求| ServiceA
    ServiceA -->|调用库存服务| ServiceB
    ServiceA -->|注册/上报分支事务| SeataServer
    ServiceB -->|注册/上报分支事务| SeataServer
    ServiceA -->|读写数据| DBA
    ServiceB -->|读写数据| DBB
```

### 2.2 服务职责划分

| 服务名称 | 端口 | 业务职责 | 数据库 |
|---------|------|---------|--------|
| seata-service-a | 8081 | 订单服务，作为全局事务发起方，处理订单创建业务 | seata_order |
| seata-service-b | 8082 | 库存服务，作为分支事务参与方，处理库存扣减业务 | seata_storage |
| Seata Server | 8091 | 事务协调器TC，负责全局事务的注册、提交、回滚协调 | seata (可选) |

### 2.3 Seata角色说明

```
graph LR
    TM[TM事务管理器<br/>Transaction Manager<br/>位于Service A]
    RM1[RM资源管理器1<br/>Resource Manager<br/>位于Service A]
    RM2[RM资源管理器2<br/>Resource Manager<br/>位于Service B]
    TC[TC事务协调器<br/>Transaction Coordinator<br/>Seata Server]
    
    TM -->|1.开启全局事务| TC
    RM1 -->|2.注册分支事务| TC
    RM2 -->|3.注册分支事务| TC
    TC -->|4.提交/回滚指令| RM1
    TC -->|5.提交/回滚指令| RM2
```

**角色说明**：
- **TM (Transaction Manager)**：事务管理器，负责开启全局事务、提交或回滚全局事务
- **RM (Resource Manager)**：资源管理器，负责分支事务的注册、状态上报、接收TC的提交或回滚指令
- **TC (Transaction Coordinator)**：事务协调器，负责维护全局事务和分支事务的状态，协调全局事务的提交或回滚

## 3. AT模式验证设计

### 3.1 AT模式原理

AT模式是一种无侵入的分布式事务解决方案，基于两阶段提交协议：

**一阶段**：
- 在本地事务中执行业务SQL
- 生成前后镜像数据（before image & after image）
- 将业务数据和回滚日志在同一个本地事务中提交
- 向TC注册分支事务并申请全局锁

**二阶段提交**：
- TC通知各RM异步删除回滚日志
- 操作快速完成

**二阶段回滚**：
- TC通知各RM进行回滚
- RM根据回滚日志生成反向SQL进行补偿
- 完成数据回滚

### 3.2 业务场景设计

**场景**：创建订单并扣减库存

```
sequenceDiagram
    participant Client as Swagger UI
    participant OrderService as 订单服务(Service A)
    participant StorageService as 库存服务(Service B)
    participant TC as Seata Server
    participant OrderDB as 订单数据库
    participant StorageDB as 库存数据库
    
    Client->>OrderService: POST /order/create-at
    activate OrderService
    OrderService->>TC: 开启全局事务(XID)
    TC-->>OrderService: 返回XID
    
    Note over OrderService: 一阶段开始
    OrderService->>OrderDB: 插入订单记录
    OrderDB->>OrderDB: 记录前后镜像
    OrderDB->>OrderDB: 生成undo_log
    OrderService->>TC: 注册分支事务1，申请全局锁
    OrderDB-->>OrderService: 提交本地事务
    
    OrderService->>StorageService: 调用扣减库存(携带XID)
    activate StorageService
    StorageService->>StorageDB: 更新库存数量
    StorageDB->>StorageDB: 记录前后镜像
    StorageDB->>StorageDB: 生成undo_log
    StorageService->>TC: 注册分支事务2，申请全局锁
    StorageDB-->>StorageService: 提交本地事务
    StorageService-->>OrderService: 返回成功
    deactivate StorageService
    
    Note over OrderService: 一阶段完成
    OrderService->>TC: 提交全局事务
    
    Note over TC: 二阶段提交
    TC->>OrderService: 异步删除undo_log
    TC->>StorageService: 异步删除undo_log
    
    OrderService-->>Client: 返回成功
    deactivate OrderService
```

### 3.3 数据模型

#### 订单表 (t_order)
| 字段名 | 类型 | 说明 | 约束 |
|--------|------|------|------|
| id | BIGINT | 订单ID | 主键、自增 |
| user_id | VARCHAR(50) | 用户ID | 非空 |
| product_id | VARCHAR(50) | 商品ID | 非空 |
| count | INT | 购买数量 | 非空 |
| amount | DECIMAL(10,2) | 订单金额 | 非空 |
| status | VARCHAR(20) | 订单状态 | 非空，默认INIT |

#### 库存表 (t_storage)
| 字段名 | 类型 | 说明 | 约束 |
|--------|------|------|------|
| id | BIGINT | 库存ID | 主键、自增 |
| product_id | VARCHAR(50) | 商品ID | 非空、唯一 |
| total | INT | 总库存 | 非空 |
| used | INT | 已用库存 | 非空、默认0 |
| residue | INT | 剩余库存 | 非空 |

#### 回滚日志表 (undo_log)
| 字段名 | 类型 | 说明 | 约束 |
|--------|------|------|------|
| id | BIGINT | 主键 | 自增 |
| branch_id | BIGINT | 分支事务ID | 非空 |
| xid | VARCHAR(100) | 全局事务ID | 非空 |
| context | VARCHAR(128) | 上下文 | 非空 |
| rollback_info | LONGBLOB | 回滚信息（前后镜像） | 非空 |
| log_status | INT | 日志状态 | 非空 |
| log_created | DATETIME | 创建时间 | 非空 |
| log_modified | DATETIME | 修改时间 | 非空 |

**说明**：undo_log表在seata_order和seata_storage两个数据库中都需要创建

### 3.4 API接口设计

#### 创建订单接口（AT模式 - 成功场景）
- **路径**: POST /order/create-at
- **功能**: 创建订单并扣减库存，验证AT模式正常提交流程
- **请求参数**:

| 参数名 | 类型 | 说明 | 必填 |
|--------|------|------|------|
| userId | String | 用户ID | 是 |
| productId | String | 商品ID | 是 |
| count | Integer | 购买数量 | 是 |
| amount | BigDecimal | 订单金额 | 是 |

- **响应结果**: 
  - 成功：订单创建成功，库存扣减成功，undo_log被异步清理
  - 失败：返回错误信息

#### 创建订单接口（AT模式 - 回滚场景）
- **路径**: POST /order/create-at-rollback
- **功能**: 模拟业务异常触发AT模式回滚流程
- **实现方式**: 在库存扣减后，主动抛出运行时异常
- **请求参数**: 同上
- **响应结果**: 
  - 全局事务回滚
  - 订单数据被回滚（通过undo_log前镜像恢复）
  - 库存数据被回滚
  - undo_log记录被清理

### 3.5 验证要点

| 验证项 | 验证方法 | 预期结果 |
|--------|---------|---------|
| 全局事务提交 | 调用create-at接口，检查两个数据库的业务数据 | 订单表新增记录，库存表residue减少、used增加 |
| undo_log生成 | 在业务执行过程中查询undo_log表 | 一阶段提交后，两个数据库都存在对应的undo_log记录 |
| undo_log清理 | 全局事务提交后查询undo_log表 | undo_log记录被异步删除 |
| 全局事务回滚 | 调用create-at-rollback接口 | 业务数据回滚到初始状态 |
| 数据一致性 | 回滚场景下检查数据 | 订单表无新增记录，库存数据未变化 |
| 全局锁机制 | 并发调用接口观察执行情况 | 后发起的事务等待全局锁释放 |

## 4. TCC模式验证设计

### 4.1 TCC模式原理

TCC模式是一种补偿型分布式事务解决方案，需要业务自行实现三个阶段的逻辑：

**Try阶段**：
- 尝试执行业务
- 完成业务资源的检查和预留

**Confirm阶段**：
- 确认执行业务
- 真正执行业务逻辑
- 使用Try阶段预留的资源

**Cancel阶段**：
- 取消执行业务
- 释放Try阶段预留的资源
- 回滚业务数据

### 4.2 业务场景设计

**场景**：创建订单并冻结库存（TCC模式）

```
sequenceDiagram
    participant Client as Swagger UI
    participant OrderService as 订单服务(Service A)
    participant StorageService as 库存服务(Service B)
    participant TC as Seata Server
    
    Client->>OrderService: POST /order/create-tcc
    activate OrderService
    OrderService->>TC: 开启全局事务(XID)
    TC-->>OrderService: 返回XID
    
    Note over OrderService,StorageService: Try阶段
    OrderService->>OrderService: tryCreate(创建订单，状态=INIT)
    OrderService->>TC: 注册分支事务1
    
    OrderService->>StorageService: tryReduce(冻结库存)
    activate StorageService
    StorageService->>StorageService: 增加frozen字段
    StorageService->>StorageService: 减少residue字段
    StorageService->>TC: 注册分支事务2
    StorageService-->>OrderService: 返回成功
    deactivate StorageService
    
    alt 业务执行成功
        Note over OrderService,StorageService: Confirm阶段
        OrderService->>TC: 提交全局事务
        TC->>OrderService: confirmCreate(更新订单状态=SUCCESS)
        TC->>StorageService: confirmReduce(frozen转为used)
        OrderService-->>Client: 返回成功
    else 业务执行失败
        Note over OrderService,StorageService: Cancel阶段
        OrderService->>TC: 回滚全局事务
        TC->>OrderService: cancelCreate(删除订单或标记为CANCEL)
        TC->>StorageService: cancelReduce(释放冻结库存)
        OrderService-->>Client: 返回失败
    end
    deactivate OrderService
```

### 4.3 数据模型

#### 订单表 (t_order_tcc)
| 字段名 | 类型 | 说明 | 约束 |
|--------|------|------|------|
| id | BIGINT | 订单ID | 主键、自增 |
| user_id | VARCHAR(50) | 用户ID | 非空 |
| product_id | VARCHAR(50) | 商品ID | 非空 |
| count | INT | 购买数量 | 非空 |
| amount | DECIMAL(10,2) | 订单金额 | 非空 |
| status | VARCHAR(20) | 订单状态 | 非空，INIT/SUCCESS/CANCEL |

#### 库存表 (t_storage_tcc)
| 字段名 | 类型 | 说明 | 约束 |
|--------|------|------|------|
| id | BIGINT | 库存ID | 主键、自增 |
| product_id | VARCHAR(50) | 商品ID | 非空、唯一 |
| total | INT | 总库存 | 非空 |
| used | INT | 已用库存 | 非空、默认0 |
| frozen | INT | 冻结库存 | 非空、默认0 |
| residue | INT | 剩余可用库存 | 非空 |

**说明**：
- TCC模式下，库存表增加了frozen字段用于记录冻结的库存
- 订单表的status字段用于区分订单的不同状态

### 4.4 TCC接口定义

#### 库存服务TCC接口

**Try接口**：
- 方法签名：`boolean tryReduce(String productId, Integer count)`
- 业务逻辑：
  - 检查剩余库存是否充足（residue >= count）
  - 增加冻结库存（frozen += count）
  - 减少剩余库存（residue -= count）
  - 返回操作结果

**Confirm接口**：
- 方法签名：`boolean confirmReduce(String productId, Integer count)`
- 业务逻辑：
  - 减少冻结库存（frozen -= count）
  - 增加已用库存（used += count）
  - 返回操作结果

**Cancel接口**：
- 方法签名：`boolean cancelReduce(String productId, Integer count)`
- 业务逻辑：
  - 减少冻结库存（frozen -= count）
  - 增加剩余库存（residue += count）
  - 返回操作结果

#### 订单服务TCC接口

**Try接口**：
- 方法签名：`boolean tryCreate(OrderDTO order)`
- 业务逻辑：
  - 创建订单记录，状态设置为INIT
  - 返回操作结果

**Confirm接口**：
- 方法签名：`boolean confirmCreate(OrderDTO order)`
- 业务逻辑：
  - 更新订单状态为SUCCESS
  - 返回操作结果

**Cancel接口**：
- 方法签名：`boolean cancelCreate(OrderDTO order)`
- 业务逻辑：
  - 更新订单状态为CANCEL或删除订单记录
  - 返回操作结果

### 4.5 API接口设计

#### 创建订单接口（TCC模式 - 成功场景）
- **路径**: POST /order/create-tcc
- **功能**: 创建订单并通过TCC模式管理库存，验证TCC正常提交流程
- **请求参数**:

| 参数名 | 类型 | 说明 | 必填 |
|--------|------|------|------|
| userId | String | 用户ID | 是 |
| productId | String | 商品ID | 是 |
| count | Integer | 购买数量 | 是 |
| amount | BigDecimal | 订单金额 | 是 |

- **响应结果**: 
  - Try阶段：订单创建（状态INIT），库存冻结
  - Confirm阶段：订单状态变更为SUCCESS，冻结库存转为已用库存

#### 创建订单接口（TCC模式 - 回滚场景）
- **路径**: POST /order/create-tcc-rollback
- **功能**: 模拟业务异常触发TCC回滚流程
- **实现方式**: 在Try阶段完成后，主动抛出运行时异常
- **请求参数**: 同上
- **响应结果**: 
  - Try阶段：订单创建，库存冻结
  - Cancel阶段：订单状态变更为CANCEL，冻结库存释放回剩余库存

### 4.6 验证要点

| 验证项 | 验证方法 | 预期结果 |
|--------|---------|---------|
| Try阶段资源预留 | 调用create-tcc接口，在Try完成后查询数据 | 订单status=INIT，库存frozen增加、residue减少 |
| Confirm阶段资源确认 | 全局事务提交后查询数据 | 订单status=SUCCESS，库存frozen减少、used增加 |
| Cancel阶段资源释放 | 调用create-tcc-rollback接口后查询数据 | 订单status=CANCEL，库存frozen减少、residue增加 |
| 幂等性验证 | 重复调用Confirm或Cancel接口 | 操作幂等，数据不会重复处理 |
| 空回滚验证 | Try阶段未执行，直接调用Cancel | Cancel能够正确处理空回滚 |
| 悬挂验证 | Cancel执行后Try才执行 | Try阶段能够识别并拒绝执行 |

## 5. 服务间调用设计

### 5.1 调用关系

```
graph LR
    A[seata-service-a<br/>订单服务]
    B[seata-service-b<br/>库存服务]
    
    A -->|RestTemplate/OpenFeign| B
    A -.->|传递XID| B
```

### 5.2 XID传播机制

**XID传播方式**：
- Seata通过拦截器自动在HTTP Header中传递XID
- Header名称：`TX_XID`
- 传播过程：
  1. Service A开启全局事务，获得XID
  2. Service A调用Service B时，Seata拦截器自动将XID放入HTTP Header
  3. Service B接收到请求，Seata拦截器自动从HTTP Header中提取XID
  4. Service B的分支事务自动关联到该XID

### 5.3 服务调用配置

| 配置项 | 说明 | 值 |
|--------|------|-----|
| 调用方式 | 建议使用OpenFeign或RestTemplate | OpenFeign |
| 超时时间 | 避免因超时导致事务异常 | 建议30秒 |
| 重试策略 | TCC模式需要保证幂等性 | 建议关闭自动重试 |
| 负载均衡 | 如有多实例部署 | Ribbon默认策略 |

## 6. 配置设计

### 6.1 Seata Server配置

#### 存储模式
- **推荐模式**: DB模式（生产环境）
- **测试模式**: File模式（快速验证）

#### 配置中心和注册中心
| 组件 | 方案 | 说明 |
|------|------|------|
| 注册中心 | Nacos / 直连 | 测试环境可使用直连，生产环境建议Nacos |
| 配置中心 | Nacos / File | 测试环境可使用File，生产环境建议Nacos |

### 6.2 客户端配置（Service A & B）

#### 核心配置项

| 配置项 | 说明 | 示例值 |
|--------|------|--------|
| seata.enabled | 是否启用Seata | true |
| seata.application-id | 应用ID | seata-service-a |
| seata.tx-service-group | 事务分组 | my_test_tx_group |
| seata.service.vgroup-mapping | 事务分组与集群映射 | my_test_tx_group: default |
| seata.service.grouplist | Seata Server地址（直连模式） | 127.0.0.1:8091 |
| seata.data-source-proxy-mode | 数据源代理模式 | AT |

#### AT模式专属配置

| 配置项 | 说明 | 推荐值 |
|--------|------|--------|
| seata.client.rm.lock.retry-times | 获取全局锁重试次数 | 30 |
| seata.client.rm.lock.retry-interval | 获取全局锁重试间隔(ms) | 10 |
| seata.client.rm.report-retry-count | 一阶段结果上报重试次数 | 5 |

#### TCC模式专属配置

| 配置项 | 说明 | 推荐值 |
|--------|------|--------|
| seata.client.rm.tcc.action-interceptor-order | TCC拦截器顺序 | -2147482648 |

### 6.3 数据库配置

#### Service A（订单服务）
- 数据库名：seata_order
- 连接地址：jdbc:mysql://localhost:3306/seata_order
- 需要创建的表：t_order（AT模式）、t_order_tcc（TCC模式）、undo_log（AT模式必需）

#### Service B（库存服务）
- 数据库名：seata_storage
- 连接地址：jdbc:mysql://localhost:3306/seata_storage
- 需要创建的表：t_storage（AT模式）、t_storage_tcc（TCC模式）、undo_log（AT模式必需）

## 7. 测试设计

### 7.1 测试环境准备

#### 环境组件清单
| 组件 | 部署方式 | 验证方式 |
|------|---------|---------|
| MySQL数据库 | 本地安装或Docker | 连接测试，创建数据库 |
| Seata Server | 下载解压启动 | 访问控制台（如有） |
| seata-service-a | IDEA启动或jar包 | 访问Swagger UI |
| seata-service-b | IDEA启动或jar包 | 健康检查接口 |

#### 数据初始化
- 创建测试商品库存数据
- 示例：product_id="P001", total=100, used=0, residue=100

#### 7.1.1 MySQL 5.7 Docker部署

**拉取镜像**：
```bash
docker pull mysql:5.7
```

**启动MySQL容器**：
```bash
docker run -d \
  --name seata-mysql \
  -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=root123 \
  -e MYSQL_CHARACTER_SET_SERVER=utf8mb4 \
  -e MYSQL_COLLATION_SERVER=utf8mb4_general_ci \
  -e TZ=Asia/Shanghai \
  -v seata-mysql-data:/var/lib/mysql \
  mysql:5.7 \
  --character-set-server=utf8mb4 \
  --collation-server=utf8mb4_general_ci
```

**参数说明**：
| 参数 | 说明 | 值 |
|------|------|-----|
| --name | 容器名称 | seata-mysql |
| -p | 端口映射 | 3306:3306 |
| MYSQL_ROOT_PASSWORD | root用户密码 | root123 |
| MYSQL_CHARACTER_SET_SERVER | 字符集 | utf8mb4 |
| TZ | 时区 | Asia/Shanghai |
| -v | 数据持久化卷 | seata-mysql-data |

**验证容器运行**：
```bash
# 查看容器状态
docker ps | grep seata-mysql

# 查看容器日志
docker logs seata-mysql

# 进入容器
docker exec -it seata-mysql bash

# 登录MySQL
mysql -uroot -proot123
```

**常用容器管理命令**：
```bash
# 启动容器
docker start seata-mysql

# 停止容器
docker stop seata-mysql

# 重启容器
docker restart seata-mysql

# 删除容器（需先停止）
docker rm seata-mysql

# 查看容器详细信息
docker inspect seata-mysql
```

#### 7.1.2 数据库初始化

**连接MySQL**：
```bash
# 使用docker exec连接
docker exec -it seata-mysql mysql -uroot -proot123

# 或使用MySQL客户端工具连接
# Host: localhost
# Port: 3306
# User: root
# Password: root123
```

**创建数据库**：
```sql
-- 创建订单数据库
CREATE DATABASE seata_order DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

-- 创建库存数据库
CREATE DATABASE seata_storage DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

-- 验证数据库创建
SHOW DATABASES;
```

**创建应用用户（可选）**：
```sql
-- 创建应用用户
CREATE USER 'seata'@'%' IDENTIFIED BY 'seata123';

-- 授予权限
GRANT ALL PRIVILEGES ON seata_order.* TO 'seata'@'%';
GRANT ALL PRIVILEGES ON seata_storage.* TO 'seata'@'%';

-- 刷新权限
FLUSH PRIVILEGES;
```

#### 7.1.3 建表SQL语句

**订单数据库（seata_order）建表语句**：

```
USE seata_order;

-- AT模式订单表
CREATE TABLE t_order (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  user_id VARCHAR(50) NOT NULL COMMENT '用户ID',
  product_id VARCHAR(50) NOT NULL COMMENT '商品ID',
  count INT NOT NULL COMMENT '购买数量',
  amount DECIMAL(10,2) NOT NULL COMMENT '订单金额',
  status VARCHAR(20) NOT NULL DEFAULT 'INIT' COMMENT '订单状态:INIT-初始化,SUCCESS-成功,CANCEL-取消',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_user_id (user_id),
  KEY idx_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AT模式订单表';

-- TCC模式订单表
CREATE TABLE t_order_tcc (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  user_id VARCHAR(50) NOT NULL COMMENT '用户ID',
  product_id VARCHAR(50) NOT NULL COMMENT '商品ID',
  count INT NOT NULL COMMENT '购买数量',
  amount DECIMAL(10,2) NOT NULL COMMENT '订单金额',
  status VARCHAR(20) NOT NULL DEFAULT 'INIT' COMMENT '订单状态:INIT-初始化,SUCCESS-成功,CANCEL-取消',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_user_id (user_id),
  KEY idx_product_id (product_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='TCC模式订单表';

-- AT模式回滚日志表（Seata必需）
CREATE TABLE undo_log (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  branch_id BIGINT NOT NULL COMMENT '分支事务ID',
  xid VARCHAR(100) NOT NULL COMMENT '全局事务ID',
  context VARCHAR(128) NOT NULL COMMENT '上下文',
  rollback_info LONGBLOB NOT NULL COMMENT '回滚信息',
  log_status INT NOT NULL COMMENT '状态:0-正常,1-已完成',
  log_created DATETIME NOT NULL COMMENT '创建时间',
  log_modified DATETIME NOT NULL COMMENT '修改时间',
  PRIMARY KEY (id),
  UNIQUE KEY ux_undo_log (xid, branch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AT模式回滚日志表';
```

**库存数据库（seata_storage）建表语句**：

```
USE seata_storage;

-- AT模式库存表
CREATE TABLE t_storage (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '库存ID',
  product_id VARCHAR(50) NOT NULL COMMENT '商品ID',
  total INT NOT NULL COMMENT '总库存',
  used INT NOT NULL DEFAULT 0 COMMENT '已用库存',
  residue INT NOT NULL COMMENT '剩余可用库存',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AT模式库存表';

-- TCC模式库存表
CREATE TABLE t_storage_tcc (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '库存ID',
  product_id VARCHAR(50) NOT NULL COMMENT '商品ID',
  total INT NOT NULL COMMENT '总库存',
  used INT NOT NULL DEFAULT 0 COMMENT '已用库存',
  frozen INT NOT NULL DEFAULT 0 COMMENT '冻结库存（TCC专用）',
  residue INT NOT NULL COMMENT '剩余可用库存',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='TCC模式库存表';

-- AT模式回滚日志表（Seata必需）
CREATE TABLE undo_log (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  branch_id BIGINT NOT NULL COMMENT '分支事务ID',
  xid VARCHAR(100) NOT NULL COMMENT '全局事务ID',
  context VARCHAR(128) NOT NULL COMMENT '上下文',
  rollback_info LONGBLOB NOT NULL COMMENT '回滚信息',
  log_status INT NOT NULL COMMENT '状态:0-正常,1-已完成',
  log_created DATETIME NOT NULL COMMENT '创建时间',
  log_modified DATETIME NOT NULL COMMENT '修改时间',
  PRIMARY KEY (id),
  UNIQUE KEY ux_undo_log (xid, branch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AT模式回滚日志表';
```

#### 7.1.4 初始化测试数据

**AT模式测试数据**：
```sql
USE seata_storage;

-- 插入AT模式库存测试数据
INSERT INTO t_storage (product_id, total, used, residue) VALUES
('P001', 100, 0, 100),
('P003', 5, 0, 5);

-- 验证数据
SELECT * FROM t_storage;
```

**TCC模式测试数据**：
```sql
USE seata_storage;

-- 插入TCC模式库存测试数据
INSERT INTO t_storage_tcc (product_id, total, used, frozen, residue) VALUES
('P002', 100, 0, 0, 100),
('P004', 10, 0, 0, 10);

-- 验证数据
SELECT * FROM t_storage_tcc;
```

**验证表结构**：
```sql
-- 查看订单数据库表
USE seata_order;
SHOW TABLES;
DESC t_order;
DESC t_order_tcc;
DESC undo_log;

-- 查看库存数据库表
USE seata_storage;
SHOW TABLES;
DESC t_storage;
DESC t_storage_tcc;
DESC undo_log;
```

### 7.2 AT模式测试场景

#### 场景1：AT模式正常提交流程验证

**业务场景**：用户下单购买商品，扣减库存成功，订单创建成功

**测试配置**：
| 项目 | 内容 |
|------|------|
| 测试目标 | 验证AT模式两阶段提交正常流程，包括undo_log的生成与清理 |
| 前置条件 | 1. 商品P001库存充足（residue >= 10）<br/>2. 数据库连接正常<br/>3. Seata Server运行正常 |
| 测试数据 | userId=U001, productId=P001, count=10, amount=100.00 |

**执行步骤**：
1. 打开Swagger UI：http://localhost:8081/swagger-ui/index.html
2. 找到"AT模式测试"分组下的POST /order/create-at接口
3. 点击"Try it out"按钮
4. 填写请求参数（JSON格式）
5. 点击"Execute"发起请求
6. **立即查询undo_log表**（在一阶段提交后、二阶段完成前）：
   - 查询seata_order库：SELECT * FROM undo_log ORDER BY id DESC LIMIT 1
   - 查询seata_storage库：SELECT * FROM undo_log ORDER BY id DESC LIMIT 1
7. 等待2-3秒后再次查询undo_log表
8. 查询业务数据变化

**验证检查点**：

| 检查项 | 验证方法 | 预期结果 | 实际结果 |
|--------|---------|---------|----------|
| 接口响应 | 查看Swagger UI响应 | HTTP 200，返回订单ID | 待填写 |
| 订单创建 | SELECT * FROM t_order WHERE user_id='U001' | 新增1条记录，status='INIT'或'SUCCESS' | 待填写 |
| 库存扣减 | SELECT * FROM t_storage WHERE product_id='P001' | residue减少10，used增加10 | 待填写 |
| undo_log生成 | 步骤6的查询结果 | 两个库各有1条undo_log记录 | 待填写 |
| undo_log清理 | 步骤7的查询结果 | 两个库的undo_log记录已被删除 | 待填写 |
| XID传播 | 查看日志中的XID | 订单服务和库存服务的XID相同 | 待填写 |
| 事务状态 | 观察Seata Server日志 | 全局事务状态为Committed | 待填写 |

**数据快照对比**：

执行前库存状态：
```
product_id: P001
total: 100
used: 0
residue: 100
```

执行后库存状态：
```
product_id: P001
total: 100
used: 10
residue: 90
```

**异常处理**：
- 如果接口返回500错误，检查Seata Server是否启动
- 如果undo_log未清理，检查Seata异步清理配置
- 如果库存未扣减，检查XID是否正确传播

#### 场景2：AT模式回滚流程验证

**业务场景**：用户下单后系统异常，触发全局事务回滚

**测试配置**：
| 项目 | 内容 |
|------|------|
| 测试目标 | 验证AT模式二阶段回滚流程，包括undo_log的反向补偿 |
| 前置条件 | 1. 商品P001库存充足<br/>2. 记录当前库存数据作为基准 |
| 测试数据 | userId=U002, productId=P001, count=5, amount=50.00 |
| 回滚触发方式 | 接口内部主动抛出RuntimeException |

**执行步骤**：
1. **记录执行前数据**：
   - 查询t_order表记录数：SELECT COUNT(*) FROM t_order
   - 查询P001库存：SELECT * FROM t_storage WHERE product_id='P001'
2. 通过Swagger UI调用POST /order/create-at-rollback接口
3. 填写请求参数
4. 观察响应结果（预期返回异常）
5. **立即查询undo_log表**（观察回滚过程）
6. 等待回滚完成后查询业务数据
7. 对比执行前后的数据

**验证检查点**：

| 检查项 | 验证方法 | 预期结果 | 实际结果 |
|--------|---------|---------|----------|
| 接口响应 | 查看响应状态码 | HTTP 500或自定义异常码 | 待填写 |
| 异常信息 | 查看响应body | 包含"模拟异常"等关键字 | 待填写 |
| 订单回滚 | 对比执行前后t_order记录数 | 记录数不变，无新增订单 | 待填写 |
| 库存回滚 | 对比执行前后库存数据 | residue和used与执行前完全一致 | 待填写 |
| undo_log清理 | 查询undo_log表 | 回滚完成后，undo_log记录被清理 | 待填写 |
| 事务状态 | 观察Seata Server日志 | 全局事务状态为Rollbacked | 待填写 |

**回滚验证SQL**：
```sql
-- 验证订单未新增
SELECT * FROM t_order WHERE user_id='U002' AND product_id='P001';
-- 预期：查询结果为空

-- 验证库存未变化
SELECT * FROM t_storage WHERE product_id='P001';
-- 预期：与执行前数据完全一致
```

#### 场景3：AT模式并发控制与全局锁验证

**业务场景**：两个用户同时购买同一商品，验证全局锁防止脏写

**测试配置**：
| 项目 | 内容 |
|------|------|
| 测试目标 | 验证全局锁机制，确保并发事务串行执行，防止脏写 |
| 前置条件 | 1. 商品P001库存充足（residue >= 20）<br/>2. 在订单服务的业务方法中增加5秒延迟 |
| 测试数据 | 事务1：userId=U005, productId=P001, count=8<br/>事务2：userId=U006, productId=P001, count=12 |

**执行步骤**：
1. 记录初始库存：SELECT * FROM t_storage WHERE product_id='P001'
2. 打开两个Swagger UI标签页
3. 在两个标签页中同时填写测试数据（事务1和事务2）
4. **几乎同时点击Execute**（间隔小于1秒）
5. 观察两个请求的响应时间差异
6. 查看Seata日志中的全局锁获取记录
7. 等待两个事务都完成后，查询最终库存

**验证检查点**：

| 检查项 | 验证方法 | 预期结果 | 实际结果 |
|--------|---------|---------|----------|
| 执行顺序 | 对比两个请求的响应时间 | 相差约5秒（延迟时间） | 待填写 |
| 全局锁等待 | 查看日志关键字"global lock" | 第二个事务等待全局锁 | 待填写 |
| 库存准确性 | 查询最终库存 | used增加20（8+12），residue减少20 | 待填写 |
| 订单数量 | 统计新增订单数 | t_order表新增2条记录 | 待填写 |
| 无脏写 | 检查是否有数据不一致 | 库存变化=订单数量之和 | 待填写 |

**日志观察要点**：
```
关键日志1：First transaction got global lock
关键日志2：Second transaction waiting for global lock
关键日志3：Global lock released
关键日志4：Second transaction got global lock
```

**性能数据记录**：
- 事务1响应时间：___秒
- 事务2响应时间：___秒
- 全局锁等待时长：___秒

#### 场景4：AT模式undo_log镜像数据验证

**业务场景**：深入验证AT模式的前后镜像机制

**测试配置**：
| 项目 | 内容 |
|------|------|
| 测试目标 | 验证undo_log中存储的前后镜像数据是否正确 |
| 前置条件 | 商品P001库存充足 |
| 测试数据 | userId=U007, productId=P001, count=3, amount=30.00 |

**执行步骤**：
1. 在订单服务业务方法中增加长时间延迟（如30秒）
2. 发起请求
3. 在延迟期间，快速查询undo_log表
4. 提取rollback_info字段内容（BLOB类型，需要工具解析）
5. 分析前后镜像数据的正确性
6. 等待事务完成

**undo_log内容验证**：

查询SQL：
```sql
SELECT branch_id, xid, log_status, log_created 
FROM undo_log 
ORDER BY log_created DESC LIMIT 1;
```

前镜像验证要点：
- beforeImage应包含操作前的库存数据
- 字段值应与执行前的数据库记录一致

后镜像验证要点：
- afterImage应包含操作后的库存数据
- residue应减少对应数量
- used应增加对应数量

### 7.3 TCC模式测试场景

#### 场景5：TCC模式Try-Confirm正常流程验证

**业务场景**：用户下单购买商品，通过TCC模式管理库存，正常提交

**测试配置**：
| 项目 | 内容 |
|------|------|
| 测试目标 | 验证TCC三阶段中的Try-Confirm完整流程，观察frozen字段的状态变化 |
| 前置条件 | 1. 商品P002库存充足（residue >= 15）<br/>2. 清空t_order_tcc表历史数据 |
| 测试数据 | userId=U003, productId=P002, count=15, amount=150.00 |

**执行步骤**：
1. **记录初始状态**：
   ```sql
   SELECT * FROM t_storage_tcc WHERE product_id='P002';
   -- 记录initial_residue, initial_used, initial_frozen
   ```
2. **在Try阶段增加断点或延迟**（用于观察中间状态）
3. 通过Swagger UI调用POST /order/create-tcc接口
4. **Try阶段完成后立即查询**：
   - 订单状态：SELECT status FROM t_order_tcc ORDER BY id DESC LIMIT 1
   - 库存状态：SELECT frozen, residue, used FROM t_storage_tcc WHERE product_id='P002'
5. 等待Confirm阶段完成
6. **Confirm完成后查询最终状态**
7. 对比三个阶段的数据变化

**三阶段数据状态验证**：

| 阶段 | 订单状态 | frozen | residue | used | 说明 |
|------|---------|--------|---------|------|------|
| 初始状态 | 无记录 | 0 | 100 | 0 | 执行前 |
| Try完成 | INIT | 15 | 85 | 0 | 库存已冻结 |
| Confirm完成 | SUCCESS | 0 | 85 | 15 | frozen转为used |

**详细验证检查点**：

| 检查项 | 验证SQL | 预期结果 | 实际结果 |
|--------|---------|---------|----------|
| Try阶段-订单创建 | SELECT * FROM t_order_tcc WHERE user_id='U003' | status='INIT' | 待填写 |
| Try阶段-库存冻结 | SELECT frozen FROM t_storage_tcc WHERE product_id='P002' | frozen=15 | 待填写 |
| Try阶段-可用库存减少 | SELECT residue FROM t_storage_tcc WHERE product_id='P002' | residue=85 | 待填写 |
| Confirm阶段-订单确认 | SELECT status FROM t_order_tcc WHERE user_id='U003' | status='SUCCESS' | 待填写 |
| Confirm阶段-冻结释放 | SELECT frozen FROM t_storage_tcc WHERE product_id='P002' | frozen=0 | 待填写 |
| Confirm阶段-已用增加 | SELECT used FROM t_storage_tcc WHERE product_id='P002' | used=15 | 待填写 |
| 总库存不变 | SELECT total FROM t_storage_tcc WHERE product_id='P002' | total=100（不变） | 待填写 |

**业务规则验证**：
```
验证公式：total = used + frozen + residue

Try阶段：100 = 0 + 15 + 85 ✓
Confirm阶段：100 = 15 + 0 + 85 ✓
```

#### 场景6：TCC模式Try-Cancel回滚流程验证

**业务场景**：用户下单后触发异常，TCC事务回滚，释放冻结库存

**测试配置**：
| 项目 | 内容 |
|------|------|
| 测试目标 | 验证TCC三阶段中的Try-Cancel回滚流程，确保资源正确释放 |
| 前置条件 | 商品P002库存充足 |
| 测试数据 | userId=U004, productId=P002, count=8, amount=80.00 |
| 回滚触发 | Try阶段完成后抛出异常 |

**执行步骤**：
1. 记录执行前库存状态
2. 在Try和Confirm之间的逻辑中增加断点
3. 调用POST /order/create-tcc-rollback接口
4. **Try完成后查询**（此时应该已冻结库存）
5. 触发异常，进入Cancel阶段
6. **Cancel完成后查询**（验证库存是否恢复）
7. 对比执行前后数据是否一致

**Cancel阶段验证重点**：

| 检查项 | 验证SQL | 预期结果 | 实际结果 |
|--------|---------|---------|----------|
| 订单状态 | SELECT status FROM t_order_tcc WHERE user_id='U004' | status='CANCEL'或无记录 | 待填写 |
| 冻结库存释放 | SELECT frozen FROM t_storage_tcc WHERE product_id='P002' | frozen=0（回到初始） | 待填写 |
| 可用库存恢复 | SELECT residue FROM t_storage_tcc WHERE product_id='P002' | residue=100（回到初始） | 待填写 |
| 已用库存不变 | SELECT used FROM t_storage_tcc WHERE product_id='P002' | used=0（保持初始） | 待填写 |
| 数据完整性 | 对比执行前后所有字段 | 完全一致 | 待填写 |

**状态流转图验证**：
```
初始: residue=100, frozen=0, used=0
  ↓ Try
Try后: residue=92, frozen=8, used=0
  ↓ Cancel触发
Cancel后: residue=100, frozen=0, used=0 (完全恢复)
```

#### 场景7：TCC幂等性验证

**业务场景**：模拟网络重试导致的重复调用，验证幂等性保护

**测试配置**：
| 项目 | 内容 |
|------|------|
| 测试目标 | 验证Try、Confirm、Cancel三个方法的幂等性实现 |
| 前置条件 | 1. 已成功执行一次TCC事务<br/>2. 记录该事务的XID和BranchID |
| 测试重点 | 同一个XID的重复Confirm/Cancel不会重复扣减库存 |

**子场景7.1：Confirm幂等性**

执行步骤：
1. 正常执行一次TCC提交流程，记录XID
2. 在日志中找到对应的BranchID
3. 模拟Seata TC重复发送Confirm指令（需要修改代码或使用调试工具）
4. 观察第二次Confirm是否重复扣减库存

验证方法：
- 方案一：在Confirm方法中增加日志，观察幂等判断逻辑
- 方案二：查询库存数据，确认只扣减一次
- 方案三：维护事务执行记录表，检查Confirm是否被标记为已执行

**子场景7.2：Cancel幂等性**

执行步骤：
1. 执行一次TCC回滚流程
2. 模拟重复发送Cancel指令
3. 验证库存不会被重复恢复（导致库存超出total）

**幂等性实现方案建议**：

| 方案 | 实现方式 | 优点 | 缺点 |
|------|---------|------|------|
| 状态机 | 记录事务状态，已完成的不再处理 | 简单直观 | 需要额外存储 |
| 数据库唯一索引 | 使用XID+BranchID作为唯一键 | 数据库层面保证 | 需要设计额外表 |
| 版本号 | 更新时检查版本号 | 通用性强 | 需要修改表结构 |
| 业务判断 | 根据frozen/used等字段判断 | 无额外存储 | 逻辑复杂 |

#### 场景8：TCC空回滚验证

**业务场景**：Try阶段因网络问题未执行，但TC发送了Cancel指令

**测试配置**：
| 项目 | 内容 |
|------|------|
| 测试目标 | 验证Cancel方法能够处理Try未执行的场景 |
| 前置条件 | 模拟Try阶段超时或失败 |
| 验证重点 | Cancel不应抛出异常，应优雅处理空回滚 |

**执行步骤**：
1. 在Try方法开始处直接返回false或抛出异常（模拟Try失败）
2. Seata TC会发送Cancel指令
3. 观察Cancel方法的处理逻辑
4. 验证数据库状态未被破坏

**验证检查点**：
- Cancel方法未抛出异常
- 库存数据保持初始状态
- 订单记录不存在或状态为CANCEL
- 日志显示"空回滚处理"相关信息

**空回滚处理逻辑建议**：
```
伪代码：
if (订单不存在 || 订单状态 != INIT) {
    // 说明Try未执行或已被Cancel
    记录空回滚日志
    return true  // 返回成功，避免TC重试
}
正常执行Cancel逻辑
```

#### 场景9：TCC悬挂问题验证

**业务场景**：Cancel先执行，Try后执行（由于网络延迟等原因）

**测试配置**：
| 项目 | 内容 |
|------|------|
| 测试目标 | 验证Try方法能够识别悬挂场景，拒绝执行 |
| 前置条件 | 模拟Cancel先于Try到达 |
| 验证重点 | Try执行时应检查Cancel是否已执行，若是则拒绝 |

**执行步骤**：
1. 在Try方法中增加长延迟（如10秒）
2. 发起事务并立即触发回滚
3. Cancel可能先于Try执行完成
4. 观察Try方法的防悬挂逻辑

**防悬挂实现方案**：
- 方案一：维护事务状态表，Try执行前检查是否已Cancel
- 方案二：使用分布式锁，确保Try和Cancel的执行顺序
- 方案三：在业务记录中标记Cancel已执行，Try检查该标记

**验证检查点**：
- Try方法检测到悬挂场景
- Try返回失败或拒绝执行
- 库存数据正确（未被Try冻结）
- 日志显示"检测到悬挂"相关信息

### 7.4 异常场景测试

#### 场景10：库存不足异常处理验证

**业务场景**：用户购买数量超过可用库存，触发业务异常

**测试配置**：
| 项目 | 内容 |
|------|------|
| 测试目标 | 验证业务异常触发的全局事务回滚，确保数据一致性 |
| 前置条件 | 准备库存不足的商品P003（residue=5） |
| 测试数据 | AT模式：userId=U008, productId=P003, count=10<br/>TCC模式：userId=U009, productId=P004, count=15（P004库存仅10） |

**AT模式库存不足测试**：

执行步骤：
1. 初始化P003库存：total=5, used=0, residue=5
2. 调用POST /order/create-at接口，请求购买10件
3. 观察库存服务的业务逻辑校验
4. 查看异常信息和事务回滚情况

验证检查点：
| 检查项 | 验证方法 | 预期结果 | 实际结果 |
|--------|---------|---------|----------|
| 业务校验 | 查看响应信息 | 包含"库存不足"等提示 | 待填写 |
| 异常抛出时机 | 观察日志 | 在库存扣减时抛出异常 | 待填写 |
| 订单未创建 | SELECT COUNT(*) FROM t_order WHERE user_id='U008' | 返回0 | 待填写 |
| 库存未变化 | SELECT * FROM t_storage WHERE product_id='P003' | residue=5保持不变 | 待填写 |
| 全局事务回滚 | 查看Seata日志 | 显示"Rollback global transaction" | 待填写 |

**TCC模式库存不足测试**：

执行步骤：
1. 初始化P004库存：total=10, residue=10, frozen=0, used=0
2. 调用POST /order/create-tcc接口，请求购买15件
3. Try阶段应检测库存不足
4. 验证Try返回失败，不进入Confirm阶段

验证检查点：
| 检查项 | 预期结果 | 实际结果 |
|--------|---------|----------|
| Try阶段校验 | residue < count，返回false | 待填写 |
| frozen未增加 | frozen=0保持不变 | 待填写 |
| 订单未创建或状态CANCEL | 无记录或status='CANCEL' | 待填写 |
| 无Confirm日志 | 日志中无"confirmReduce"调用 | 待填写 |

**边界值测试**：
```
测试数据组：
1. 请求数量 = 剩余库存（count=5, residue=5）
   预期：成功
   
2. 请求数量 = 剩余库存 + 1（count=6, residue=5）
   预期：失败，提示库存不足
   
3. 请求数量 = 0
   预期：参数校验失败
   
4. 请求数量 < 0
   预期：参数校验失败
```

#### 场景11：服务调用超时处理验证

**业务场景**：库存服务响应超时，订单服务触发超时回滚

**测试配置**：
| 项目 | 内容 |
|------|------|
| 测试目标 | 验证服务间调用超时时的事务回滚机制 |
| 前置条件 | 配置Feign或RestTemplate超时时间为5秒 |
| 模拟方式 | 在库存服务的扣减方法中增加10秒延迟 |

**执行步骤**：
1. 在库存服务的reduce方法开始处增加延迟：
   - Thread.sleep(10000) // 10秒延迟
2. 配置订单服务的调用超时：
   - feign.client.config.default.connectTimeout=5000
   - feign.client.config.default.readTimeout=5000
3. 调用POST /order/create-at接口
4. 观察订单服务在5秒后抛出超时异常
5. 验证全局事务回滚

**验证检查点**：

| 检查项 | 验证方法 | 预期结果 | 实际结果 |
|--------|---------|---------|----------|
| 超时异常 | 查看响应信息 | 包含"timeout"或"Read timed out" | 待填写 |
| 订单服务分支回滚 | 查询t_order表 | 订单记录已回滚，不存在 | 待填写 |
| 库存服务分支回滚 | 查询t_storage表 | 库存数据未变化 | 待填写 |
| undo_log处理 | 查询undo_log表 | 即使超时，undo_log也被正确清理 | 待填写 |
| Seata TC协调 | 查看TC日志 | TC接收到超时分支的回滚请求 | 待填写 |

**超时场景矩阵**：

| 超时发生位置 | 影响范围 | 预期行为 |
|-------------|---------|----------|
| 订单插入前 | 仅订单服务 | 直接返回异常，无需回滚 |
| 订单插入后、调用库存前 | 订单服务 | 回滚已插入的订单 |
| 调用库存服务时 | 订单+库存服务 | 两个分支都回滚 |
| 库存扣减中 | 订单+库存服务 | 库存服务可能已提交，需undo_log回滚 |

#### 场景12：数据库连接异常处理

**业务场景**：执行过程中数据库连接断开或异常

**测试配置**：
| 项目 | 内容 |
|------|------|
| 测试目标 | 验证数据库异常时的事务处理 |
| 模拟方式 | 在事务执行过程中手动停止MySQL服务 |

**执行步骤**：
1. 在订单服务的业务方法中增加延迟
2. 发起请求
3. 在延迟期间，停止MySQL服务或断开数据库连接
4. 观察异常处理和事务状态

**验证要点**：
- 应用能够捕获数据库连接异常
- 全局事务标记为失败
- Seata TC记录异常状态
- 数据库恢复后，重启服务数据一致

#### 场景13：Seata Server宕机恢复

**业务场景**：事务执行过程中Seata Server宕机

**测试配置**：
| 项目 | 内容 |
|------|------|
| 测试目标 | 验证TC宕机时的容错能力 |
| 前提 | Seata Server使用DB存储模式（保证数据持久化） |

**执行步骤**：
1. 确保Seata Server使用DB模式
2. 在事务执行过程中关闭Seata Server
3. 观察客户端行为（应该报错）
4. 重启Seata Server
5. 检查未完成事务的状态
6. 验证数据一致性

**恢复验证**：
- File模式：事务状态丢失，需要人工检查数据
- DB模式：TC重启后可恢复事务状态，继续处理未完成事务

#### 场景14：并发压力测试

**业务场景**：多用户高并发下单，验证系统稳定性

**测试配置**：
| 项目 | 内容 |
|------|------|
| 测试目标 | 验证高并发场景下的事务正确性和性能 |
| 工具 | JMeter或自定义脚本 |
| 并发数 | 10/50/100三个级别 |
| 测试时长 | 每个级别持续1分钟 |

**测试方案**：

初始库存：P001=1000件

并发请求：100个用户，每人购买5件

预期结果：
- 成功订单：100个
- 最终库存：residue=500, used=500
- 无脏数据，无死锁

**性能指标记录**：

| 并发数 | 平均响应时间(ms) | 成功率 | TPS | 全局锁冲突次数 |
|--------|----------------|--------|-----|---------------|
| 10 | | | | |
| 50 | | | | |
| 100 | | | | |

**验证SQL**：
```sql
-- 验证订单总数
SELECT COUNT(*) FROM t_order;
-- 预期：100

-- 验证库存准确性
SELECT SUM(count) FROM t_order;
-- 预期：500

SELECT used FROM t_storage WHERE product_id='P001';
-- 预期：500

-- 检查数据一致性
SELECT total - used - residue AS diff 
FROM t_storage WHERE product_id='P001';
-- 预期：0（TCC模式检查frozen字段）
```

### 7.5 Swagger UI测试指南

#### 访问地址
- Service A: http://localhost:8081/swagger-ui/index.html
- Service B: http://localhost:8082/swagger-ui/index.html

#### API分组展示
- **AT模式测试组**：
  - POST /order/create-at
  - POST /order/create-at-rollback
  
- **TCC模式测试组**：
  - POST /order/create-tcc
  - POST /order/create-tcc-rollback

- **查询接口组**：
  - GET /order/{id}
  - GET /storage/{productId}

#### 测试数据准备
提供默认测试数据集：
- 用户ID：U001, U002, U003, U004
- 商品ID：P001（初始库存100）, P002（初始库存100）, P003（初始库存5）
- 订单金额：根据count计算，单价假设为10元

## 8. 关键技术点

### 8.1 数据源代理

**AT模式**：
- Seata会自动代理DataSource
- 在业务SQL执行前后生成前后镜像
- 自动管理undo_log的写入和清理

**TCC模式**：
- 不需要数据源代理
- 直接使用普通的DataSource
- 业务逻辑完全由开发者控制

### 8.2 注解使用

#### 全局事务注解
```
@GlobalTransactional(name = "事务名称", rollbackFor = Exception.class)
```
- 使用位置：Service A的业务方法上（事务发起方）
- 作用：开启全局事务，向TC注册全局事务

#### TCC注解
```
@LocalTCC
```
- 使用位置：TCC接口上（标记为TCC资源）
- 需配合：
  - @TwoPhaseBusinessAction：标记Try方法
  - @Compensate：标记Cancel方法
  - commitMethod：指定Confirm方法名

### 8.3 事务隔离级别

| 模式 | 默认全局隔离级别 | 说明 |
|------|-----------------|------|
| AT | 读未提交 | 若需要读已提交，需使用SELECT FOR UPDATE |
| TCC | 由业务实现决定 | 开发者自行控制资源锁定 |

### 8.4 异常处理策略

**回滚规则**：
- 默认：RuntimeException及其子类触发回滚
- 可配置：通过rollbackFor和noRollbackFor指定异常类型

**重试机制**：
- TC通知RM回滚失败时会重试
- 重试次数和间隔可配置
- 业务需保证回滚操作幂等

## 9. 验证指标

### 9.1 功能指标

| 指标项 | AT模式 | TCC模式 |
|--------|--------|---------|
| 正常提交成功率 | 100% | 100% |
| 异常回滚成功率 | 100% | 100% |
| 数据一致性 | 无脏数据 | 无脏数据 |
| undo_log清理 | 自动清理 | 不适用 |
| 幂等性 | 框架保证 | 业务实现 |

### 9.2 性能观察点

| 观察点 | 说明 | 监控方式 |
|--------|------|---------|
| 全局锁等待时间 | AT模式下并发事务的等待时长 | 日志记录 |
| 二阶段提交耗时 | AT模式异步删除undo_log的耗时 | 数据库查询 |
| TCC三阶段耗时 | Try、Confirm、Cancel各阶段执行时间 | 方法计时 |
| 跨服务调用延迟 | Service A调用Service B的网络延迟 | 链路追踪 |

### 9.3 验证完成标准

所有测试用例通过，且满足以下条件：
1. AT模式和TCC模式的正常提交、异常回滚场景均验证通过
2. 数据库数据一致性得到保证，无残留脏数据
3. 全局锁机制有效防止并发脏写
4. TCC模式的幂等性、空回滚、悬挂等边界情况处理正确
5. Swagger UI能够正常调用所有测试接口
6. 日志输出清晰，能够追踪事务执行过程