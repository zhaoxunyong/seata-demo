#!/bin/bash

# Seata Demo 一键停止脚本
# 使用方式: ./stop-all.sh

echo "=========================================="
echo "  Seata分布式事务验证项目 - 停止服务"
echo "=========================================="
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 停止Seata Server
echo -e "${YELLOW}停止Seata Server容器...${NC}"
if [ "$(docker ps -q -f name=seata-server)" ]; then
    docker stop seata-server
    echo -e "${GREEN}✓ Seata Server已停止${NC}"
else
    echo "Seata Server容器未运行"
fi

# 停止MySQL（可选）
echo ""
read -p "是否停止MySQL容器? (y/n): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${YELLOW}停止MySQL容器...${NC}"
    if [ "$(docker ps -q -f name=seata-mysql)" ]; then
        docker stop seata-mysql
        echo -e "${GREEN}✓ MySQL已停止${NC}"
    else
        echo "MySQL容器未运行"
    fi
fi

echo ""
echo "=========================================="
echo "  服务已停止"
echo "=========================================="
echo ""
echo -e "${YELLOW}查看容器状态：${NC}"
docker ps -a | grep 'seata-mysql\|seata-server'
echo ""
echo -e "${YELLOW}重新启动：${NC}"
echo "   ./start-all.sh"
echo ""
echo -e "${YELLOW}完全删除容器（包括数据）：${NC}"
echo "   docker rm -f seata-server seata-mysql"
echo "   docker volume rm seata-mysql-data"
echo ""
