# Seata Saga模式使用指南

## 概述

Saga模式是Seata提供的长事务解决方案，适用于业务流程长且需要保证事务一致性的场景。在Saga模式中，业务流程中每个参与者都提交本地事务，当出现某一个参与者失败则补偿前面已经成功的参与者，一阶段正向服务和二阶段补偿服务都由业务开发实现。

## 核心概念

### 状态机引擎
Seata Saga模式基于状态机引擎实现，通过状态图定义服务调用流程。

### 正向操作与补偿操作
- **正向操作**：业务流程中的正常操作
- **补偿操作**：用于回滚正向操作的逆向操作

### 服务编排
通过JSON格式的状态机定义文件编排业务流程。

## 项目实现说明

### 实体类设计
Saga模式相关的实体类：
- `OrderSaga`：订单Saga实体
- `StorageSaga`：库存Saga实体

### 服务层实现
Saga模式相关的服务接口和实现：
- `OrderSagaService`：订单Saga服务接口
- `OrderSagaServiceImpl`：订单Saga服务实现
- `StorageSagaService`：库存Saga服务接口
- `StorageSagaServiceImpl`：库存Saga服务实现

### 控制器层实现
Saga模式相关的控制器：
- `OrderSagaController`：订单Saga控制器
- `StorageSagaController`：库存Saga控制器

### 状态机定义
状态机定义文件位于：`seata-service-a/src/main/resources/saga/create-order-saga.json`

## 使用方式

### 1. 正常提交流程
调用接口：`POST /order-saga/create`

### 2. 回滚流程
调用接口：`POST /order-saga/create-rollback`

## 状态流转

Saga模式中实体状态流转：
- `INIT`：初始化状态
- `PROCESSING`：处理中状态
- `SUCCESS`：成功状态
- `FAIL`：失败状态

## 补偿机制

当业务流程中某个环节失败时，Saga模式会按照相反的顺序执行补偿操作：
1. 执行失败环节的补偿操作
2. 依次执行之前成功环节的补偿操作

## 注意事项

1. 补偿操作需要保证幂等性
2. 状态机定义文件需要准确描述业务流程
3. 正向操作和补偿操作需要成对实现
4. 服务间调用需要通过Feign等方式实现