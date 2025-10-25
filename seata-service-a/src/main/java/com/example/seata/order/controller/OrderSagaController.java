package com.example.seata.order.controller;

import com.example.seata.order.dto.OrderDTO;
import com.example.seata.order.dto.Result;
import com.example.seata.order.service.OrderService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * Saga模式订单控制器
 */
@Slf4j
@Api(tags = "Saga模式订单服务")
@RestController
@RequestMapping("/order-saga")
public class OrderSagaController {

    @Resource
    private OrderService orderService;

    /**
     * 创建订单（Saga模式 - 正常提交）
     */
    @ApiOperation(value = "创建订单（Saga模式 - 正常提交）", 
                  notes = "验证Saga模式正常提交流程，包括订单创建和库存扣减")
    @PostMapping("/create")
    public Result<String> createOrderSaga(@RequestBody OrderDTO orderDTO) {
        try {
            log.info("接收到创建订单请求（Saga模式）：{}", orderDTO);
            String result = orderService.createOrderSaga(orderDTO);
            return Result.success("订单创建成功（Saga模式）", result);
        } catch (Exception e) {
            log.error("创建订单失败（Saga模式）", e);
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 创建订单（Saga模式 - 回滚场景）
     */
    @ApiOperation(value = "创建订单（Saga模式 - 回滚场景）", 
                  notes = "模拟异常触发Saga模式回滚流程，验证补偿机制")
    @PostMapping("/create-rollback")
    public Result<String> createOrderSagaRollback(@RequestBody OrderDTO orderDTO) {
        try {
            log.info("接收到创建订单请求（Saga模式-回滚场景）：{}", orderDTO);
            String result = orderService.createOrderSagaWithRollback(orderDTO);
            return Result.success("订单创建成功（Saga模式）", result);
        } catch (Exception e) {
            log.error("创建订单触发回滚（Saga模式）", e);
            return Result.fail(e.getMessage());
        }
    }
}