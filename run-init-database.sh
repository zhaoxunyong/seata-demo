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
