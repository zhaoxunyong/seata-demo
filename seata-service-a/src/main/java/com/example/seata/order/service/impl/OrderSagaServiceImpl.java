package com.example.seata.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.seata.order.entity.OrderSaga;
import com.example.seata.order.mapper.OrderSagaMapper;
import com.example.seata.order.service.OrderSagaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Saga模式订单服务实现
 */
@Slf4j
@Service
public class OrderSagaServiceImpl implements OrderSagaService {

    @Resource
    private OrderSagaMapper orderSagaMapper;

    /**
     * 创建订单（Saga正向操作）
     */
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
        if (result <= 0) {
            log.error("Saga订单服务 - 创建订单失败");
            return false;
        }

        log.info("Saga订单服务 - 创建订单成功，订单ID={}，状态=PROCESSING", order.getId());
        return true;
    }

    /**
     * 补偿订单（Saga补偿操作）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean compensateOrder(String userId, String productId) {
        log.info("Saga订单服务 - 补偿订单，用户ID={}，商品ID={}", userId, productId);

        // 更新订单状态为FAIL
        int result = orderSagaMapper.update(null, 
                new LambdaUpdateWrapper<OrderSaga>()
                        .set(OrderSaga::getStatus, "FAIL")
                        .set(OrderSaga::getUpdateTime, LocalDateTime.now())
                        .eq(OrderSaga::getUserId, userId)
                        .eq(OrderSaga::getProductId, productId)
                        .in(OrderSaga::getStatus, "INIT", "PROCESSING")
        );

        if (result <= 0) {
            log.warn("Saga订单服务 - 补偿订单：未找到PROCESSING状态的订单，用户ID={}，商品ID={}", userId, productId);
            return true;
        }

        log.info("Saga订单服务 - 补偿订单成功，订单状态更新为FAIL");
        return true;
    }

    /**
     * 完成订单（Saga完成操作）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean completeOrder(String userId, String productId) {
        log.info("Saga订单服务 - 完成订单，用户ID={}，商品ID={}", userId, productId);

        // 更新订单状态为SUCCESS
        int result = orderSagaMapper.update(null, 
                new LambdaUpdateWrapper<OrderSaga>()
                        .set(OrderSaga::getStatus, "SUCCESS")
                        .set(OrderSaga::getUpdateTime, LocalDateTime.now())
                        .eq(OrderSaga::getUserId, userId)
                        .eq(OrderSaga::getProductId, productId)
                        .eq(OrderSaga::getStatus, "PROCESSING")
        );

        if (result <= 0) {
            log.warn("Saga订单服务 - 完成订单：未找到PROCESSING状态的订单，用户ID={}，商品ID={}", userId, productId);
            return false;
        }

        log.info("Saga订单服务 - 完成订单成功，订单状态更新为SUCCESS");
        return true;
    }
}