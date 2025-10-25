package com.example.seata.order;

import com.example.seata.order.dto.OrderDTO;
import com.example.seata.order.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.math.BigDecimal;

/**
 * Saga模式集成测试
 */
@Slf4j
@SpringBootTest
public class SagaModeIntegrationTest {

    @Resource
    private OrderService orderService;

    /**
     * 测试Saga模式正常提交流程
     */
    @Test
    public void testCreateOrderSaga() {
        log.info("开始测试Saga模式正常提交流程");

        OrderDTO orderDTO = new OrderDTO();
        orderDTO.setUserId("U001");
        orderDTO.setProductId("P001");
        orderDTO.setCount(10);
        orderDTO.setAmount(new BigDecimal("100.00"));

        try {
            String result = orderService.createOrderSaga(orderDTO);
            log.info("Saga模式订单创建结果: {}", result);
        } catch (Exception e) {
            log.error("Saga模式订单创建异常", e);
        }
    }

    /**
     * 测试Saga模式回滚流程
     */
    @Test
    public void testCreateOrderSagaRollback() {
        log.info("开始测试Saga模式回滚流程");

        OrderDTO orderDTO = new OrderDTO();
        orderDTO.setUserId("U001");
        orderDTO.setProductId("P001");
        orderDTO.setCount(10);
        orderDTO.setAmount(new BigDecimal("100.00"));

        try {
            String result = orderService.createOrderSagaWithRollback(orderDTO);
            log.info("Saga模式订单创建结果: {}", result);
        } catch (Exception e) {
            log.error("Saga模式订单创建异常（预期）", e);
        }
    }
}