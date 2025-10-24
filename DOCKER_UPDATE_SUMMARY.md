# Seata Server Docker安装补充说明

## 更新内容

本次更新为Seata分布式事务验证项目补充了完整的Seata Server Docker部署方案。

## 新增文件

### 1. 部署文档更新
- **文件**: `DEPLOYMENT.md`
- **更新内容**:
  - ✅ 新增Docker快速部署方式（方式一）
  - ✅ 新增自定义配置Docker部署（方式一）
  - ✅ 新增Docker Compose完整部署方案（方式三）
  - ✅ 更新故障排查章节，增加Docker相关问题处理

### 2. 快速启动脚本
- **文件**: `start-all.sh`
- **功能**:
  - 自动检查并启动MySQL容器
  - 自动检查并启动Seata Server容器
  - 智能处理容器已存在的情况
  - 提供清晰的下一步操作指引

### 3. 停止脚本
- **文件**: `stop-all.sh`
- **功能**:
  - 停止Seata Server容器
  - 可选停止MySQL容器
  - 提供容器管理提示

### 4. Docker部署详细指南
- **文件**: `SEATA_DOCKER_GUIDE.md`（488行）
- **内容**:
  - 三种Docker部署方式详解
  - 完整的验证步骤
  - 常用管理命令大全
  - 故障排查指南
  - 性能优化建议
  - 生产环境最佳实践

### 5. README更新
- **文件**: `README.md`
- **更新内容**:
  - ✅ 新增"一键启动"方式
  - ✅ 补充Docker快速启动Seata Server说明
  - ✅ 优化文档结构，增加层级

## Docker部署方式对比

| 方式 | 适用场景 | 优点 | 配置复杂度 |
|------|---------|------|-----------|
| 方式一：快速启动 | 快速验证、学习测试 | 最简单，一条命令启动 | ⭐ |
| 方式二：自定义配置 | 需要调整配置参数 | 灵活可控 | ⭐⭐ |
| 方式三：Docker Compose | 生产环境、完整部署 | 统一管理，易于维护 | ⭐⭐⭐ |

## 快速使用指南

### 最快启动方式（3步）

```bash
# 1. 运行一键启动脚本
./start-all.sh

# 2. 启动库存服务
cd seata-service-b && mvn spring-boot:run

# 3. 启动订单服务（新终端）
cd seata-service-a && mvn spring-boot:run
```

### Docker单独启动

```bash
# 启动Seata Server
docker run -d \
  --name seata-server \
  -p 8091:8091 \
  -e SEATA_IP=127.0.0.1 \
  -e SEATA_PORT=8091 \
  seataio/seata-server:1.4.2

# 查看日志
docker logs -f seata-server
```

### Docker Compose启动

```bash
# 使用项目提供的docker-compose.yml
docker-compose up -d

# 查看状态
docker-compose ps
```

## 验证部署成功

### 1. 检查Seata Server

```bash
# 查看容器
docker ps | grep seata-server

# 查看日志（应显示：Server started, listen port: 8091）
docker logs seata-server --tail 10

# 测试端口
telnet 127.0.0.1 8091
```

### 2. 启动微服务测试

按照README.md中的测试指南，通过Swagger UI进行功能验证。

## 关键配置说明

### 客户端配置（application.yml）

```yaml
seata:
  service:
    grouplist:
      default: 127.0.0.1:8091  # Docker容器映射到宿主机的地址
```

### Docker网络注意事项

- **宿主机访问容器**: 使用 `127.0.0.1:8091`
- **容器间访问**: 使用 `seata-server:8091`（需在同一网络）
- **Mac/Windows Docker Desktop**: 可使用 `host.docker.internal:8091`

## 常见问题

### Q1: 如何查看Seata Server是否启动成功？

```bash
# 方法1：查看容器状态
docker ps | grep seata-server

# 方法2：查看日志
docker logs seata-server | grep "Server started"

# 方法3：测试端口
nc -zv 127.0.0.1 8091
```

### Q2: 客户端无法连接Seata Server怎么办？

检查清单：
1. ✅ Seata Server容器是否运行
2. ✅ 端口8091是否正确映射
3. ✅ 客户端配置地址是否正确
4. ✅ 防火墙是否允许访问

详细排查见 `SEATA_DOCKER_GUIDE.md` 的故障排查章节。

### Q3: 如何停止所有服务？

```bash
# 使用停止脚本
./stop-all.sh

# 或手动停止
docker stop seata-server
docker stop seata-mysql
```

### Q4: 如何完全清理环境？

```bash
# 删除容器
docker rm -f seata-server seata-mysql

# 删除数据卷
docker volume rm seata-mysql-data

# 删除网络（如果使用Docker Compose）
docker network rm seata-network
```

## 文档索引

| 文档 | 用途 | 详细程度 |
|------|------|---------|
| `README.md` | 项目总览、快速开始 | ⭐⭐ |
| `DEPLOYMENT.md` | 完整部署指南 | ⭐⭐⭐⭐ |
| `SEATA_DOCKER_GUIDE.md` | Docker专项指南 | ⭐⭐⭐⭐⭐ |
| `start-all.sh` | 一键启动脚本 | - |
| `stop-all.sh` | 一键停止脚本 | - |

## 优势总结

### 1. 部署更简单
- 从需要手动下载、解压、配置，到一条命令启动
- 无需关心环境依赖和配置细节

### 2. 管理更方便
- 统一的容器管理方式
- 清晰的日志查看
- 简单的启停控制

### 3. 环境更隔离
- 不污染宿主机环境
- 可快速删除和重建
- 易于版本切换

### 4. 文档更完善
- 三层文档体系（快速开始 → 部署指南 → Docker专项）
- 详细的故障排查指南
- 丰富的使用示例

## 下一步建议

### 立即可做
1. ✅ 运行 `./start-all.sh` 启动环境
2. ✅ 启动两个微服务
3. ✅ 通过Swagger UI测试AT和TCC模式

### 后续优化
1. 📌 配置Nacos注册中心（替代file模式）
2. 📌 使用DB存储模式（替代file模式）
3. 📌 配置Seata Server集群
4. 📌 接入监控系统

## 总结

本次更新完成了Seata Server的Docker部署方案补充，包括：

- ✅ 3种Docker部署方式
- ✅ 2个自动化脚本
- ✅ 1个专项Docker指南（488行）
- ✅ 更新了主要文档

现在用户可以通过简单的 `./start-all.sh` 命令，快速启动包括MySQL、Seata Server在内的完整验证环境，大大降低了部署门槛。

---

**更新日期**: 2025-10-24  
**更新版本**: v1.1  
**主要贡献**: Docker部署方案完善
