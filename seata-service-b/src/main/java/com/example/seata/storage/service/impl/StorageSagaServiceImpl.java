package com.example.seata.storage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.seata.storage.entity.StorageSaga;
import com.example.seata.storage.exception.BusinessException;
import com.example.seata.storage.mapper.StorageSagaMapper;
import com.example.seata.storage.service.StorageSagaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

/**
 * Saga模式库存服务实现
 */
@Slf4j
@Service
public class StorageSagaServiceImpl implements StorageSagaService {

    @Resource
    private StorageSagaMapper storageSagaMapper;

    /**
     * 扣减库存（Saga正向操作）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean reduceStorage(String productId, Integer count) {
        log.info("Saga库存服务 - 扣减库存，商品ID={}，扣减数量={}", productId, count);

        // 查询库存信息
        StorageSaga storage = storageSagaMapper.selectOne(
                new LambdaQueryWrapper<StorageSaga>().eq(StorageSaga::getProductId, productId)
        );

        if (storage == null) {
            log.error("Saga库存服务 - 扣减库存：商品不存在，商品ID={}", productId);
            throw new BusinessException("商品不存在");
        }

        if (storage.getResidue() < count) {
            log.error("Saga库存服务 - 扣减库存：库存不足，商品ID={}，剩余库存={}，需要扣减={}", 
                    productId, storage.getResidue(), count);
            throw new BusinessException("库存不足");
        }

        // 扣减库存并更新状态为PROCESSING
        int result = storageSagaMapper.reduceStorage(productId, count);
        if (result <= 0) {
            log.error("Saga库存服务 - 扣减库存失败，商品ID={}", productId);
            return false;
        }

        log.info("Saga库存服务 - 扣减库存成功，商品ID={}，扣减数量={}，状态更新为PROCESSING", productId, count);
        return true;
    }

    /**
     * 补偿库存（Saga补偿操作）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean compensateStorage(String productId, Integer count) {
        log.info("Saga库存服务 - 补偿库存，商品ID={}，补偿数量={}", productId, count);

        // 补偿库存（增加库存）
        int result = storageSagaMapper.compensateStorage(productId, count);
        if (result <= 0) {
            log.error("Saga库存服务 - 补偿库存失败，商品ID={}", productId);
            return false;
        }

        // 更新状态为FAIL
        storageSagaMapper.update(null,
                new LambdaUpdateWrapper<StorageSaga>()
                        .set(StorageSaga::getStatus, "FAIL")
                        .set(StorageSaga::getUpdateTime, java.time.LocalDateTime.now())
                        .eq(StorageSaga::getProductId, productId)
                        .in(StorageSaga::getStatus, "INIT", "PROCESSING")
        );

        log.info("Saga库存服务 - 补偿库存成功，商品ID={}，补偿数量={}", productId, count);
        return true;
    }

    /**
     * 完成库存操作（Saga完成操作）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean completeStorage(String productId) {
        log.info("Saga库存服务 - 完成库存操作，商品ID={}", productId);

        // 更新状态为SUCCESS
        int result = storageSagaMapper.update(null,
                new LambdaUpdateWrapper<StorageSaga>()
                        .set(StorageSaga::getStatus, "SUCCESS")
                        .set(StorageSaga::getUpdateTime, java.time.LocalDateTime.now())
                        .eq(StorageSaga::getProductId, productId)
                        .eq(StorageSaga::getStatus, "PROCESSING")
        );

        if (result <= 0) {
            log.warn("Saga库存服务 - 完成库存操作：未找到PROCESSING状态的库存记录，商品ID={}", productId);
            return false;
        }

        log.info("Saga库存服务 - 完成库存操作成功，商品ID={}，状态更新为SUCCESS", productId);
        return true;
    }
}