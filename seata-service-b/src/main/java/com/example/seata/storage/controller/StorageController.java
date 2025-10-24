package com.example.seata.storage.controller;

import com.example.seata.storage.dto.Result;
import com.example.seata.storage.dto.StorageDTO;
import com.example.seata.storage.service.StorageService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * AT模式库存控制器
 */
@Slf4j
@Api(tags = "AT模式库存服务")
@RestController
@RequestMapping("/storage")
public class StorageController {

    @Resource
    private StorageService storageService;

    /**
     * 扣减库存（AT模式）
     */
    @ApiOperation("扣减库存（AT模式）")
    @PostMapping("/reduce")
    public Result<Void> reduce(@RequestBody StorageDTO dto) {
        try {
            log.info("接收到扣减库存请求：{}", dto);
            storageService.reduce(dto.getProductId(), dto.getCount());
            return Result.success("扣减库存成功", null);
        } catch (Exception e) {
            log.error("扣减库存失败", e);
            return Result.fail(e.getMessage());
        }
    }
}
