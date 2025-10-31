cURL Request Example
```shell
curl --location --request POST 'http://127.0.0.1:8080/saga/buy' \
--header 'Content-Type: application/json' \
--data-raw '{
    "userId": "1",
    "productId":"1",
    "count":"2"
}'
```

## Swagger API 文档

项目已集成 Swagger，可以通过以下地址访问各服务的 API 文档：

- 业务服务: http://127.0.0.1:8080/swagger-ui/
- 账户服务: http://127.0.0.1:8083/swagger-ui/
- 订单服务: http://127.0.0.1:8081/swagger-ui/
- 产品服务: http://127.0.0.1:8082/swagger-ui/

或者使用以下路径：

- 业务服务: http://127.0.0.1:8080/swagger-ui/index.html
- 账户服务: http://127.0.0.1:8083/swagger-ui/index.html
- 订单服务: http://127.0.0.1:8081/swagger-ui/index.html
- 产品服务: http://127.0.0.1:8082/swagger-ui/index.html

API 文档 JSON 格式：

- 业务服务: http://127.0.0.1:8080/v3/api-docs
- 账户服务: http://127.0.0.1:8083/v3/api-docs
- 订单服务: http://127.0.0.1:8081/v3/api-docs
- 产品服务: http://127.0.0.1:8082/v3/api-docs

### 使用 Swagger 测试 API

1. 启动所有服务
2. 在浏览器中打开上述任一地址
3. 可以查看 API 文档并进行在线测试