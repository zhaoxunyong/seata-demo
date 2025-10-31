package com.example.seata.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.seata.order.entity.OrderTCC;
import com.example.seata.order.mapper.OrderTCCMapper;
import com.example.seata.order.service.OrderTCCService;
import io.seata.rm.tcc.api.BusinessActionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * TCC模式订单服务实现
 */
@Slf4j
@Service
public class OrderTCCServiceImpl implements OrderTCCService {

    @Resource
    private OrderTCCMapper orderTCCMapper;

    /**
     * Try阶段：尝试创建订单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean tryCreate(String userId, String productId, Integer count, String amount) {
        log.info("TCC订单服务 - Try阶段：开始创建订单，用户ID={}，商品ID={}，数量={}，金额={}", 
                userId, productId, count, amount);

        OrderTCC order = new OrderTCC();
        order.setUserId(userId);
        order.setProductId(productId);
        order.setCount(count);
        order.setAmount(new BigDecimal(amount));
        order.setStatus("INIT");
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());

        int result = orderTCCMapper.insert(order);
        if (result <= 0) {
            log.error("TCC订单服务 - Try阶段：创建订单失败");
            return false;
        }

        log.info("TCC订单服务 - Try阶段：创建订单成功，订单ID={}，状态=INIT", order.getId());
        return true;
    }

    /**
     * Confirm阶段：确认订单创建
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean confirmCreate(BusinessActionContext context) {
        String userId = (String) context.getActionContext("userId");
        String productId = (String) context.getActionContext("productId");
        
        log.info("TCC订单服务 - Confirm阶段：开始确认订单，用户ID={}，商品ID={}", userId, productId);

        // 更新订单状态为SUCCESS
        int result = orderTCCMapper.update(null, 
                new LambdaUpdateWrapper<OrderTCC>()
                        .set(OrderTCC::getStatus, "SUCCESS")
                        .set(OrderTCC::getUpdateTime, LocalDateTime.now())
                        .eq(OrderTCC::getUserId, userId)
                        .eq(OrderTCC::getProductId, productId)
                        .eq(OrderTCC::getStatus, "INIT")
        );

        if (result <= 0) {
            log.warn("TCC订单服务 - Confirm阶段：未找到INIT状态的订单（可能已确认），用户ID={}，商品ID={}", userId, productId);
            // 幂等性处理：已经确认过的订单，返回true
            return true;
        }

        log.info("TCC订单服务 - Confirm阶段：确认订单成功，订单状态更新为SUCCESS");
        return true;
    }

    /**
     * Cancel阶段：取消订单创建
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelCreate(BusinessActionContext context) {
        String userId = (String) context.getActionContext("userId");
        String productId = (String) context.getActionContext("productId");
        
        log.info("TCC订单服务 - Cancel阶段：开始取消订单，用户ID={}，商品ID={}", userId, productId);

        // 更新订单状态为CANCEL
        int result = orderTCCMapper.update(null, 
                new LambdaUpdateWrapper<OrderTCC>()
                        .set(OrderTCC::getStatus, "CANCEL")
                        .set(OrderTCC::getUpdateTime, LocalDateTime.now())
                        .eq(OrderTCC::getUserId, userId)
                        .eq(OrderTCC::getProductId, productId)
                        .eq(OrderTCC::getStatus, "INIT")
        );

        if (result <= 0) {
            log.warn("TCC订单服务 - Cancel阶段：未找到INIT状态的订单（可能是空回滚），用户ID={}，商品ID={}", userId, productId);
            // 空回滚场景：Try未执行，直接Cancel
            // 这种情况下应返回true，避免TC重试
            return true;
        }

        log.info("TCC订单服务 - Cancel阶段：取消订单成功，订单状态更新为CANCEL");
        return true;
    }
}
