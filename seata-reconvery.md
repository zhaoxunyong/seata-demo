# Seata TCC/Saga模式服务器意外终止恢复方案

当使用Seata的TCC或Saga模式时，如果服务器意外终止，可以通过以下几种方式来解决和恢复：

## 1. Seata的自动恢复机制

### 1.1 事务恢复机制
Seata提供了自动恢复机制来处理服务器意外终止的情况：

1. **事务状态持久化**：
   - Seata会将事务状态信息持久化到数据库中
   - 即使服务器重启，事务状态也不会丢失

2. **定期扫描未完成事务**：
   - Seata Server会定期扫描未完成的事务
   - 对于超时或异常的事务，会自动触发回滚操作

3. **重试机制**：
   - Seata提供了事务重试机制
   - 可以配置重试次数和间隔时间

### 1.2 配置参数优化
在application.yml中可以配置以下参数来增强恢复能力：

```yaml
seata:
  client:
    tm:
      commit-retry-count: 5           # 提交重试次数
      rollback-retry-count: 5         # 回滚重试次数
      default-global-transaction-timeout: 60000  # 全局事务超时时间(毫秒)
    rm:
      report-retry-count: 5           # RM报告重试次数
```

## 2. TCC模式的处理方案

### 2.1 幂等性设计
在TCC模式中，必须保证Try、Confirm、Cancel操作的幂等性：

```java
// OrderTCCServiceImpl.java
@Service
public class OrderTCCServiceImpl implements OrderTCCService {
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    @GlobalTransactional
    public boolean prepareCreateOrder(OrderDTO orderDTO) {
        // Try阶段：创建订单，状态设为INIT
        OrderTCC order = new OrderTCC();
        order.setUserId(orderDTO.getUserId());
        order.setProductId(orderDTO.getProductId());
        order.setCount(orderDTO.getCount());
        order.setAmount(orderDTO.getAmount());
        order.setStatus("INIT");  // 初始化状态
        order.setCreateTime(LocalDateTime.now());
        orderMapper.insert(order);
        return true;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean commitCreateOrder(OrderDTO orderDTO) {
        // Confirm阶段：更新订单状态为SUCCESS
        // 需要幂等性处理，检查订单是否已经处理过
        int result = orderMapper.updateStatusByUserIdAndProductId(
            orderDTO.getUserId(), 
            orderDTO.getProductId(), 
            "INIT", 
            "SUCCESS"
        );
        return result > 0;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean rollbackCreateOrder(OrderDTO orderDTO) {
        // Cancel阶段：更新订单状态为CANCEL
        // 需要幂等性处理，检查订单是否已经处理过
        int result = orderMapper.updateStatusByUserIdAndProductId(
            orderDTO.getUserId(), 
            orderDTO.getProductId(), 
            Arrays.asList("INIT", "PROCESSING"), 
            "CANCEL"
        );
        return result > 0;
    }
}
```

### 2.2 状态检查机制
在实现TCC接口时，需要添加状态检查机制：

```java
// OrderTCCMapper.java
@Mapper
public interface OrderTCCMapper {
    // 更新订单状态（带状态检查）
    @Update("UPDATE t_order_tcc SET status = #{newStatus}, update_time = NOW() " +
            "WHERE user_id = #{userId} AND product_id = #{productId} " +
            "AND status = #{oldStatus}")
    int updateStatusByUserIdAndProductId(@Param("userId") String userId,
                                        @Param("productId") String productId,
                                        @Param("oldStatus") String oldStatus,
                                        @Param("newStatus") String newStatus);
    
    // 批量状态更新（用于回滚）
    @Update("<script>" +
            "UPDATE t_order_tcc SET status = #{newStatus}, update_time = NOW() " +
            "WHERE user_id = #{userId} AND product_id = #{productId} " +
            "AND status IN " +
            "<foreach item='item' collection='oldStatusList' open='(' separator=',' close=')'>" +
            "#{item}" +
            "</foreach>" +
            "</script>")
    int updateStatusByUserIdAndProductId(@Param("userId") String userId,
                                        @Param("productId") String productId,
                                        @Param("oldStatusList") List<String> oldStatusList,
                                        @Param("newStatus") String newStatus);
}
```

## 3. Saga模式的处理方案

### 3.1 补偿机制
Saga模式通过补偿机制来处理服务器意外终止：

```json
{
  "Name": "create-order-saga",
  "Comment": "创建订单Saga流程",
  "StartState": "CreateOrder",
  "Version": "0.0.1",
  "States": {
    "CreateOrder": {
      "Type": "ServiceTask",
      "ServiceName": "order-service",
      "ServiceMethod": "createOrder",
      "CompensateState": "CompensateOrder",
      "Next": "ReduceStorage",
      "Input": [
        "$.userId",
        "$.productId",
        "$.count",
        "$.amount"
      ],
      "Status": {
        "#root == true": "SU",
        "#root == false": "FA"
      }
    },
    "CompensateOrder": {
      "Type": "ServiceTask",
      "ServiceName": "order-service",
      "ServiceMethod": "compensateOrder",
      "Input": [
        "$.userId",
        "$.productId"
      ]
    },
    // ... 其他状态定义
  }
}
```

### 3.2 幂等性补偿操作
在Saga模式中，补偿操作也需要保证幂等性：

```java
// OrderSagaServiceImpl.java
@Service
public class OrderSagaServiceImpl implements OrderSagaService {
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean createOrder(String userId, String productId, Integer count, String amount) {
        log.info("Saga订单服务 - 创建订单，用户ID={}，商品ID={}，数量={}，金额={}", 
                userId, productId, count, amount);
        
        OrderSaga order = new OrderSaga();
        order.setUserId(userId);
        order.setProductId(productId);
        order.setCount(count);
        order.setAmount(new BigDecimal(amount));
        order.setStatus("PROCESSING");
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        
        int result = orderSagaMapper.insert(order);
        return result > 0;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean compensateOrder(String userId, String productId) {
        log.info("Saga订单服务 - 补偿订单，用户ID={}，商品ID={}", userId, productId);
        
        // 幂等性处理：检查订单状态是否需要补偿
        int result = orderSagaMapper.update(null, 
                new LambdaUpdateWrapper<OrderSaga>()
                        .set(OrderSaga::getStatus, "FAIL")
                        .set(OrderSaga::getUpdateTime, LocalDateTime.now())
                        .eq(OrderSaga::getUserId, userId)
                        .eq(OrderSaga::getProductId, productId)
                        .in(OrderSaga::getStatus, "INIT", "PROCESSING")
        );
        
        // 即使没有更新记录也返回成功，保证幂等性
        return true;
    }
}
```

## 4. 监控和告警机制

### 4.1 事务状态监控
可以通过以下方式监控事务状态：

1. **Seata控制台**：
   - 使用Seata提供的控制台监控事务状态
   - 实时查看全局事务和分支事务的状态

2. **自定义监控**：
   - 在业务代码中添加事务状态日志
   - 通过日志分析事务执行情况

### 4.2 告警机制
建立告警机制及时发现异常事务：

```java
// TransactionMonitor.java
@Component
public class TransactionMonitor {
    
    @Scheduled(fixedRate = 60000) // 每分钟检查一次
    public void checkPendingTransactions() {
        // 查询长时间未完成的事务
        List<OrderSaga> pendingOrders = orderSagaMapper.selectList(
            new LambdaQueryWrapper<OrderSaga>()
                .in(OrderSaga::getStatus, "INIT", "PROCESSING")
                .lt(OrderSaga::getCreateTime, LocalDateTime.now().minusMinutes(10))
        );
        
        if (!pendingOrders.isEmpty()) {
            // 发送告警通知
            log.warn("发现{}个长时间未完成的事务", pendingOrders.size());
            // 可以集成邮件、短信等告警方式
        }
    }
}
```

## 5. 定期清理机制

### 5.1 过期事务清理
定期清理过期的事务记录：

```java
// TransactionCleaner.java
@Component
public class TransactionCleaner {
    
    @Scheduled(cron = "0 0 2 * * ?") // 每天凌晨2点执行
    public void cleanExpiredTransactions() {
        // 清理7天前的已完成事务
        LocalDateTime expiredTime = LocalDateTime.now().minusDays(7);
        int deletedCount = orderSagaMapper.delete(
            new LambdaQueryWrapper<OrderSaga>()
                .in(OrderSaga::getStatus, "SUCCESS", "FAIL")
                .lt(OrderSaga::getCreateTime, expiredTime)
        );
        
        log.info("清理了{}条过期事务记录", deletedCount);
    }
}
```

## 6. 最佳实践建议

### 6.1 配置优化
1. 合理设置事务超时时间
2. 配置适当的重试次数和间隔
3. 启用事务日志持久化

### 6.2 代码设计
1. 所有操作必须保证幂等性
2. 添加状态检查机制
3. 实现完善的补偿逻辑

### 6.3 监控告警
1. 建立事务状态监控
2. 设置合理的告警阈值
3. 定期检查和清理过期数据

通过以上方案，可以有效解决Seata TCC/Saga模式下服务器意外终止的问题，确保分布式事务的一致性和可靠性。