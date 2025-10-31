package com.example.seata.order;

import com.example.seata.order.dto.OrderDTO;
import com.example.seata.order.mapper.OrderTCCMapper;
import com.example.seata.order.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TCC模式集成测试
 * 测试目标：验证TCC模式的Try-Confirm、Try-Cancel流程
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TCCModeIntegrationTest {

    @Resource
    private OrderService orderService;

    @Resource
    private OrderTCCMapper orderTCCMapper;

    @Resource
    private JdbcTemplate jdbcTemplate;

    // 库存数据库的JdbcTemplate
    private JdbcTemplate storageJdbcTemplate;

    // 测试商品ID
    private static final String TEST_PRODUCT_ID_TCC = "P002";
    private static final String TEST_USER_ID = "U003";

    @BeforeEach
    public void setUp() {
        log.info("=== 测试准备：开始 ===");
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
        log.info("=== 测试结束 ===\n");
    }

    /**
     * 场景5：TCC模式Try-Confirm正常流程验证
     * 验证点：
     * 1. Try阶段：订单创建（状态INIT），库存冻结
     * 2. Confirm阶段：订单确认（状态SUCCESS），frozen转为used
     * 3. 数据一致性：total = used + frozen + residue
     */
    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("场景5：TCC模式Try-Confirm正常流程")
    public void testTCCModeCommit() throws Exception {
        log.info("========================================");
        log.info("场景5：TCC模式Try-Confirm正常流程验证");
        log.info("========================================");

        // 1. 查询初始库存状态
        String queryStorageSql = "SELECT * FROM t_storage_tcc WHERE product_id = ?";
        Map<String, Object> initialStorage = storageJdbcTemplate.queryForMap(queryStorageSql, TEST_PRODUCT_ID_TCC);
        int initialResidue = ((Number) initialStorage.get("residue")).intValue();
        int initialUsed = ((Number) initialStorage.get("used")).intValue();
        int initialFrozen = ((Number) initialStorage.get("frozen")).intValue();
        log.info("初始库存状态 - residue: {}, used: {}, frozen: {}", initialResidue, initialUsed, initialFrozen);

        // 2. 准备订单数据
        OrderDTO orderDTO = new OrderDTO();
        orderDTO.setUserId(TEST_USER_ID);
        orderDTO.setProductId(TEST_PRODUCT_ID_TCC);
        orderDTO.setCount(15);
        orderDTO.setAmount(new BigDecimal("150.00"));

        // 3. 创建订单（TCC模式）
        log.info("发起TCC订单创建请求...");
        String result = orderService.createOrderTCC(orderDTO);
        assertNotNull(result, "返回结果不应为空");
        log.info("TCC订单创建响应: {}", result);

        // 4. 等待Confirm阶段完成
        Thread.sleep(3000);

        // 5. 验证订单状态（Confirm后应为SUCCESS）
        String queryOrderSql = "SELECT * FROM t_order_tcc WHERE user_id = ? AND product_id = ? ORDER BY id DESC LIMIT 1";
        Map<String, Object> order = jdbcTemplate.queryForMap(queryOrderSql, TEST_USER_ID, TEST_PRODUCT_ID_TCC);
        String orderStatus = (String) order.get("status");
        assertEquals("SUCCESS", orderStatus, "Confirm后订单状态应为SUCCESS");
        log.info("✓ 订单状态验证通过：status = {}", orderStatus);

        // 6. 验证库存变化（Confirm后：frozen转为used）
        Map<String, Object> finalStorage = storageJdbcTemplate.queryForMap(queryStorageSql, TEST_PRODUCT_ID_TCC);
        int finalResidue = ((Number) finalStorage.get("residue")).intValue();
        int finalUsed = ((Number) finalStorage.get("used")).intValue();
        int finalFrozen = ((Number) finalStorage.get("frozen")).intValue();

        assertEquals(initialResidue - 15, finalResidue, "剩余库存应减少15");
        assertEquals(initialUsed + 15, finalUsed, "已用库存应增加15");
        assertEquals(initialFrozen, finalFrozen, "冻结库存应回归初始值（Try时+15，Confirm时-15）");
        log.info("最终库存状态 - residue: {}, used: {}, frozen: {}", finalResidue, finalUsed, finalFrozen);
        log.info("✓ 库存变化验证通过");

        // 7. 验证数据一致性：total = used + frozen + residue
        int total = ((Number) finalStorage.get("total")).intValue();
        assertEquals(total, finalUsed + finalFrozen + finalResidue, 
                    "TCC库存恒等式：total = used + frozen + residue");
        log.info("✓ 数据一致性验证通过：{} = {} + {} + {}", total, finalUsed, finalFrozen, finalResidue);

        log.info("========================================");
        log.info("场景5：TCC模式Try-Confirm正常流程验证 - 通过 ✓");
        log.info("========================================");
    }

    /**
     * 场景6：TCC模式Try-Cancel回滚流程验证
     * 验证点：
     * 1. Try阶段：订单创建，库存冻结
     * 2. Cancel阶段：订单取消（状态CANCEL），冻结库存释放
     * 3. 库存数据恢复到初始状态
     */
    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("场景6：TCC模式Try-Cancel回滚流程")
    public void testTCCModeRollback() throws Exception {
        log.info("========================================");
        log.info("场景6：TCC模式Try-Cancel回滚流程验证");
        log.info("========================================");

        // 1. 查询初始库存状态
        String queryStorageSql = "SELECT * FROM t_storage_tcc WHERE product_id = ?";
        Map<String, Object> initialStorage = storageJdbcTemplate.queryForMap(queryStorageSql, TEST_PRODUCT_ID_TCC);
        int initialResidue = ((Number) initialStorage.get("residue")).intValue();
        int initialUsed = ((Number) initialStorage.get("used")).intValue();
        int initialFrozen = ((Number) initialStorage.get("frozen")).intValue();
        log.info("初始库存状态 - residue: {}, used: {}, frozen: {}", initialResidue, initialUsed, initialFrozen);

        // 2. 准备订单数据
        OrderDTO orderDTO = new OrderDTO();
        orderDTO.setUserId("U004");
        orderDTO.setProductId(TEST_PRODUCT_ID_TCC);
        orderDTO.setCount(8);
        orderDTO.setAmount(new BigDecimal("80.00"));

        // 3. 调用TCC回滚接口，预期抛出异常
        log.info("发起TCC订单创建请求（回滚场景）...");
        Exception exception = assertThrows(Exception.class, () -> {
            orderService.createOrderTCCWithRollback(orderDTO);
        });
        assertTrue(exception.getMessage().contains("模拟异常"), "异常信息应包含'模拟异常'");
        log.info("✓ 成功触发异常：{}", exception.getMessage());

        // 4. 等待Cancel阶段完成
        Thread.sleep(3000);

        // 5. 验证订单状态（Cancel后应为CANCEL）
        String queryOrderSql = "SELECT * FROM t_order_tcc WHERE user_id = ? AND product_id = ? ORDER BY id DESC LIMIT 1";
        Map<String, Object> order = jdbcTemplate.queryForMap(queryOrderSql, "U004", TEST_PRODUCT_ID_TCC);
        String orderStatus = (String) order.get("status");
        assertEquals("CANCEL", orderStatus, "Cancel后订单状态应为CANCEL");
        log.info("✓ 订单状态验证通过：status = {}", orderStatus);

        // 6. 验证库存恢复（Cancel后：frozen释放回residue）
        Map<String, Object> finalStorage = storageJdbcTemplate.queryForMap(queryStorageSql, TEST_PRODUCT_ID_TCC);
        int finalResidue = ((Number) finalStorage.get("residue")).intValue();
        int finalUsed = ((Number) finalStorage.get("used")).intValue();
        int finalFrozen = ((Number) finalStorage.get("frozen")).intValue();

        assertEquals(initialResidue, finalResidue, "剩余库存应恢复到初始值（Try时-8，Cancel时+8）");
        assertEquals(initialUsed, finalUsed, "已用库存应不变");
        assertEquals(initialFrozen, finalFrozen, "冻结库存应恢复到初始值（Try时+8，Cancel时-8）");
        log.info("最终库存状态 - residue: {}, used: {}, frozen: {}", finalResidue, finalUsed, finalFrozen);
        log.info("✓ 库存恢复验证通过");

        // 7. 验证数据一致性
        int total = ((Number) finalStorage.get("total")).intValue();
        assertEquals(total, finalUsed + finalFrozen + finalResidue, 
                    "TCC库存恒等式：total = used + frozen + residue");
        log.info("✓ 数据一致性验证通过：{} = {} + {} + {}", total, finalUsed, finalFrozen, finalResidue);

        log.info("========================================");
        log.info("场景6：TCC模式Try-Cancel回滚流程验证 - 通过 ✓");
        log.info("========================================");
    }

    /**
     * 场景7：TCC模式库存不足异常处理
     * 验证点：
     * 1. 库存不足时Try阶段失败
     * 2. 订单未创建或状态为CANCEL
     * 3. 库存数据不变
     */
    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("场景7：TCC模式库存不足异常处理")
    public void testTCCInsufficientStock() throws Exception {
        log.info("========================================");
        log.info("场景7：TCC模式库存不足异常处理");
        log.info("========================================");

        // 1. 查询当前库存
        String queryStorageSql = "SELECT * FROM t_storage_tcc WHERE product_id = ?";
        Map<String, Object> storage = storageJdbcTemplate.queryForMap(queryStorageSql, TEST_PRODUCT_ID_TCC);
        int currentResidue = ((Number) storage.get("residue")).intValue();
        log.info("当前剩余库存: {}", currentResidue);

        // 2. 准备超出库存的订单数据
        OrderDTO orderDTO = new OrderDTO();
        orderDTO.setUserId("U005");
        orderDTO.setProductId(TEST_PRODUCT_ID_TCC);
        orderDTO.setCount(currentResidue + 50); // 超出库存
        orderDTO.setAmount(new BigDecimal("500.00"));

        // 3. 执行TCC订单创建，预期失败
        log.info("尝试创建超出库存的TCC订单（需要数量: {}）...", orderDTO.getCount());
        Exception exception = assertThrows(Exception.class, () -> {
            orderService.createOrderTCC(orderDTO);
        });
        assertTrue(exception.getMessage().contains("库存不足") || 
                   exception.getMessage().contains("冻结库存失败"), 
                   "异常信息应包含库存不足相关信息");
        log.info("✓ 成功捕获库存不足异常：{}", exception.getMessage());

        // 4. 等待事务完成
        Thread.sleep(2000);

        // 5. 验证库存未变化
        Map<String, Object> finalStorage = storageJdbcTemplate.queryForMap(queryStorageSql, TEST_PRODUCT_ID_TCC);
        int finalResidue = ((Number) finalStorage.get("residue")).intValue();
        int finalFrozen = ((Number) finalStorage.get("frozen")).intValue();
        
        assertEquals(currentResidue, finalResidue, "剩余库存应保持不变");
        log.info("✓ 库存数据一致性验证通过");

        log.info("========================================");
        log.info("场景7：TCC模式库存不足异常处理 - 通过 ✓");
        log.info("========================================");
    }

    /**
     * 场景8：TCC模式数据一致性验证
     * 验证total = used + frozen + residue恒等式
     */
    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("场景8：TCC模式数据一致性验证")
    public void testTCCDataConsistency() {
        log.info("========================================");
        log.info("场景8：TCC模式数据一致性验证");
        log.info("========================================");

        String queryStorageSql = "SELECT * FROM t_storage_tcc WHERE product_id = ?";
        Map<String, Object> storage = storageJdbcTemplate.queryForMap(queryStorageSql, TEST_PRODUCT_ID_TCC);
        
        int total = ((Number) storage.get("total")).intValue();
        int used = ((Number) storage.get("used")).intValue();
        int frozen = ((Number) storage.get("frozen")).intValue();
        int residue = ((Number) storage.get("residue")).intValue();

        log.info("TCC库存数据 - total: {}, used: {}, frozen: {}, residue: {}", 
                total, used, frozen, residue);
        
        assertEquals(total, used + frozen + residue, 
                    "TCC库存恒等式验证：total应等于used + frozen + residue");
        log.info("✓ 数据一致性验证通过：{} = {} + {} + {}", total, used, frozen, residue);

        log.info("========================================");
        log.info("场景8：TCC模式数据一致性验证 - 通过 ✓");
        log.info("========================================");
    }
}
