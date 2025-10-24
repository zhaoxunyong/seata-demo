package com.example.seata.storage.controller;

import com.example.seata.storage.dto.Result;
import com.example.seata.storage.dto.StorageDTO;
import com.example.seata.storage.service.StorageTCCService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * TCC模式库存控制器
 */
@Slf4j
@Api(tags = "TCC模式库存服务")
@RestController
@RequestMapping("/storage/tcc")
public class StorageTCCController {

    @Resource
    private StorageTCCService storageTCCService;

    /**
     * 扣减库存（TCC模式 - Try阶段）
     */
    @ApiOperation("扣减库存（TCC模式 - Try阶段）")
    @PostMapping("/reduce")
    public Result<Void> reduce(@RequestBody StorageDTO dto) {
        try {
            log.info("接收到扣减库存请求（TCC模式）：{}", dto);
            boolean success = storageTCCService.tryReduce(dto.getProductId(), dto.getCount());
            if (success) {
                return Result.success("TCC-Try：冻结库存成功", null);
            } else {
                return Result.fail("TCC-Try：冻结库存失败");
            }
        } catch (Exception e) {
            log.error("TCC-Try：冻结库存失败", e);
            return Result.fail(e.getMessage());
        }
    }
}
