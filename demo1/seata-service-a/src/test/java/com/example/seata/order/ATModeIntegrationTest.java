package com.example.seata.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.seata.order.dto.OrderDTO;
import com.example.seata.order.entity.Order;
import com.example.seata.order.mapper.OrderMapper;
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
 * AT模式集成测试
 * 测试目标：验证AT模式的正常提交、回滚、undo_log生成与清理
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ATModeIntegrationTest {

    @Resource
    private OrderService orderService;

    @Resource
    private OrderMapper orderMapper;

    @Resource
    private JdbcTemplate jdbcTemplate;

    // 库存数据库的JdbcTemplate
    private JdbcTemplate storageJdbcTemplate;

    // 测试商品ID
    private static final String TEST_PRODUCT_ID = "P001";
    private static final String TEST_USER_ID = "U001";

    /**
     * 测试前准备
     */
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
     * 场景1：AT模式正常提交流程验证
     * 验证点：
     * 1. 订单创建成功
     * 2. 库存正确扣减
     * 3. undo_log生成与清理
     */
    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("场景1：AT模式正常提交流程")
    public void testATModeCommit() throws Exception {
        log.info("========================================");
        log.info("场景1：AT模式正常提交流程验证");
        log.info("========================================");

        // 1. 查询初始库存（使用库存数据库连接）
        String queryStorageSql = "SELECT * FROM t_storage WHERE product_id = ?";
        Map<String, Object> initialStorage = storageJdbcTemplate.queryForMap(queryStorageSql, TEST_PRODUCT_ID);
        int initialResidue = ((Number) initialStorage.get("residue")).intValue();
        int initialUsed = ((Number) initialStorage.get("used")).intValue();
        log.info("初始库存状态 - residue: {}, used: {}", initialResidue, initialUsed);

        // 2. 准备订单数据
        OrderDTO orderDTO = new OrderDTO();
        orderDTO.setUserId(TEST_USER_ID);
        orderDTO.setProductId(TEST_PRODUCT_ID);
        orderDTO.setCount(10);
        orderDTO.setAmount(new BigDecimal("100.00"));

        // 3. 创建订单
        log.info("发起订单创建请求...");
        Long orderId = orderService.createOrder(orderDTO);
        assertNotNull(orderId, "订单ID不应为空");
        log.info("订单创建成功，订单ID: {}", orderId);

        // 4. 验证订单数据
        Order order = orderMapper.selectById(orderId);
        assertNotNull(order, "订单记录应存在");
        assertEquals(TEST_USER_ID, order.getUserId(), "用户ID应匹配");
        assertEquals(TEST_PRODUCT_ID, order.getProductId(), "商品ID应匹配");
        assertEquals(10, order.getCount(), "购买数量应匹配");
        assertEquals("SUCCESS", order.getStatus(), "订单状态应为SUCCESS");
        log.info("✓ 订单数据验证通过");

        // 5. 验证库存扣减（等待几秒确保事务完成）
        Thread.sleep(2000);
        Map<String, Object> finalStorage = storageJdbcTemplate.queryForMap(queryStorageSql, TEST_PRODUCT_ID);
        int finalResidue = ((Number) finalStorage.get("residue")).intValue();
        int finalUsed = ((Number) finalStorage.get("used")).intValue();

        assertEquals(initialResidue - 10, finalResidue, "剩余库存应减少10");
        assertEquals(initialUsed + 10, finalUsed, "已用库存应增加10");
        log.info("最终库存状态 - residue: {}, used: {}", finalResidue, finalUsed);
        log.info("✓ 库存扣减验证通过");

        // 6. 验证undo_log已清理
        String undoLogQuery = "SELECT COUNT(*) FROM undo_log";
        int undoLogCount = jdbcTemplate.queryForObject(undoLogQuery, Integer.class);
        assertEquals(0, undoLogCount, "undo_log应该已被清理");
        log.info("✓ undo_log清理验证通过");

        log.info("========================================");
        log.info("场景1：AT模式正常提交流程验证 - 通过 ✓");
        log.info("========================================");
    }

    /**
     * 场景2：AT模式回滚流程验证
     * 验证点：
     * 1. 触发异常后订单数据回滚
     * 2. 库存数据回滚
     * 3. undo_log清理
     */
    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("场景2：AT模式回滚流程")
    public void testATModeRollback() throws Exception {
        log.info("========================================");
        log.info("场景2：AT模式回滚流程验证");
        log.info("========================================");

        // 1. 查询初始库存和订单数量
        String queryStorageSql = "SELECT * FROM t_storage WHERE product_id = ?";
        Map<String, Object> initialStorage = storageJdbcTemplate.queryForMap(queryStorageSql, TEST_PRODUCT_ID);
        int initialResidue = ((Number) initialStorage.get("residue")).intValue();
        int initialUsed = ((Number) initialStorage.get("used")).intValue();
        log.info("初始库存状态 - residue: {}, used: {}", initialResidue, initialUsed);

        String countOrderSql = "SELECT COUNT(*) FROM t_order WHERE user_id = ? AND product_id = ?";
        int initialOrderCount = jdbcTemplate.queryForObject(countOrderSql, Integer.class, "U002", TEST_PRODUCT_ID);
        log.info("初始订单数量: {}", initialOrderCount);

        // 2. 准备订单数据
        OrderDTO orderDTO = new OrderDTO();
        orderDTO.setUserId("U002");
        orderDTO.setProductId(TEST_PRODUCT_ID);
        orderDTO.setCount(5);
        orderDTO.setAmount(new BigDecimal("50.00"));

        // 3. 调用回滚接口，预期抛出异常
        log.info("发起订单创建请求（回滚场景）...");
        Exception exception = assertThrows(Exception.class, () -> {
            orderService.createOrderWithRollback(orderDTO);
        });
        assertTrue(exception.getMessage().contains("模拟异常"), "异常信息应包含'模拟异常'");
        log.info("✓ 成功触发异常：{}", exception.getMessage());

        // 4. 等待回滚完成
        Thread.sleep(3000);

        // 5. 验证订单未新增
        int finalOrderCount = jdbcTemplate.queryForObject(countOrderSql, Integer.class, "U002", TEST_PRODUCT_ID);
        assertEquals(initialOrderCount, finalOrderCount, "订单数量应不变（已回滚）");
        log.info("最终订单数量: {}", finalOrderCount);
        log.info("✓ 订单回滚验证通过");

        // 6. 验证库存未变化
        Map<String, Object> finalStorage = storageJdbcTemplate.queryForMap(queryStorageSql, TEST_PRODUCT_ID);
        int finalResidue = ((Number) finalStorage.get("residue")).intValue();
        int finalUsed = ((Number) finalStorage.get("used")).intValue();

        assertEquals(initialResidue, finalResidue, "剩余库存应不变（已回滚）");
        assertEquals(initialUsed, finalUsed, "已用库存应不变（已回滚）");
        log.info("最终库存状态 - residue: {}, used: {}", finalResidue, finalUsed);
        log.info("✓ 库存回滚验证通过");

        // 7. 验证undo_log已清理
        String undoLogQuery = "SELECT COUNT(*) FROM undo_log";
        int undoLogCount = jdbcTemplate.queryForObject(undoLogQuery, Integer.class);
        assertEquals(0, undoLogCount, "undo_log应该已被清理");
        log.info("✓ undo_log清理验证通过");

        log.info("========================================");
        log.info("场景2：AT模式回滚流程验证 - 通过 ✓");
        log.info("========================================");
    }

    /**
     * 场景3：库存不足异常处理
     * 验证点：
     * 1. 库存不足时抛出异常
     * 2. 订单未创建
     * 3. 库存数据一致性
     */
    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("场景3：库存不足异常处理")
    public void testInsufficientStock() throws Exception {
        log.info("========================================");
        log.info("场景3：库存不足异常处理");
        log.info("========================================");

        // 1. 查询当前库存
        String queryStorageSql = "SELECT * FROM t_storage WHERE product_id = ?";
        Map<String, Object> storage = storageJdbcTemplate.queryForMap(queryStorageSql, TEST_PRODUCT_ID);
        int currentResidue = ((Number) storage.get("residue")).intValue();
        log.info("当前剩余库存: {}", currentResidue);

        // 2. 准备超出库存的订单数据
        OrderDTO orderDTO = new OrderDTO();
        orderDTO.setUserId("U003");
        orderDTO.setProductId(TEST_PRODUCT_ID);
        orderDTO.setCount(currentResidue + 100); // 超出库存
        orderDTO.setAmount(new BigDecimal("1000.00"));

        // 3. 执行订单创建，预期失败
        log.info("尝试创建超出库存的订单（需要数量: {}）...", orderDTO.getCount());
        Exception exception = assertThrows(Exception.class, () -> {
            orderService.createOrder(orderDTO);
        });
        assertTrue(exception.getMessage().contains("库存不足") || 
                   exception.getMessage().contains("扣减库存失败"), 
                   "异常信息应包含库存不足相关信息");
        log.info("✓ 成功捕获库存不足异常：{}", exception.getMessage());

        // 4. 等待事务完成
        Thread.sleep(2000);

        // 5. 验证订单未创建
        String countOrderSql = "SELECT COUNT(*) FROM t_order WHERE user_id = ?";
        int orderCount = jdbcTemplate.queryForObject(countOrderSql, Integer.class, "U003");
        assertEquals(0, orderCount, "库存不足时订单不应被创建");
        log.info("✓ 订单未创建验证通过");

        // 6. 验证库存未变化
        Map<String, Object> finalStorage = storageJdbcTemplate.queryForMap(queryStorageSql, TEST_PRODUCT_ID);
        int finalResidue = ((Number) finalStorage.get("residue")).intValue();
        assertEquals(currentResidue, finalResidue, "库存应保持不变");
        log.info("✓ 库存数据一致性验证通过");

        log.info("========================================");
        log.info("场景3：库存不足异常处理 - 通过 ✓");
        log.info("========================================");
    }

    /**
     * 场景4：数据一致性验证
     * 验证total = used + residue恒等式
     */
    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("场景4：数据一致性验证")
    public void testDataConsistency() {
        log.info("========================================");
        log.info("场景4：数据一致性验证");
        log.info("========================================");

        String queryStorageSql = "SELECT * FROM t_storage WHERE product_id = ?";
        Map<String, Object> storage = storageJdbcTemplate.queryForMap(queryStorageSql, TEST_PRODUCT_ID);
        
        int total = ((Number) storage.get("total")).intValue();
        int used = ((Number) storage.get("used")).intValue();
        int residue = ((Number) storage.get("residue")).intValue();

        log.info("库存数据 - total: {}, used: {}, residue: {}", total, used, residue);
        
        assertEquals(total, used + residue, "库存恒等式验证：total应等于used + residue");
        log.info("✓ 数据一致性验证通过：{} = {} + {}", total, used, residue);

        log.info("========================================");
        log.info("场景4：数据一致性验证 - 通过 ✓");
        log.info("========================================");
    }
}
