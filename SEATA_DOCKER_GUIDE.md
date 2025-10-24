# Seata Server Docker 部署指南

## 概述

本文档详细说明如何使用Docker部署Seata Server，适用于Seata分布式事务技术验证项目。

## 前置条件

- Docker已安装（版本17.05+）
- 网络连接正常（需要拉取Docker镜像）
- 端口8091未被占用

## 部署方式

### 方式一：快速启动（推荐用于验证）

适用于快速验证功能，使用File存储模式。

```bash
# 1. 拉取Seata Server镜像
docker pull seataio/seata-server:1.4.2

# 2. 启动容器
docker run -d \
  --name seata-server \
  -p 8091:8091 \
  -e SEATA_IP=127.0.0.1 \
  -e SEATA_PORT=8091 \
  seataio/seata-server:1.4.2

# 3. 查看容器状态
docker ps | grep seata-server

# 4. 查看日志
docker logs -f seata-server
```

**预期日志输出**：
```
Server started, listen port: 8091
```

### 方式二：自定义配置启动

适用于需要自定义配置的场景。

#### 步骤1：准备配置文件

```bash
# 创建配置目录
mkdir -p ~/seata-server/resources
cd ~/seata-server/resources
```

#### 步骤2：创建file.conf

```bash
cat > file.conf << 'EOF'
## transaction log store, only used in seata-server
store {
  ## store mode: file、db、redis
  mode = "file"
  
  ## file store property
  file {
    ## store location dir
    dir = "sessionStore"
    ## branch session size , if exceeded first try compress lockkey, still exceeded throws exceptions
    maxBranchSessionSize = 16384
    ## globe session size , if exceeded throws exceptions
    maxGlobalSessionSize = 512
    ## file buffer size , if exceeded allocate new buffer
    fileWriteBufferCacheSize = 16384
    ## when recover batch read size
    sessionReloadReadSize = 100
    ## async, sync
    flushDiskMode = async
  }
}
EOF
```

#### 步骤3：创建registry.conf

```bash
cat > registry.conf << 'EOF'
registry {
  # file 、nacos 、eureka、redis、zk、consul、etcd3、sofa
  type = "file"
  
  file {
    name = "file.conf"
  }
}

config {
  # file、nacos 、apollo、zk、consul、etcd3
  type = "file"
  
  file {
    name = "file.conf"
  }
}
EOF
```

#### 步骤4：启动容器（挂载配置）

```bash
docker run -d \
  --name seata-server \
  -p 8091:8091 \
  -e SEATA_IP=127.0.0.1 \
  -e SEATA_PORT=8091 \
  -v ~/seata-server/resources:/seata-server/resources \
  seataio/seata-server:1.4.2
```

### 方式三：使用Docker Compose（推荐用于生产）

适用于需要同时管理多个容器的场景。

#### 创建docker-compose.yml

```yaml
version: '3.8'

services:
  seata-mysql:
    image: mysql:5.7
    container_name: seata-mysql
    ports:
      - "3306:3306"
    environment:
      MYSQL_ROOT_PASSWORD: root123
      MYSQL_CHARACTER_SET_SERVER: utf8mb4
      MYSQL_COLLATION_SERVER: utf8mb4_general_ci
      TZ: Asia/Shanghai
    volumes:
      - seata-mysql-data:/var/lib/mysql
    networks:
      - seata-network
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      timeout: 5s
      retries: 5

  seata-server:
    image: seataio/seata-server:1.4.2
    container_name: seata-server
    ports:
      - "8091:8091"
    environment:
      SEATA_IP: seata-server
      SEATA_PORT: 8091
    depends_on:
      - seata-mysql
    networks:
      - seata-network
    healthcheck:
      test: ["CMD", "nc", "-z", "localhost", "8091"]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  seata-mysql-data:
    name: seata-mysql-data

networks:
  seata-network:
    name: seata-network
    driver: bridge
```

#### 启动所有服务

```bash
# 启动
docker-compose up -d

# 查看状态
docker-compose ps

# 查看日志
docker-compose logs -f seata-server

# 停止
docker-compose down

# 停止并删除数据卷
docker-compose down -v
```

## 验证部署

### 1. 检查容器状态

```bash
# 查看运行中的容器
docker ps | grep seata-server

# 查看所有容器（包括已停止）
docker ps -a | grep seata-server

# 查看容器详细信息
docker inspect seata-server
```

### 2. 查看日志

```bash
# 查看实时日志
docker logs -f seata-server

# 查看最后100行日志
docker logs --tail 100 seata-server

# 查看指定时间后的日志
docker logs --since 2025-10-24T21:00:00 seata-server
```

### 3. 测试端口连通性

```bash
# 使用telnet测试
telnet 127.0.0.1 8091

# 使用nc测试
nc -zv 127.0.0.1 8091

# 使用lsof查看端口
lsof -i :8091
```

### 4. 进入容器内部检查

```bash
# 进入容器
docker exec -it seata-server sh

# 查看进程
ps aux | grep seata

# 查看配置文件
cat /seata-server/resources/registry.conf

# 退出容器
exit
```

## 常用管理命令

### 容器生命周期管理

```bash
# 启动容器
docker start seata-server

# 停止容器
docker stop seata-server

# 重启容器
docker restart seata-server

# 删除容器（需先停止）
docker stop seata-server
docker rm seata-server

# 强制删除容器
docker rm -f seata-server
```

### 日志管理

```bash
# 清空日志（Docker不支持直接清空，需要删除日志文件）
truncate -s 0 $(docker inspect --format='{{.LogPath}}' seata-server)

# 限制日志大小（在docker run时配置）
docker run -d \
  --name seata-server \
  --log-opt max-size=10m \
  --log-opt max-file=3 \
  -p 8091:8091 \
  seataio/seata-server:1.4.2
```

### 资源监控

```bash
# 查看容器资源使用情况
docker stats seata-server

# 查看容器占用的磁盘空间
docker system df

# 查看容器的详细资源使用
docker inspect seata-server | grep -A 10 "Memory"
```

## 故障排查

### 问题1：容器无法启动

**症状**：`docker ps`看不到seata-server容器

**排查步骤**：

```bash
# 1. 查看所有容器（包括已停止）
docker ps -a | grep seata-server

# 2. 查看容器退出原因
docker logs seata-server

# 3. 查看容器详细信息
docker inspect seata-server
```

**常见原因**：
- 端口8091被占用
- 配置文件错误
- 内存不足
- 镜像损坏

**解决方案**：
```bash
# 检查端口占用
lsof -i :8091

# 删除并重新创建容器
docker rm -f seata-server
docker run -d --name seata-server -p 8091:8091 seataio/seata-server:1.4.2
```

### 问题2：客户端无法连接Seata Server

**症状**：微服务日志显示"can not connect to seata server"

**排查步骤**：

```bash
# 1. 检查容器是否运行
docker ps | grep seata-server

# 2. 检查端口映射
docker port seata-server

# 3. 测试端口连通性
telnet 127.0.0.1 8091
```

**客户端配置检查**：

如果使用Docker运行微服务，需要注意网络配置：

```yaml
# 同一Docker网络内
seata:
  service:
    grouplist:
      default: seata-server:8091  # 使用容器名

# 宿主机访问容器
seata:
  service:
    grouplist:
      default: 127.0.0.1:8091  # 使用宿主机IP

# Mac/Windows的Docker Desktop
seata:
  service:
    grouplist:
      default: host.docker.internal:8091  # 使用特殊域名
```

### 问题3：配置文件未生效

**症状**：修改配置后，行为未改变

**解决方案**：

```bash
# 1. 检查配置文件挂载
docker inspect seata-server | grep -A 5 "Mounts"

# 2. 重新启动容器
docker restart seata-server

# 3. 进入容器验证配置
docker exec -it seata-server cat /seata-server/resources/registry.conf
```

## 性能优化

### 1. 调整JVM参数

```bash
docker run -d \
  --name seata-server \
  -p 8091:8091 \
  -e JAVA_OPTS="-Xms512m -Xmx512m -XX:MetaspaceSize=128m" \
  seataio/seata-server:1.4.2
```

### 2. 限制容器资源

```bash
docker run -d \
  --name seata-server \
  -p 8091:8091 \
  --memory="1g" \
  --cpus="2" \
  seataio/seata-server:1.4.2
```

## 生产环境建议

### 1. 使用数据库存储模式

File模式仅适用于测试，生产环境建议使用DB模式或Redis模式。

### 2. 启用健康检查

```yaml
healthcheck:
  test: ["CMD", "nc", "-z", "localhost", "8091"]
  interval: 30s
  timeout: 10s
  retries: 3
  start_period: 60s
```

### 3. 配置日志卷

```bash
docker run -d \
  --name seata-server \
  -p 8091:8091 \
  -v ~/seata-logs:/seata-server/logs \
  seataio/seata-server:1.4.2
```

### 4. 使用环境变量配置

```bash
docker run -d \
  --name seata-server \
  -p 8091:8091 \
  -e SEATA_IP=192.168.1.100 \
  -e SEATA_PORT=8091 \
  -e STORE_MODE=db \
  seataio/seata-server:1.4.2
```

## 参考资料

- [Seata官方文档](https://seata.io/zh-cn/docs/overview/what-is-seata.html)
- [Seata Docker Hub](https://hub.docker.com/r/seataio/seata-server)
- [项目部署文档](./DEPLOYMENT.md)
- [项目README](./README.md)

## 附录

### 常用镜像版本

| 版本 | 适配Spring Cloud Alibaba | 发布日期 |
|------|-------------------------|----------|
| 1.4.2 | 2.2.x | 2021-04 |
| 1.5.2 | 2021.x | 2022-07 |
| 1.6.1 | 2021.x | 2023-02 |

### 环境变量说明

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| SEATA_IP | Seata Server IP | 127.0.0.1 |
| SEATA_PORT | Seata Server端口 | 8091 |
| STORE_MODE | 存储模式 | file |
| SERVER_NODE | 服务器节点ID | 1 |

---

**更新日期**：2025-10-24  
**文档版本**：v1.0
