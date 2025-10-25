package com.example.seata.storage;

import com.example.seata.storage.dto.StorageDTO;
import com.example.seata.storage.service.StorageSagaService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

/**
 * Saga模式服务集成测试
 */
@Slf4j
@SpringBootTest
public class SagaServiceIntegrationTest {

    @Resource
    private StorageSagaService storageSagaService;

    /**
     * 测试Saga模式库存扣减
     */
    @Test
    public void testReduceStorage() {
        log.info("开始测试Saga模式库存扣减");

        try {
            boolean result = storageSagaService.reduceStorage("P001", 10);
            log.info("Saga模式库存扣减结果: {}", result);
        } catch (Exception e) {
            log.error("Saga模式库存扣减异常", e);
        }
    }

    /**
     * 测试Saga模式库存补偿
     */
    @Test
    public void testCompensateStorage() {
        log.info("开始测试Saga模式库存补偿");

        try {
            boolean result = storageSagaService.compensateStorage("P001", 10);
            log.info("Saga模式库存补偿结果: {}", result);
        } catch (Exception e) {
            log.error("Saga模式库存补偿异常", e);
        }
    }

    /**
     * 测试Saga模式库存完成
     */
    @Test
    public void testCompleteStorage() {
        log.info("开始测试Saga模式库存完成");

        try {
            boolean result = storageSagaService.completeStorage("P001");
            log.info("Saga模式库存完成结果: {}", result);
        } catch (Exception e) {
            log.error("Saga模式库存完成异常", e);
        }
    }
}