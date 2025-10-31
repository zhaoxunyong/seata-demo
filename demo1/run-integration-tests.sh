#!/bin/bash

# Seata集成测试执行脚本
# 功能：批量运行所有集成测试并生成测试报告

echo "=========================================="
echo "Seata 集成测试执行脚本"
echo "=========================================="
echo ""

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 项目根目录
PROJECT_ROOT=$(cd "$(dirname "$0")" && pwd)
SERVICE_A_DIR="$PROJECT_ROOT/seata-service-a"
SERVICE_B_DIR="$PROJECT_ROOT/seata-service-b"

# 测试结果变量
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# 检查环境
echo "1. 检查测试环境..."
echo "-------------------------------------------"

# 检查MySQL
if docker ps | grep -q seata-mysql; then
    echo -e "${GREEN}✓${NC} MySQL容器运行中"
else
    echo -e "${RED}✗${NC} MySQL容器未运行"
    echo "   请先启动MySQL: ./start-all.sh 或 docker start seata-mysql"
    exit 1
fi

# 检查Seata Server
if docker ps | grep -q seata-server; then
    echo -e "${GREEN}✓${NC} Seata Server运行中"
else
    echo -e "${YELLOW}⚠${NC} Seata Server未运行"
    echo "   某些测试可能需要Seata Server"
    echo "   启动命令: docker start seata-server"
fi

# 检查微服务
if lsof -i :8081 > /dev/null 2>&1; then
    echo -e "${GREEN}✓${NC} 订单服务(8081)运行中"
else
    echo -e "${YELLOW}⚠${NC} 订单服务(8081)未运行"
    echo "   端到端测试需要两个服务都运行"
fi

if lsof -i :8082 > /dev/null 2>&1; then
    echo -e "${GREEN}✓${NC} 库存服务(8082)运行中"
else
    echo -e "${YELLOW}⚠${NC} 库存服务(8082)未运行"
    echo "   端到端测试需要两个服务都运行"
fi

echo ""

# 询问是否重置测试数据
read -p "是否重置测试数据? (y/n, 默认y): " RESET_DATA
RESET_DATA=${RESET_DATA:-y}

if [ "$RESET_DATA" = "y" ] || [ "$RESET_DATA" = "Y" ]; then
    echo ""
    echo "2. 重置测试数据..."
    echo "-------------------------------------------"
    
    docker exec -i seata-mysql mysql -uroot -proot123 << EOF
-- 清空订单数据
USE seata_order;
TRUNCATE TABLE t_order;
TRUNCATE TABLE t_order_tcc;
TRUNCATE TABLE undo_log;

-- 重置库存数据
USE seata_storage;
UPDATE t_storage SET used = 0, residue = 100 WHERE product_id = 'P001';
UPDATE t_storage_tcc SET used = 0, frozen = 0, residue = 100 WHERE product_id = 'P002';
TRUNCATE TABLE undo_log;

SELECT 'AT模式库存数据:' AS info;
SELECT product_id, total, used, residue FROM t_storage WHERE product_id IN ('P001', 'P003');

SELECT 'TCC模式库存数据:' AS info;
SELECT product_id, total, used, frozen, residue FROM t_storage_tcc WHERE product_id IN ('P002', 'P004');
EOF

    echo -e "${GREEN}✓${NC} 测试数据已重置"
else
    echo -e "${YELLOW}⚠${NC} 跳过数据重置"
fi

echo ""

# 运行测试
echo "3. 开始运行集成测试..."
echo "=========================================="
echo ""

# 函数：运行单个测试类
run_test() {
    local service_dir=$1
    local test_class=$2
    local test_name=$3
    
    echo "【测试】$test_name"
    echo "-------------------------------------------"
    
    cd "$service_dir"
    
    # 运行测试并捕获输出
    TEST_OUTPUT=$(mktemp)
    if mvn -q test -Dtest="$test_class" -DfailIfNoTests=false > "$TEST_OUTPUT" 2>&1; then
        echo -e "${GREEN}✓ 通过${NC}"
        PASSED_TESTS=$((PASSED_TESTS + 1))
        # 提取测试数量
        local tests=$(grep -oE "Tests run: [0-9]+" "$TEST_OUTPUT" | grep -oE "[0-9]+" | head -1)
        if [ -n "$tests" ]; then
            TOTAL_TESTS=$((TOTAL_TESTS + tests))
        fi
    else
        echo -e "${RED}✗ 失败${NC}"
        FAILED_TESTS=$((FAILED_TESTS + 1))
        echo "错误详情:"
        tail -50 "$TEST_OUTPUT"
    fi
    
    rm -f "$TEST_OUTPUT"
    echo ""
}

# 3.1 订单服务 - AT模式测试
run_test "$SERVICE_A_DIR" "ATModeIntegrationTest" "订单服务 - AT模式集成测试"

# 3.2 订单服务 - TCC模式测试  
run_test "$SERVICE_A_DIR" "TCCModeIntegrationTest" "订单服务 - TCC模式集成测试"

# 3.3 库存服务测试
run_test "$SERVICE_B_DIR" "StorageServiceIntegrationTest" "库存服务集成测试"

# 3.4 端到端集成测试
if lsof -i :8081 > /dev/null 2>&1 && lsof -i :8082 > /dev/null 2>&1; then
    run_test "$SERVICE_A_DIR" "EndToEndIntegrationTest" "端到端集成测试"
else
    echo "【跳过】端到端集成测试"
    echo -e "${YELLOW}原因: 订单服务或库存服务未运行${NC}"
    echo ""
fi

# 测试总结
echo "=========================================="
echo "测试执行完成"
echo "=========================================="
echo ""
echo "测试统计:"
echo "  总测试场景数: $TOTAL_TESTS"
echo -e "  通过测试类: ${GREEN}$PASSED_TESTS${NC}"
if [ $FAILED_TESTS -gt 0 ]; then
    echo -e "  失败测试类: ${RED}$FAILED_TESTS${NC}"
else
    echo -e "  失败测试类: ${GREEN}$FAILED_TESTS${NC}"
fi
echo ""

# 查看最终数据状态
echo "4. 测试后数据状态..."
echo "-------------------------------------------"

docker exec -i seata-mysql mysql -uroot -proot123 << EOF
USE seata_storage;

SELECT 'AT模式库存数据:' AS info;
SELECT product_id, total, used, residue, 
       (total - used - residue) AS diff,
       CASE WHEN total = used + residue THEN 'PASS' ELSE 'FAIL' END AS consistency
FROM t_storage 
WHERE product_id IN ('P001', 'P003');

SELECT 'TCC模式库存数据:' AS info;
SELECT product_id, total, used, frozen, residue,
       (total - used - frozen - residue) AS diff,
       CASE WHEN total = used + frozen + residue THEN 'PASS' ELSE 'FAIL' END AS consistency
FROM t_storage_tcc 
WHERE product_id IN ('P002', 'P004');

SELECT 'undo_log清理状态:' AS info;
SELECT COUNT(*) AS remaining_undo_logs FROM undo_log;
EOF

echo ""

# 退出码
if [ $FAILED_TESTS -gt 0 ]; then
    echo -e "${RED}部分测试失败，请查看上面的错误详情${NC}"
    exit 1
else
    echo -e "${GREEN}所有测试通过！ ✓${NC}"
    exit 0
fi
