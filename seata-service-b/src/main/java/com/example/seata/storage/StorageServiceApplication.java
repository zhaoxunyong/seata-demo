package com.example.seata.storage;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Seata库存服务启动类
 * 
 * @author Seata Demo
 */
@SpringBootApplication
@MapperScan("com.example.seata.storage.mapper")
public class StorageServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(StorageServiceApplication.class, args);
    }
}
