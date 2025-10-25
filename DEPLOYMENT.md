# Seata项目部署指南

## 环境要求

- Java 11+
- Maven 3.6+
- MySQL 5.7+
- Docker（可选，用于MySQL）
- Seata Server 1.4.x

## 部署步骤

### 1. MySQL数据库部署

#### 方式一：Docker部署（推荐）

```bash
# 启动MySQL容器
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

# 验证容器运行
docker ps | grep seata-mysql

# 查看日志
docker logs seata-mysql
```

#### 方式二：本地MySQL安装

确保MySQL 5.7+已安装并运行在3306端口。

### 2. 数据库初始化

#### 创建数据库

```sql
-- 创建订单数据库
CREATE DATABASE seata_order DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

-- 创建库存数据库
CREATE DATABASE seata_storage DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

#### 创建表结构

**订单数据库（seata_order）**：

```sql
USE seata_order;

-- AT模式订单表
CREATE TABLE t_order (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  user_id VARCHAR(50) NOT NULL COMMENT '用户ID',
  product_id VARCHAR(50) NOT NULL COMMENT '商品ID',
  count INT NOT NULL COMMENT '购买数量',
  amount DECIMAL(10,2) NOT NULL COMMENT '订单金额',
  status VARCHAR(20) NOT NULL DEFAULT 'INIT' COMMENT '订单状态',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_id (user_id),
  KEY idx_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- TCC模式订单表
CREATE TABLE t_order_tcc (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  user_id VARCHAR(50) NOT NULL COMMENT '用户ID',
  product_id VARCHAR(50) NOT NULL COMMENT '商品ID',
  count INT NOT NULL COMMENT '购买数量',
  amount DECIMAL(10,2) NOT NULL COMMENT '订单金额',
  status VARCHAR(20) NOT NULL DEFAULT 'INIT' COMMENT '订单状态',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_id (user_id),
  KEY idx_product_id (product_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- AT模式回滚日志表
CREATE TABLE undo_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  branch_id BIGINT NOT NULL,
  xid VARCHAR(100) NOT NULL,
  context VARCHAR(128) NOT NULL,
  rollback_info LONGBLOB NOT NULL,
  log_status INT NOT NULL,
  log_created DATETIME NOT NULL,
  log_modified DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY ux_undo_log (xid, branch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Saga模式订单表
CREATE TABLE t_order_saga (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  user_id VARCHAR(50) NOT NULL COMMENT '用户ID',
  product_id VARCHAR(50) NOT NULL COMMENT '商品ID',
  count INT NOT NULL COMMENT '购买数量',
  amount DECIMAL(10,2) NOT NULL COMMENT '订单金额',
  status VARCHAR(20) NOT NULL DEFAULT 'INIT' COMMENT '订单状态：INIT-初始化，PROCESSING-处理中，SUCCESS-成功，FAIL-失败',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_id (user_id),
  KEY idx_product_id (product_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**库存数据库（seata_storage）**：

```sql
USE seata_storage;

-- AT模式库存表
CREATE TABLE t_storage (
  id BIGINT NOT NULL AUTO_INCREMENT,
  product_id VARCHAR(50) NOT NULL,
  total INT NOT NULL,
  used INT NOT NULL DEFAULT 0,
  residue INT NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- TCC模式库存表
CREATE TABLE t_storage_tcc (
  id BIGINT NOT NULL AUTO_INCREMENT,
  product_id VARCHAR(50) NOT NULL,
  total INT NOT NULL,
  used INT NOT NULL DEFAULT 0,
  frozen INT NOT NULL DEFAULT 0,
  residue INT NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- AT模式回滚日志表
CREATE TABLE undo_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  branch_id BIGINT NOT NULL,
  xid VARCHAR(100) NOT NULL,
  context VARCHAR(128) NOT NULL,
  rollback_info LONGBLOB NOT NULL,
  log_status INT NOT NULL,
  log_created DATETIME NOT NULL,
  log_modified DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY ux_undo_log (xid, branch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Saga模式库存表
CREATE TABLE t_storage_saga (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '库存ID',
  product_id VARCHAR(50) NOT NULL COMMENT '商品ID',
  total INT NOT NULL COMMENT '总库存',
  used INT NOT NULL DEFAULT '0' COMMENT '已用库存',
  residue INT NOT NULL COMMENT '剩余可用库存',
  status VARCHAR(20) NOT NULL DEFAULT 'INIT' COMMENT '订单状态：INIT-初始化，PROCESSING-处理中，SUCCESS-成功，FAIL-失败',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_product_id (product_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

#### 初始化测试数据

```sql
-- AT模式库存数据
USE seata_storage;
INSERT INTO t_storage (product_id, total, used, residue) VALUES
('P001', 100, 0, 100),
('P003', 5, 0, 5);

-- TCC模式库存数据
INSERT INTO t_storage_tcc (product_id, total, used, frozen, residue) VALUES
('P002', 100, 0, 0, 100),
('P004', 10, 0, 0, 10);

-- Saga模式订单数据
INSERT INTO t_order_saga (user_id, product_id, count, amount, status) VALUES
('U001', 'P005', 1, 50.00, 'INIT'),
('U002', 'P006', 2, 40.00, 'INIT');

-- Saga模式库存数据
INSERT INTO t_storage_saga (product_id, total, used, residue, status) VALUES
('P005', 100, 0, 100, 'INIT'),
('P006', 20, 0, 20, 'INIT');
```

### 3. Seata Server部署

#### 方式一：Docker部署（推荐）

**优点**：快速启动、环境隔离、配置简单

##### 3.1.1 使用官方镜像（快速验证）

```bash
# 拉取Seata Server镜像（1.4.2版本）
docker pull seataio/seata-server:1.4.2

# 启动Seata Server容器（File模式）
docker run -d \
  --name seata-server \
  -p 8091:8091 \
  -e SEATA_IP=127.0.0.1 \
  -e SEATA_PORT=8091 \
  seataio/seata-server:1.4.2

# 查看容器状态
docker ps | grep seata-server

# 查看日志
docker logs -f seata-server
```

##### 3.1.2 自定义配置启动

**创建配置目录**：

```bash
# 创建本地配置目录
mkdir -p ~/seata-server/resources
cd ~/seata-server/resources
```

**创建 file.conf**：

```bash
cat > file.conf << 'EOF'
store {
  mode = "file"
  file {
    dir = "sessionStore"
    maxBranchSessionSize = 16384
    maxGlobalSessionSize = 512
  }
}
EOF
```

**创建 registry.conf**：

```bash
cat > registry.conf << 'EOF'
registry {
  type = "file"
  file {
    name = "file.conf"
  }
}

config {
  type = "file"
  file {
    name = "file.conf"
  }
}
EOF
```

**启动容器（挂载配置）**：

```bash
docker run -d \
  --name seata-server \
  -p 8091:8091 \
  -e SEATA_IP=127.0.0.1 \
  -e SEATA_PORT=8091 \
  -v ~/seata-server/resources:/seata-server/resources \
  seataio/seata-server:1.4.2
```

##### 3.1.3 验证Seata Server

```bash
# 检查容器运行状态
docker ps -a | grep seata-server

# 查看启动日志
docker logs seata-server

# 验证端口监听
netstat -an | grep 8091
# 或者
lsof -i :8091

# 进入容器查看
docker exec -it seata-server sh
```

**预期日志输出**：
```
Server started, listen port: 8091
```

##### 3.1.4 Docker常用管理命令

```bash
# 停止容器
docker stop seata-server

# 启动容器
docker start seata-server

# 重启容器
docker restart seata-server

# 删除容器
docker rm -f seata-server

# 查看容器详细信息
docker inspect seata-server
```

#### 方式二：本地安装部署

**下载Seata Server**：

访问 https://github.com/seata/seata/releases 下载对应版本（建议1.4.x系列）

```bash
# 解压
tar -zxvf seata-server-1.4.2.tar.gz
cd seata-server-1.4.2
```

**配置Seata Server（File模式）**

编辑 `conf/file.conf`：

```conf
store {
  mode = "file"
  
  file {
    dir = "sessionStore"
    maxBranchSessionSize = 16384
    maxGlobalSessionSize = 512
  }
}
```

编辑 `conf/registry.conf`：

```conf
registry {
  type = "file"
  
  file {
    name = "file.conf"
  }
}

config {
  type = "file"
  
  file {
    name = "file.conf"
  }
}
```

**启动Seata Server**：

```bash
# Linux/Mac
sh bin/seata-server.sh -p 8091 -h 127.0.0.1

# Windows
bin\seata-server.bat -p 8091 -h 127.0.0.1
```

**验证启动成功**：
- 查看日志：`logs/seata-server.log`
- 端口监听：`netstat -an | grep 8091`

#### 方式三：Docker Compose部署（推荐生产环境）

**创建 docker-compose.yml**：

```yaml
version: '3'
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

volumes:
  seata-mysql-data:

networks:
  seata-network:
    driver: bridge
```

**启动所有服务**：

```bash
# 启动
docker-compose up -d

# 查看状态
docker-compose ps

# 查看日志
docker-compose logs -f seata-server

# 停止
docker-compose down
```

### 4. 编译项目

```bash
# 编译订单服务
cd seata-service-a
mvn clean package -DskipTests

# 编译库存服务
cd ../seata-service-b
mvn clean package -DskipTests
```

### 5. 启动服务

#### 方式一：IDEA启动（开发环境）

1. 导入项目到IDEA
2. 启动 `StorageServiceApplication`（库存服务）
3. 启动 `OrderServiceApplication`（订单服务）

#### 方式二：命令行启动

```bash
# 启动库存服务
cd seata-service-b
mvn spring-boot:run

# 启动订单服务（新终端）
cd seata-service-a
mvn spring-boot:run
```

#### 方式三：Jar包启动（生产环境）

```bash
# 启动库存服务
cd seata-service-b/target
java -jar seata-service-b-1.0.0.jar

# 启动订单服务（新终端）
cd seata-service-a/target
java -jar seata-service-a-1.0.0.jar
```

### 6. 验证部署

#### 检查服务状态

- 订单服务Swagger：http://localhost:8081/swagger-ui/index.html
- 库存服务Swagger：http://localhost:8082/swagger-ui/index.html

#### 查看服务日志

```bash
# 订单服务日志
tail -f seata-service-a/logs/application.log

# 库存服务日志
tail -f seata-service-b/logs/application.log

# Seata Server日志
tail -f seata-server/logs/seata-server.log
```

## 配置说明

### 数据库连接配置

在 `application.yml` 中修改数据库连接信息：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/seata_order?...
    username: root
    password: root123  # 修改为实际密码
```

### Seata Server地址配置

```yaml
seata:
  service:
    grouplist:
      default: 127.0.0.1:8091  # 修改为实际Seata Server地址
```

### 端口配置

如需修改服务端口，编辑 `application.yml`：

```yaml
server:
  port: 8081  # 修改为其他端口
```

## 故障排查

### 1. 数据库连接失败

**症状**：服务启动报错 "Communications link failure"

**排查**：
1. 检查MySQL是否启动：`docker ps | grep seata-mysql`
2. 检查端口是否开放：`telnet localhost 3306`
3. 检查用户名密码是否正确
4. 检查数据库是否存在：`SHOW DATABASES;`

### 2. Seata Server连接失败

**症状**：日志显示 "can not connect to seata server"

**排查**：

**Docker部署方式**：
```bash
# 1. 检查Seata Server容器是否运行
docker ps -a | grep seata-server

# 2. 查看容器日志
docker logs seata-server

# 3. 检查端口映射
docker port seata-server

# 4. 重启容器
docker restart seata-server

# 5. 如果容器未运行，查看退出原因
docker logs seata-server --tail 100
```

**本地部署方式**：
```bash
# 1. 检查Seata Server进程
ps -ef | grep seata

# 2. 检查端口8091是否监听
netstat -an | grep 8091
# 或者
lsof -i :8091

# 3. 查看Seata Server日志
tail -f seata-server/logs/seata-server.log
```

**配置检查**：
- 检查 `application.yml` 中Seata Server地址
- Docker容器方式，客户端配置应为：`127.0.0.1:8091` 或 `host.docker.internal:8091`（Mac/Windows）
- 如使用Docker Compose网络，配置应为：`seata-server:8091`

### 3. 服务启动端口冲突

**症状**：启动时报错 "Port 8081 was already in use"

**解决**：
1. 修改 `application.yml` 中的端口号
2. 或停止占用端口的进程：`lsof -ti:8081 | xargs kill -9`

### 4. Maven依赖下载失败

**解决**：
1. 配置国内镜像源（阿里云Maven镜像）
2. 删除 `.m2/repository` 中损坏的依赖
3. 重新执行 `mvn clean install`

## 生产环境建议

### 1. Seata Server高可用

- 使用DB存储模式替代File模式
- 配置Nacos注册中心实现集群部署
- 配置多个Seata Server实例

### 2. 数据库优化

- 增加数据库连接池大小
- 配置主从复制
- 定期清理undo_log表

### 3. 监控告警

- 接入Prometheus监控
- 配置Seata指标采集
- 设置异常告警规则

### 4. 安全加固

- 修改数据库默认密码
- 配置防火墙规则
- 启用SSL/TLS加密

## 附录

### 快速清理数据

```sql
-- 清空订单表
TRUNCATE TABLE seata_order.t_order;
TRUNCATE TABLE seata_order.t_order_tcc;

-- 重置库存数据
UPDATE seata_storage.t_storage SET used = 0, residue = total;
UPDATE seata_storage.t_storage_tcc SET used = 0, frozen = 0, residue = total;

-- 清空undo_log
TRUNCATE TABLE seata_order.undo_log;
TRUNCATE TABLE seata_storage.undo_log;
```

### Docker Compose部署（可选）

创建 `docker-compose.yml`：

```yaml
version: '3'
services:
  mysql:
    image: mysql:5.7
    container_name: seata-mysql
    ports:
      - "3306:3306"
    environment:
      MYSQL_ROOT_PASSWORD: root123
      MYSQL_CHARACTER_SET_SERVER: utf8mb4
    volumes:
      - mysql-data:/var/lib/mysql
      
volumes:
  mysql-data:
```

启动：`docker-compose up -d`
