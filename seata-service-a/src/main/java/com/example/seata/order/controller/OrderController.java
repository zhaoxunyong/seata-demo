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
 * AT模式订单控制器
 */
@Slf4j
@Api(tags = "AT模式订单服务")
@RestController
@RequestMapping("/order")
public class OrderController {

    @Resource
    private OrderService orderService;

    /**
     * 创建订单（AT模式 - 正常提交）
     */
    @ApiOperation(value = "创建订单（AT模式 - 正常提交）", 
                  notes = "验证AT模式正常提交流程，包括订单创建和库存扣减")
    @PostMapping("/create-at")
    public Result<Long> createOrderAT(@RequestBody OrderDTO orderDTO) {
        try {
            log.info("接收到创建订单请求（AT模式）：{}", orderDTO);
            Long orderId = orderService.createOrder(orderDTO);
            return Result.success("订单创建成功", orderId);
        } catch (Exception e) {
            log.error("创建订单失败（AT模式）", e);
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 创建订单（AT模式 - 回滚场景）
     */
    @ApiOperation(value = "创建订单（AT模式 - 回滚场景）", 
                  notes = "模拟异常触发AT模式回滚流程，验证undo_log回滚机制")
    @PostMapping("/create-at-rollback")
    public Result<Long> createOrderATRollback(@RequestBody OrderDTO orderDTO) {
        try {
            log.info("接收到创建订单请求（AT模式-回滚场景）：{}", orderDTO);
            Long orderId = orderService.createOrderWithRollback(orderDTO);
            return Result.success("订单创建成功", orderId);
        } catch (Exception e) {
            log.error("创建订单触发回滚（AT模式）", e);
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 创建订单（TCC模式 - 正常提交）
     */
    @ApiOperation(value = "创建订单（TCC模式 - 正常提交）", 
                  notes = "验证TCC模式Try-Confirm流程，包括订单创建和库存冻结确认")
    @PostMapping("/create-tcc")
    public Result<String> createOrderTCC(@RequestBody OrderDTO orderDTO) {
        try {
            log.info("接收到创建订单请求（TCC模式）：{}", orderDTO);
            String result = orderService.createOrderTCC(orderDTO);
            return Result.success("订单创建成功（TCC模式）", result);
        } catch (Exception e) {
            log.error("创建订单失败（TCC模式）", e);
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 创建订单（TCC模式 - 回滚场景）
     */
    @ApiOperation(value = "创建订单（TCC模式 - 回滚场景）", 
                  notes = "模拟异常触发TCC模式Cancel回滚流程，验证冻结库存释放机制")
    @PostMapping("/create-tcc-rollback")
    public Result<String> createOrderTCCRollback(@RequestBody OrderDTO orderDTO) {
        try {
            log.info("接收到创建订单请求（TCC模式-回滚场景）：{}", orderDTO);
            String result = orderService.createOrderTCCWithRollback(orderDTO);
            return Result.success("订单创建成功（TCC模式）", result);
        } catch (Exception e) {
            log.error("创建订单触发回滚（TCC模式）", e);
            return Result.fail(e.getMessage());
        }
    }
}
