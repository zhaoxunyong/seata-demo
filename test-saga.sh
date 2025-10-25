#!/bin/bash

# Saga模式测试脚本

echo "=========================================="
echo "Seata Saga模式测试脚本"
echo "=========================================="
echo ""

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查服务是否运行
echo "1. 检查服务状态..."
echo "-------------------------------------------"

# 检查订单服务
if lsof -i :8081 > /dev/null 2>&1; then
    echo -e "${GREEN}✓${NC} 订单服务(8081)运行中"
else
    echo -e "${RED}✗${NC} 订单服务(8081)未运行"
    echo "   请先启动订单服务: cd seata-service-a && mvn spring-boot:run"
    exit 1
fi

# 检查库存服务
if lsof -i :8082 > /dev/null 2>&1; then
    echo -e "${GREEN}✓${NC} 库存服务(8082)运行中"
else
    echo -e "${RED}✗${NC} 库存服务(8082)未运行"
    echo "   请先启动库存服务: cd seata-service-b && mvn spring-boot:run"
    exit 1
fi

echo ""
echo "2. 测试Saga模式正常提交..."
echo "-------------------------------------------"

# Saga模式正常提交测试
echo "发送Saga模式正常提交请求..."
curl -X POST "http://localhost:8081/order-saga/create" \
  -H "Content-Type: application/json" \
  -d '{
  "userId": "U001",
  "productId": "P001",
  "count": 5,
  "amount": 50.00
}' | jq .

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓${NC} Saga模式正常提交测试完成"
else
    echo -e "${RED}✗${NC} Saga模式正常提交测试失败"
fi

echo ""
echo "3. 测试Saga模式回滚..."
echo "-------------------------------------------"

# Saga模式回滚测试
echo "发送Saga模式回滚请求..."
curl -X POST "http://localhost:8081/order-saga/create-rollback" \
  -H "Content-Type: application/json" \
  -d '{
  "userId": "U001",
  "productId": "P001",
  "count": 3,
  "amount": 30.00
}' | jq .

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓${NC} Saga模式回滚测试完成（异常为预期结果）"
else
    echo -e "${RED}✗${NC} Saga模式回滚测试失败"
fi

echo ""
echo "4. 验证数据状态..."
echo "-------------------------------------------"

# 验证订单Saga表数据
echo "订单Saga表数据:"
docker exec -i seata-mysql mysql -uroot -proot123 -e "SELECT * FROM seata_order.t_order_saga ORDER BY id DESC LIMIT 5;" 2>/dev/null

# 验证库存Saga表数据
echo "库存Saga表数据:"
docker exec -i seata-mysql mysql -uroot -proot123 -e "SELECT * FROM seata_storage.t_storage_saga WHERE product_id = 'P001';" 2>/dev/null

echo ""
echo -e "${GREEN}✓${NC} Saga模式测试脚本执行完成"
echo "   请检查以上结果确认Saga模式是否正常工作"