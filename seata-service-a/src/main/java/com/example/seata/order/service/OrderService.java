package com.example.seata.order.service;

import com.example.seata.order.dto.OrderDTO;
import com.example.seata.order.dto.Result;
import com.example.seata.order.dto.StorageDTO;
import com.example.seata.order.entity.Order;
import com.example.seata.order.exception.BusinessException;
import com.example.seata.order.feign.StorageFeignClient;
import com.example.seata.order.mapper.OrderMapper;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.math.BigDecimal;

/**
 * AT模式订单服务
 */
@Slf4j
@Service
public class OrderService {

    @Resource
    private OrderMapper orderMapper;

    @Resource
    private StorageFeignClient storageFeignClient;

    @Resource
    private OrderTCCService orderTCCService;

    /**
     * 创建订单（AT模式 - 正常提交场景）
     *
     * @param orderDTO 订单DTO
     * @return 订单ID
     */
    @GlobalTransactional(name = "create-order-at", rollbackFor = Exception.class)
    public Long createOrder(OrderDTO orderDTO) {
        log.info("订单服务：开始创建订单（AT模式），订单信息={}", orderDTO);

        // 1. 创建订单记录
        Order order = new Order();
        order.setUserId(orderDTO.getUserId());
        order.setProductId(orderDTO.getProductId());
        order.setCount(orderDTO.getCount());
        order.setAmount(orderDTO.getAmount());
        order.setStatus("INIT");
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());

        int result = orderMapper.insert(order);
        if (result <= 0) {
            throw new BusinessException("创建订单失败");
        }
        log.info("订单服务：订单创建成功，订单ID={}", order.getId());

        // 2. 调用库存服务扣减库存
        log.info("订单服务：开始调用库存服务扣减库存");
        StorageDTO storageDTO = new StorageDTO(orderDTO.getProductId(), orderDTO.getCount());
        Result<Void> storageResult = storageFeignClient.reduce(storageDTO);

        if (storageResult.getCode() != 200) {
            log.error("订单服务：扣减库存失败，{}", storageResult.getMessage());
            throw new BusinessException("扣减库存失败：" + storageResult.getMessage());
        }
        log.info("订单服务：扣减库存成功");

        // 3. 更新订单状态为成功
        order.setStatus("SUCCESS");
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);

        log.info("订单服务：订单创建完成（AT模式），订单ID={}", order.getId());
        return order.getId();
    }

    /**
     * 创建订单（AT模式 - 回滚场景）
     * 模拟异常触发全局事务回滚
     *
     * @param orderDTO 订单DTO
     * @return 订单ID
     */
    @GlobalTransactional(name = "create-order-at-rollback", rollbackFor = Exception.class)
    public Long createOrderWithRollback(OrderDTO orderDTO) {
        log.info("订单服务：开始创建订单（AT模式-回滚场景），订单信息={}", orderDTO);

        // 1. 创建订单记录
        Order order = new Order();
        order.setUserId(orderDTO.getUserId());
        order.setProductId(orderDTO.getProductId());
        order.setCount(orderDTO.getCount());
        order.setAmount(orderDTO.getAmount());
        order.setStatus("INIT");
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());

        int result = orderMapper.insert(order);
        if (result <= 0) {
            throw new BusinessException("创建订单失败");
        }
        log.info("订单服务：订单创建成功，订单ID={}", order.getId());

        // 2. 调用库存服务扣减库存
        log.info("订单服务：开始调用库存服务扣减库存");
        StorageDTO storageDTO = new StorageDTO(orderDTO.getProductId(), orderDTO.getCount());
        Result<Void> storageResult = storageFeignClient.reduce(storageDTO);

        if (storageResult.getCode() != 200) {
            log.error("订单服务：扣减库存失败，{}", storageResult.getMessage());
            throw new BusinessException("扣减库存失败：" + storageResult.getMessage());
        }
        log.info("订单服务：扣减库存成功");

        // 3. 模拟异常，触发回滚
        log.warn("订单服务：模拟业务异常，触发全局事务回滚");
        throw new BusinessException("模拟异常：业务处理失败，触发AT模式回滚");
    }

    /**
     * 创建订单（TCC模式 - 正常提交场景）
     *
     * @param orderDTO 订单DTO
     * @return 订单创建结果
     */
    @GlobalTransactional(name = "create-order-tcc", rollbackFor = Exception.class)
    public String createOrderTCC(OrderDTO orderDTO) {
        log.info("订单服务：开始创建订单（TCC模式），订单信息={}", orderDTO);

        // 1. Try阶段：创建订单（状态=INIT）
        boolean orderResult = orderTCCService.tryCreate(
                orderDTO.getUserId(),
                orderDTO.getProductId(),
                orderDTO.getCount(),
                orderDTO.getAmount().toString()
        );

        if (!orderResult) {
            throw new BusinessException("TCC-Try：创建订单失败");
        }
        log.info("订单服务：TCC-Try创建订单成功");

        // 2. Try阶段：调用库存服务冻结库存
        log.info("订单服务：开始调用库存服务冻结库存（TCC模式）");
        StorageDTO storageDTO = new StorageDTO(orderDTO.getProductId(), orderDTO.getCount());
        Result<Void> storageResult = storageFeignClient.reduceTcc(storageDTO);

        if (storageResult.getCode() != 200) {
            log.error("订单服务：TCC-Try冻结库存失败，{}", storageResult.getMessage());
            throw new BusinessException("TCC-Try冻结库存失败：" + storageResult.getMessage());
        }
        log.info("订单服务：TCC-Try冻结库存成功");

        // Try阶段全部成功，Seata会自动调用Confirm方法
        log.info("订单服务：TCC-Try阶段全部完成，等待Confirm确认");
        return "TCC订单创建成功（Try阶段完成，等待Confirm）";
    }

    /**
     * 创建订单（TCC模式 - 回滚场景）
     * 模拟异常触发TCC回滚
     *
     * @param orderDTO 订单DTO
     * @return 订单创建结果
     */
    @GlobalTransactional(name = "create-order-tcc-rollback", rollbackFor = Exception.class)
    public String createOrderTCCWithRollback(OrderDTO orderDTO) {
        log.info("订单服务：开始创建订单（TCC模式-回滚场景），订单信息={}", orderDTO);

        // 1. Try阶段：创建订单（状态=INIT）
        boolean orderResult = orderTCCService.tryCreate(
                orderDTO.getUserId(),
                orderDTO.getProductId(),
                orderDTO.getCount(),
                orderDTO.getAmount().toString()
        );

        if (!orderResult) {
            throw new BusinessException("TCC-Try：创建订单失败");
        }
        log.info("订单服务：TCC-Try创建订单成功");

        // 2. Try阶段：调用库存服务冻结库存
        log.info("订单服务：开始调用库存服务冻结库存（TCC模式）");
        StorageDTO storageDTO = new StorageDTO(orderDTO.getProductId(), orderDTO.getCount());
        Result<Void> storageResult = storageFeignClient.reduceTcc(storageDTO);

        if (storageResult.getCode() != 200) {
            log.error("订单服务：TCC-Try冻结库存失败，{}", storageResult.getMessage());
            throw new BusinessException("TCC-Try冻结库存失败：" + storageResult.getMessage());
        }
        log.info("订单服务：TCC-Try冻结库存成功");

        // 3. 模拟异常，触发TCC回滚
        log.warn("订单服务：模拟业务异常，触发TCC Cancel回滚");
        throw new BusinessException("模拟异常：业务处理失败，触发TCC模式Cancel回滚");
    }
}
