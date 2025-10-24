package com.example.seata.order;

import com.example.seata.order.dto.OrderDTO;
import com.example.seata.order.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 端到端集成测试
 * 测试目标：验证完整的分布式事务流程
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class EndToEndIntegrationTest {

    @Resource
    private OrderService orderService;

    @Resource
    private JdbcTemplate jdbcTemplate;

    // 库存数据库的JdbcTemplate
    private JdbcTemplate storageJdbcTemplate;

    private static final String TEST_PRODUCT_AT = "P001";
    private static final String TEST_PRODUCT_TCC = "P002";

    @BeforeEach
    public void setUp() {
        log.info("=== 端到端测试准备 ===");
        // 创建连接到库存数据库的JdbcTemplate
        org.springframework.jdbc.datasource.DriverManagerDataSource storageDataSource = 
            new org.springframework.jdbc.datasource.DriverManagerDataSource();
        storageDataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        storageDataSource.setUrl("jdbc:mysql://localhost:3306/seata_storage?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false");
        storageDataSource.setUsername("root");
        storageDataSource.setPassword("root123");
        storageJdbcTemplate = new JdbcTemplate(storageDataSource);
    }

    @AfterEach
    public void tearDown() {
        log.info("=== 端到端测试结束 ===\n");
    }

    /**
     * 场景9：跨服务AT模式分布式事务提交验证
     * 验证订单服务和库存服务的数据一致性
     */
    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("场景9：跨服务AT模式分布式事务提交")
    public void testCrossServiceATCommit() throws Exception {
        log.info("========================================");
        log.info("场景9：跨服务AT模式分布式事务提交验证");
        log.info("========================================");

        // 1. 记录初始状态
        String orderCountSql = "SELECT COUNT(*) FROM t_order";
        int initialOrderCount = jdbcTemplate.queryForObject(orderCountSql, Integer.class);

        String storageSql = "SELECT * FROM t_storage WHERE product_id = ?";
        Map<String, Object> initialStorage = storageJdbcTemplate.queryForMap(storageSql, TEST_PRODUCT_AT);
        int initialResidue = ((Number) initialStorage.get("residue")).intValue();
        int initialUsed = ((Number) initialStorage.get("used")).intValue();
        
        log.info("初始状态 - 订单数: {}, 库存 residue: {}, used: {}", 
                initialOrderCount, initialResidue, initialUsed);

        // 2. 执行跨服务事务
        OrderDTO orderDTO = new OrderDTO();
        orderDTO.setUserId("E2E_USER_001");
        orderDTO.setProductId(TEST_PRODUCT_AT);
        orderDTO.setCount(20);
        orderDTO.setAmount(new BigDecimal("200.00"));

        log.info("执行跨服务订单创建...");
        Long orderId = orderService.createOrder(orderDTO);
        assertNotNull(orderId, "订单ID不应为空");

        // 3. 等待分布式事务完成
        Thread.sleep(3000);

        // 4. 验证订单服务数据
        int finalOrderCount = jdbcTemplate.queryForObject(orderCountSql, Integer.class);
        assertEquals(initialOrderCount + 1, finalOrderCount, "订单应新增1条");
        log.info("✓ 订单服务数据验证通过");

        // 5. 验证库存服务数据
        Map<String, Object> finalStorage = storageJdbcTemplate.queryForMap(storageSql, TEST_PRODUCT_AT);
        int finalResidue = ((Number) finalStorage.get("residue")).intValue();
        int finalUsed = ((Number) finalStorage.get("used")).intValue();

        assertEquals(initialResidue - 20, finalResidue, "库存应扣减20");
        assertEquals(initialUsed + 20, finalUsed, "已用库存应增加20");
        log.info("✓ 库存服务数据验证通过");

        // 6. 验证数据一致性
        int total = ((Number) finalStorage.get("total")).intValue();
        assertEquals(total, finalUsed + finalResidue, "库存数据应保持一致");
        log.info("✓ 跨服务数据一致性验证通过");

        log.info("========================================");
        log.info("场景9：跨服务AT模式分布式事务提交验证 - 通过 ✓");
        log.info("========================================");
    }

    /**
     * 场景10：跨服务AT模式分布式事务回滚验证
     * 验证订单服务和库存服务的回滚一致性
     */
    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("场景10：跨服务AT模式分布式事务回滚")
    public void testCrossServiceATRollback() throws Exception {
        log.info("========================================");
        log.info("场景10：跨服务AT模式分布式事务回滚验证");
        log.info("========================================");

        // 1. 记录初始状态
        String orderCountSql = "SELECT COUNT(*) FROM t_order WHERE user_id = ?";
        int initialOrderCount = jdbcTemplate.queryForObject(orderCountSql, Integer.class, "E2E_USER_002");

        String storageSql = "SELECT * FROM t_storage WHERE product_id = ?";
        Map<String, Object> initialStorage = storageJdbcTemplate.queryForMap(storageSql, TEST_PRODUCT_AT);
        int initialResidue = ((Number) initialStorage.get("residue")).intValue();
        int initialUsed = ((Number) initialStorage.get("used")).intValue();
        
        log.info("初始状态 - 订单数: {}, 库存 residue: {}, used: {}", 
                initialOrderCount, initialResidue, initialUsed);

        // 2. 执行会触发回滚的跨服务事务
        OrderDTO orderDTO = new OrderDTO();
        orderDTO.setUserId("E2E_USER_002");
        orderDTO.setProductId(TEST_PRODUCT_AT);
        orderDTO.setCount(10);
        orderDTO.setAmount(new BigDecimal("100.00"));

        log.info("执行会触发回滚的跨服务事务...");
        Exception exception = assertThrows(Exception.class, () -> {
            orderService.createOrderWithRollback(orderDTO);
        });
        log.info("✓ 成功触发异常：{}", exception.getMessage());

        // 3. 等待分布式事务回滚完成
        Thread.sleep(3000);

        // 4. 验证订单服务数据未变化
        int finalOrderCount = jdbcTemplate.queryForObject(orderCountSql, Integer.class, "E2E_USER_002");
        assertEquals(initialOrderCount, finalOrderCount, "订单数应保持不变");
        log.info("✓ 订单服务回滚验证通过");

        // 5. 验证库存服务数据未变化
        Map<String, Object> finalStorage = storageJdbcTemplate.queryForMap(storageSql, TEST_PRODUCT_AT);
        int finalResidue = ((Number) finalStorage.get("residue")).intValue();
        int finalUsed = ((Number) finalStorage.get("used")).intValue();

        assertEquals(initialResidue, finalResidue, "库存应保持不变");
        assertEquals(initialUsed, finalUsed, "已用库存应保持不变");
        log.info("✓ 库存服务回滚验证通过");

        log.info("========================================");
        log.info("场景10：跨服务AT模式分布式事务回滚验证 - 通过 ✓");
        log.info("========================================");
    }

    /**
     * 场景11：跨服务TCC模式分布式事务提交验证
     */
    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("场景11：跨服务TCC模式分布式事务提交")
    public void testCrossServiceTCCCommit() throws Exception {
        log.info("========================================");
        log.info("场景11：跨服务TCC模式分布式事务提交验证");
        log.info("========================================");

        // 1. 记录初始状态
        String orderCountSql = "SELECT COUNT(*) FROM t_order_tcc WHERE user_id = ?";
        int initialOrderCount = jdbcTemplate.queryForObject(orderCountSql, Integer.class, "E2E_TCC_001");

        String storageSql = "SELECT * FROM t_storage_tcc WHERE product_id = ?";
        Map<String, Object> initialStorage = storageJdbcTemplate.queryForMap(storageSql, TEST_PRODUCT_TCC);
        int initialResidue = ((Number) initialStorage.get("residue")).intValue();
        int initialUsed = ((Number) initialStorage.get("used")).intValue();
        int initialFrozen = ((Number) initialStorage.get("frozen")).intValue();
        
        log.info("初始状态 - 订单数: {}, 库存 residue: {}, used: {}, frozen: {}", 
                initialOrderCount, initialResidue, initialUsed, initialFrozen);

        // 2. 执行TCC跨服务事务
        OrderDTO orderDTO = new OrderDTO();
        orderDTO.setUserId("E2E_TCC_001");
        orderDTO.setProductId(TEST_PRODUCT_TCC);
        orderDTO.setCount(12);
        orderDTO.setAmount(new BigDecimal("120.00"));

        log.info("执行TCC跨服务订单创建...");
        String result = orderService.createOrderTCC(orderDTO);
        assertNotNull(result, "返回结果不应为空");

        // 3. 等待TCC Confirm阶段完成
        Thread.sleep(3000);

        // 4. 验证订单服务数据（状态应为SUCCESS）
        String orderStatusSql = "SELECT status FROM t_order_tcc WHERE user_id = ? ORDER BY id DESC LIMIT 1";
        String orderStatus = jdbcTemplate.queryForObject(orderStatusSql, String.class, "E2E_TCC_001");
        assertEquals("SUCCESS", orderStatus, "订单状态应为SUCCESS");
        log.info("✓ 订单服务Confirm验证通过");

        // 5. 验证库存服务数据（frozen应转为used）
        Map<String, Object> finalStorage = storageJdbcTemplate.queryForMap(storageSql, TEST_PRODUCT_TCC);
        int finalResidue = ((Number) finalStorage.get("residue")).intValue();
        int finalUsed = ((Number) finalStorage.get("used")).intValue();
        int finalFrozen = ((Number) finalStorage.get("frozen")).intValue();

        assertEquals(initialResidue - 12, finalResidue, "剩余库存应减少12");
        assertEquals(initialUsed + 12, finalUsed, "已用库存应增加12");
        assertEquals(initialFrozen, finalFrozen, "冻结库存应恢复初始值");
        log.info("✓ 库存服务Confirm验证通过");

        // 6. 验证TCC数据一致性
        int total = ((Number) finalStorage.get("total")).intValue();
        assertEquals(total, finalUsed + finalFrozen + finalResidue, "TCC库存数据应保持一致");
        log.info("✓ TCC跨服务数据一致性验证通过");

        log.info("========================================");
        log.info("场景11：跨服务TCC模式分布式事务提交验证 - 通过 ✓");
        log.info("========================================");
    }

    /**
     * 场景12：跨服务TCC模式分布式事务回滚验证
     */
    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("场景12：跨服务TCC模式分布式事务回滚")
    public void testCrossServiceTCCRollback() throws Exception {
        log.info("========================================");
        log.info("场景12：跨服务TCC模式分布式事务回滚验证");
        log.info("========================================");

        // 1. 记录初始状态
        String storageSql = "SELECT * FROM t_storage_tcc WHERE product_id = ?";
        Map<String, Object> initialStorage = storageJdbcTemplate.queryForMap(storageSql, TEST_PRODUCT_TCC);
        int initialResidue = ((Number) initialStorage.get("residue")).intValue();
        int initialUsed = ((Number) initialStorage.get("used")).intValue();
        int initialFrozen = ((Number) initialStorage.get("frozen")).intValue();
        
        log.info("初始状态 - 库存 residue: {}, used: {}, frozen: {}", 
                initialResidue, initialUsed, initialFrozen);

        // 2. 执行会触发回滚的TCC跨服务事务
        OrderDTO orderDTO = new OrderDTO();
        orderDTO.setUserId("E2E_TCC_002");
        orderDTO.setProductId(TEST_PRODUCT_TCC);
        orderDTO.setCount(6);
        orderDTO.setAmount(new BigDecimal("60.00"));

        log.info("执行会触发Cancel的TCC跨服务事务...");
        Exception exception = assertThrows(Exception.class, () -> {
            orderService.createOrderTCCWithRollback(orderDTO);
        });
        log.info("✓ 成功触发异常：{}", exception.getMessage());

        // 3. 等待TCC Cancel阶段完成
        Thread.sleep(3000);

        // 4. 验证订单服务数据（状态应为CANCEL）
        String orderStatusSql = "SELECT status FROM t_order_tcc WHERE user_id = ? ORDER BY id DESC LIMIT 1";
        String orderStatus = jdbcTemplate.queryForObject(orderStatusSql, String.class, "E2E_TCC_002");
        assertEquals("CANCEL", orderStatus, "订单状态应为CANCEL");
        log.info("✓ 订单服务Cancel验证通过");

        // 5. 验证库存服务数据（frozen应释放回residue）
        Map<String, Object> finalStorage = storageJdbcTemplate.queryForMap(storageSql, TEST_PRODUCT_TCC);
        int finalResidue = ((Number) finalStorage.get("residue")).intValue();
        int finalUsed = ((Number) finalStorage.get("used")).intValue();
        int finalFrozen = ((Number) finalStorage.get("frozen")).intValue();

        assertEquals(initialResidue, finalResidue, "剩余库存应恢复");
        assertEquals(initialUsed, finalUsed, "已用库存应不变");
        assertEquals(initialFrozen, finalFrozen, "冻结库存应恢复");
        log.info("✓ 库存服务Cancel验证通过");

        log.info("========================================");
        log.info("场景12：跨服务TCC模式分布式事务回滚验证 - 通过 ✓");
        log.info("========================================");
    }

    /**
     * 场景13：并发场景下的分布式事务一致性验证
     */
    @Test
    @org.junit.jupiter.api.Order(5)
    @DisplayName("场景13：并发场景分布式事务一致性")
    public void testConcurrentDistributedTransaction() throws Exception {
        log.info("========================================");
        log.info("场景13：并发场景分布式事务一致性验证");
        log.info("========================================");

        // 1. 记录初始库存
        String storageSql = "SELECT * FROM t_storage WHERE product_id = ?";
        Map<String, Object> initialStorage = storageJdbcTemplate.queryForMap(storageSql, TEST_PRODUCT_AT);
        int initialResidue = ((Number) initialStorage.get("residue")).intValue();
        int initialUsed = ((Number) initialStorage.get("used")).intValue();
        
        log.info("初始库存 - residue: {}, used: {}", initialResidue, initialUsed);

        // 2. 并发执行多个订单
        int concurrentCount = 5;
        int eachOrderCount = 3;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentCount);
        CountDownLatch latch = new CountDownLatch(concurrentCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        log.info("启动{}个并发订单请求，每个订单数量: {}", concurrentCount, eachOrderCount);
        
        for (int i = 0; i < concurrentCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    OrderDTO orderDTO = new OrderDTO();
                    orderDTO.setUserId("CONCURRENT_USER_" + index);
                    orderDTO.setProductId(TEST_PRODUCT_AT);
                    orderDTO.setCount(eachOrderCount);
                    orderDTO.setAmount(new BigDecimal("30.00"));
                    
                    orderService.createOrder(orderDTO);
                    successCount.incrementAndGet();
                    log.info("并发订单{}执行成功", index);
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    log.warn("并发订单{}执行失败: {}", index, e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        // 3. 等待所有请求完成
        latch.await();
        executor.shutdown();
        Thread.sleep(5000); // 等待事务完全完成

        log.info("并发执行结果 - 成功: {}, 失败: {}", successCount.get(), failCount.get());

        // 4. 验证库存数据一致性
        Map<String, Object> finalStorage = storageJdbcTemplate.queryForMap(storageSql, TEST_PRODUCT_AT);
        int finalResidue = ((Number) finalStorage.get("residue")).intValue();
        int finalUsed = ((Number) finalStorage.get("used")).intValue();

        int expectedDecrease = successCount.get() * eachOrderCount;
        assertEquals(initialResidue - expectedDecrease, finalResidue, 
                    "库存扣减应等于成功订单总数");
        assertEquals(initialUsed + expectedDecrease, finalUsed, 
                    "已用库存增加应等于成功订单总数");
        
        log.info("✓ 并发场景数据一致性验证通过");
        log.info("预期扣减: {}, 实际扣减: {}", expectedDecrease, initialResidue - finalResidue);

        log.info("========================================");
        log.info("场景13：并发场景分布式事务一致性验证 - 通过 ✓");
        log.info("========================================");
    }

    /**
     * 场景14：全局数据一致性检查
     */
    @Test
    @org.junit.jupiter.api.Order(6)
    @DisplayName("场景14：全局数据一致性检查")
    public void testGlobalDataConsistency() {
        log.info("========================================");
        log.info("场景14：全局数据一致性检查");
        log.info("========================================");

        // 1. 检查AT模式数据一致性
        String atStorageSql = "SELECT * FROM t_storage WHERE product_id = ?";
        Map<String, Object> atStorage = storageJdbcTemplate.queryForMap(atStorageSql, TEST_PRODUCT_AT);
        int atTotal = ((Number) atStorage.get("total")).intValue();
        int atUsed = ((Number) atStorage.get("used")).intValue();
        int atResidue = ((Number) atStorage.get("residue")).intValue();
        
        assertEquals(atTotal, atUsed + atResidue, "AT模式库存一致性检查");
        log.info("✓ AT模式全局数据一致：{} = {} + {}", atTotal, atUsed, atResidue);

        // 2. 检查TCC模式数据一致性
        String tccStorageSql = "SELECT * FROM t_storage_tcc WHERE product_id = ?";
        Map<String, Object> tccStorage = storageJdbcTemplate.queryForMap(tccStorageSql, TEST_PRODUCT_TCC);
        int tccTotal = ((Number) tccStorage.get("total")).intValue();
        int tccUsed = ((Number) tccStorage.get("used")).intValue();
        int tccFrozen = ((Number) tccStorage.get("frozen")).intValue();
        int tccResidue = ((Number) tccStorage.get("residue")).intValue();
        
        assertEquals(tccTotal, tccUsed + tccFrozen + tccResidue, "TCC模式库存一致性检查");
        log.info("✓ TCC模式全局数据一致：{} = {} + {} + {}", 
                tccTotal, tccUsed, tccFrozen, tccResidue);

        // 3. 检查undo_log清理情况
        String undoLogCountSql = "SELECT COUNT(*) FROM undo_log";
        int undoLogCount = jdbcTemplate.queryForObject(undoLogCountSql, Integer.class);
        assertEquals(0, undoLogCount, "所有undo_log应该已被清理");
        log.info("✓ undo_log清理检查通过，剩余记录数: {}", undoLogCount);

        log.info("========================================");
        log.info("场景14：全局数据一致性检查 - 通过 ✓");
        log.info("========================================");
    }
}
