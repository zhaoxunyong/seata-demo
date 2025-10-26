# Seata 1.4版本与2.5版本功能特性对比分析

## 1. 概述

Seata是一个开源的分布式事务解决方案，提供了高性能和简单易用的分布式事务服务。从1.4版本到2.5版本，Seata在功能特性、性能优化、API变化、配置方式和兼容性等方面都有显著的改进和增强。

本文将详细对比Seata 1.4版本和2.5版本在各个方面的差异，并说明从1.4升级到2.5版本可能带来的影响和需要注意的事项。

## 2. 主要功能特性对比

### 2.1 事务模式支持

| 特性 | Seata 1.4 | Seata 2.5 | 说明 |
|------|-----------|-----------|------|
| AT模式 | 支持 | 支持 | 自动补偿事务模式，无代码侵入 |
| TCC模式 | 支持 | 支持 | Try-Confirm-Cancel三阶段模式 |
| Saga模式 | 支持 | 支持 | 长事务解决方案 |
| XA模式 | 支持 | 支持 | 基于XA规范的分布式事务 |

### 2.2 性能优化

#### Seata 1.4版本
- 基于两阶段提交协议的优化实现
- 通过undo_log实现自动回滚机制
- 性能优于传统的XA模式

#### Seata 2.5版本
- 支持HTTP/2协议，可降低30%的网络传输延迟
- 通过异步处理HTTP请求，提高并发处理能力
- undo_log压缩功能，减少存储空间占用
- 优化了全局事务的管理性能，减少了事务提交和回滚的开销

### 2.3 兼容性改进

#### Seata 1.4版本
- 兼容JDK 8及以上版本
- 支持多种注册中心（Nacos、Eureka、Zookeeper等）
- 支持多种配置中心（Nacos、Apollo、Zookeeper等）

#### Seata 2.5版本
- 增强了对不同版本JDK（8, 11, 17）的兼容性
- 增强了对不同版本Spring（5.2.x, 5.3.x, 6.0.x）的兼容性
- 新增对OceanBase Oracle的支持
- 对低版本conf配置进行了兼容适配
- 对安全问题进行了深度治理

## 3. API变化

### 3.1 注解变化

#### Seata 1.4版本
- 使用`@GlobalTransactional`注解开启全局事务
- 使用`@LocalTCC`注解标记TCC模式接口
- 使用`@TwoPhaseBusinessAction`注解定义TCC的两阶段方法

#### Seata 2.5版本
- 保持了与1.4版本相同的注解体系
- 注解`@LocalTCC`需要修饰在实现类上
- 注解`@TwoPhaseBusinessAction`需要修饰在实现类方法prepare上

### 3.2 配置方式变化

#### Seata 1.4版本
- 支持file、nacos、apollo、zookeeper、consul等多种配置方式
- 配置文件命名风格支持点号+驼峰式组合（conf文件）或点号+中划线组合（properties/yml文件）

#### Seata 2.5版本
- 保持了与1.4版本相同的配置方式
- 配置项风格统一为点号+中划线组合
- 事务分组配置支持了默认值，避免了歧义和降低学习成本

## 4. 架构变化

### 4.1 Seata Server变化

#### Seata 1.4版本
- Seata Server包含Web控制台
- HTTP端口与事务端口分离

#### Seata 2.5版本
- 移除了spring-boot-web相关组件
- HTTP端口合并至Seata事务端口（默认8091）
- 移除了内置的Web控制台，需要通过namingserver访问

### 4.2 存储模式变化

#### Seata 1.4版本
- 支持file、db、redis三种存储模式
- Redis存储模式使用重构后的hash数据结构

#### Seata 2.5版本
- 保持了与1.4版本相同的存储模式
- 增加了distributed_lock表用于Seata Server异步任务调度
- lock_table增加了status字段和相关索引

## 5. 安全性改进

### Seata 1.4版本
- 基本的安全机制
- 默认凭证机制

### Seata 2.5版本
- 强制账户初始化并禁用默认凭证
- 增强了Raft存储模式下的鉴权能力
- 对安全问题进行了深度治理
- 解决了客户端-服务端固定密钥问题

## 6. 从1.4升级到2.5的注意事项

### 6.1 兼容性事项

1. **端口配置变更**：
   - Seata 2.5版本移除了spring-boot-web相关组件
   - HTTP端口合并至Seata事务端口（默认8091）
   - 需要将server.port配置改为与事务端口一致

2. **控制台变更**：
   - Seata 2.5版本移除了内置的Web控制台
   - 如果需要访问控制台，需要部署namingserver并将seata-server注册至namingserver中

3. **Raft模式配置变更**：
   - Seata 2.5版本增强了Raft存储模式下的鉴权能力
   - 需要配置seata raft的鉴权信息
   - 客户端和服务端需要保持username和password一致

4. **注解使用变更**：
   - `@LocalTCC`注解需要修饰在实现类上
   - `@TwoPhaseBusinessAction`注解需要修饰在实现类方法prepare上

### 6.2 数据库表结构变更

1. 表结构字符集统一从utf8调整为utf8mb4
2. global_table调整索引从idx_gmt_modified_status调整为idx_status_gmt_modified
3. lock_table增加status字段，增加idx_status，idx_xid_and_branch_id索引
4. 增加distributed_lock表用于seata-server异步任务调度

### 6.3 配置项变更

1. 事务分组配置默认值由my_test_tx_group修改为default_tx_group
2. 部分配置项命名风格进行了统一调整
3. Redis注册中心内部结构调整，不再向下兼容

### 6.4 升级建议

1. **升级前准备**：
   - 备份现有配置文件和数据库
   - 确保所有事务运行完毕后再进行升级
   - 在线下环境充分验证后再进行生产环境升级

2. **升级步骤**：
   - 先升级Seata Client SDK，再升级Seata Server
   - 如果使用Redis作为注册中心，需要将客户端和服务端一并升级
   - 如果使用Raft存储模式，需要按照特定的升级流程进行

3. **验证升级结果**：
   - 检查配置文件是否正确迁移
   - 验证各事务模式是否正常工作
   - 检查控制台是否能正常访问（如需要）

## 7. 总结

Seata从1.4版本到2.5版本在多个方面都有显著的改进和增强：

1. **性能提升**：通过支持HTTP/2协议、异步处理和undo_log压缩等功能，显著提升了系统性能
2. **安全性增强**：强制账户初始化、禁用默认凭证、深度安全治理等措施提高了系统安全性
3. **兼容性改进**：增强了对不同版本JDK、Spring等的兼容性，新增对OceanBase Oracle的支持
4. **架构优化**：移除了不必要的组件，简化了系统架构
5. **功能增强**：保持了原有的四种事务模式支持，并在细节上进行了优化

升级到Seata 2.5版本可以获得更好的性能、安全性和兼容性，但需要注意版本间的兼容性问题，按照建议的升级步骤进行操作，确保升级过程平稳顺利。