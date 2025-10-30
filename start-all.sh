#!/bin/bash

# Seata Demo 一键启动脚本
# 使用方式: ./start-all.sh

set -e

echo "=========================================="
echo "  Seata分布式事务验证项目 - 一键启动"
echo "=========================================="
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查Docker是否安装
if ! command -v docker &> /dev/null; then
    echo -e "${RED}错误: Docker未安装，请先安装Docker${NC}"
    exit 1
fi

# 1. 启动MySQL
echo -e "${YELLOW}[1/3] 检查MySQL容器...${NC}"
if [ "$(docker ps -q -f name=seata-mysql)" ]; then
    echo -e "${GREEN}✓ MySQL容器已运行${NC}"
else
    if [ "$(docker ps -aq -f name=seata-mysql)" ]; then
        echo "启动已存在的MySQL容器..."
        docker start seata-mysql
    else
        echo "创建并启动MySQL容器..."
        docker run -d \
          --name seata-mysql \
          -p 3306:3306 \
          -e MYSQL_ROOT_PASSWORD=root123 \
          -e MYSQL_CHARACTER_SET_SERVER=utf8mb4 \
          -e MYSQL_COLLATION_SERVER=utf8mb4_general_ci \
          -e TZ=Asia/Shanghai \
          -v seata-mysql-data:/var/lib/mysql \
          mysql:5.7 \
          --character-set-server=utf8mb4 \
          --collation-server=utf8mb4_general_ci
    fi
    echo "等待MySQL启动..."
    sleep 10
    echo -e "${GREEN}✓ MySQL容器已启动${NC}"
fi

# 2. 启动Seata Server
echo -e "${YELLOW}[2/3] 检查Seata Server容器...${NC}"
if [ "$(docker ps -q -f name=seata-server)" ]; then
    echo -e "${GREEN}✓ Seata Server容器已运行${NC}"
else
    if [ "$(docker ps -aq -f name=seata-server)" ]; then
        echo "启动已存在的Seata Server容器..."
        docker start seata-server
    else
        echo "拉取Seata Server镜像..."
        docker pull seataio/seata-server:1.5.1
        #docker run -d -p 8091:8091 -p 7091:7091  --name seata-server seataio/seata-server:1.5.1
        #docker cp seata-server:/seata-server/resources /Developer/workspace/seata-demo/seata-config
        #docker rm -vf seata-server
        echo "创建并启动Seata Server容器..."
        docker run -d --name seata-server \
            -p 8091:8091 \
            -p 7091:7091 \
            -e SEATA_IP=localhost \
            -e SEATA_PORT=8091 \
            -v /Developer/workspace/seata-demo/seata-config:/seata-server/resources  \
            seataio/seata-server:1.5.1
    fi
    echo "等待Seata Server启动..."
    sleep 5
    echo -e "${GREEN}✓ Seata Server容器已启动${NC}"
fi

# 3. 检查Maven
echo -e "${YELLOW}[3/3] 检查Maven环境...${NC}"
if ! command -v mvn &> /dev/null; then
    echo -e "${RED}错误: Maven未安装，请先安装Maven${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Maven环境就绪${NC}"

echo ""
echo "=========================================="
echo "  基础环境启动完成！"
echo "=========================================="
echo ""
echo -e "${GREEN}下一步操作：${NC}"
echo ""
echo "1. 启动库存服务："
echo "   cd seata-service-b && mvn spring-boot:run"
echo ""
echo "2. 启动订单服务（新终端）："
echo "   cd seata-service-a && mvn spring-boot:run"
echo ""
echo "3. 访问测试界面："
echo "   订单服务Swagger: http://localhost:8081/swagger-ui/index.html"
echo "   库存服务Swagger: http://localhost:8082/swagger-ui/index.html"
echo ""
echo -e "${YELLOW}查看容器状态：${NC}"
echo "   docker ps | grep 'seata-mysql\|seata-server'"
echo ""
echo -e "${YELLOW}查看容器日志：${NC}"
echo "   docker logs -f seata-server"
echo ""
