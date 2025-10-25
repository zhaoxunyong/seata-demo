package com.example.seata.order.feign;

import com.example.seata.order.dto.Result;
import com.example.seata.order.dto.StorageDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 库存服务Feign客户端
 */
@FeignClient(name = "seata-service-b", url = "http://localhost:8082")
public interface StorageFeignClient {

    /**
     * 扣减库存（AT模式）
     *
     * @param dto 库存扣减DTO
     * @return 操作结果
     */
    @PostMapping("/storage/reduce")
    Result<Void> reduce(@RequestBody StorageDTO dto);

    /**
     * 扣减库存（TCC模式）
     *
     * @param dto 库存扣减DTO
     * @return 操作结果
     */
    @PostMapping("/storage/tcc/reduce")
    Result<Void> reduceTcc(@RequestBody StorageDTO dto);

    /**
     * 扣减库存（Saga模式）
     *
     * @param dto 库存扣减DTO
     * @return 操作结果
     */
    @PostMapping("/storage-saga/reduce")
    Result<Void> reduceSaga(@RequestBody StorageDTO dto);

    /**
     * 补偿库存（Saga模式）
     *
     * @param dto 库存补偿DTO
     * @return 操作结果
     */
    @PostMapping("/storage-saga/compensate")
    Result<Void> compensateSaga(@RequestBody StorageDTO dto);

    /**
     * 完成库存操作（Saga模式）
     *
     * @param dto 库存完成DTO
     * @return 操作结果
     */
    @PostMapping("/storage-saga/complete")
    Result<Void> completeSaga(@RequestBody StorageDTO dto);
}