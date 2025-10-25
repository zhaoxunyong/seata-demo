package com.example.seata.storage.controller;

import com.example.seata.storage.dto.Result;
import com.example.seata.storage.dto.StorageDTO;
import com.example.seata.storage.service.StorageSagaService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * Saga模式库存控制器
 */
@Slf4j
@Api(tags = "Saga模式库存服务")
@RestController
@RequestMapping("/storage-saga")
public class StorageSagaController {

    @Resource
    private StorageSagaService storageSagaService;

    /**
     * 扣减库存（Saga正向操作）
     */
    @ApiOperation(value = "扣减库存（Saga正向操作）", notes = "Saga模式下扣减库存")
    @PostMapping("/reduce")
    public Result<String> reduceStorage(@RequestBody StorageDTO storageDTO) {
        try {
            log.info("接收到扣减库存请求（Saga模式）：{}", storageDTO);
            boolean result = storageSagaService.reduceStorage(storageDTO.getProductId(), storageDTO.getCount());
            if (result) {
                return Result.success("库存扣减成功");
            } else {
                return Result.fail("库存扣减失败");
            }
        } catch (Exception e) {
            log.error("库存扣减失败（Saga模式）", e);
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 补偿库存（Saga补偿操作）
     */
    @ApiOperation(value = "补偿库存（Saga补偿操作）", notes = "Saga模式下补偿库存")
    @PostMapping("/compensate")
    public Result<String> compensateStorage(@RequestBody StorageDTO storageDTO) {
        try {
            log.info("接收到补偿库存请求（Saga模式）：{}", storageDTO);
            boolean result = storageSagaService.compensateStorage(storageDTO.getProductId(), storageDTO.getCount());
            if (result) {
                return Result.success("库存补偿成功");
            } else {
                return Result.fail("库存补偿失败");
            }
        } catch (Exception e) {
            log.error("库存补偿失败（Saga模式）", e);
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 完成库存操作（Saga完成操作）
     */
    @ApiOperation(value = "完成库存操作（Saga完成操作）", notes = "Saga模式下完成库存操作")
    @PostMapping("/complete")
    public Result<String> completeStorage(@RequestBody StorageDTO storageDTO) {
        try {
            log.info("接收到完成库存操作请求（Saga模式）：{}", storageDTO);
            boolean result = storageSagaService.completeStorage(storageDTO.getProductId());
            if (result) {
                return Result.success("库存操作完成");
            } else {
                return Result.fail("库存操作完成失败");
            }
        } catch (Exception e) {
            log.error("库存操作完成失败（Saga模式）", e);
            return Result.fail(e.getMessage());
        }
    }
}