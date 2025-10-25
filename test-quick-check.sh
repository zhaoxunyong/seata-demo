#!/bin/bash

# 快速测试检查脚本
# 运行简单的数据一致性测试验证修复是否成功

echo "=========================================="
echo "Seata 集成测试快速检查"
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

# 检查MySQL
echo "1. 检查MySQL容器..."
if docker ps | grep -q seata-mysql; then
    echo -e "${GREEN}✓${NC} MySQL容器运行中"
else
    echo -e "${RED}✗${NC} MySQL容器未运行"
    echo "   请先启动MySQL: docker start seata-mysql"
    exit 1
fi

echo ""
echo "2. 运行快速测试..."
echo "=========================================="

# 运行数据一致性测试（最简单的测试）
cd "$SERVICE_A_DIR"

echo ""
echo "【测试1】AT模式数据一致性"
echo "-------------------------------------------"
mvn -q test -Dtest=ATModeIntegrationTest#testDataConsistency -DfailIfNoTests=false > /dev/null
if [[ $? == 0 ]]; then
    echo -e "${GREEN}✓ AT模式测试通过${NC}"
else
    echo -e "${RED}✗ AT模式测试失败${NC}"
    echo "查看详细错误: mvn test -Dtest=ATModeIntegrationTest#testDataConsistency"
fi

echo ""
echo "【测试2】TCC模式数据一致性"
echo "-------------------------------------------"
mvn -q test -Dtest=TCCModeIntegrationTest#testTCCDataConsistency -DfailIfNoTests=false > /dev/null
if [[ $? == 0 ]]; then
    echo -e "${GREEN}✓ TCC模式测试通过${NC}"
else
    echo -e "${RED}✗ TCC模式测试失败${NC}"
    echo "查看详细错误: mvn test -Dtest=TCCModeIntegrationTest#testTCCDataConsistency"
fi

echo ""
echo "=========================================="
echo "快速检查完成"
echo "=========================================="
echo ""
echo "如需运行完整测试，请执行:"
echo "  ./run-integration-tests.sh"
echo ""
