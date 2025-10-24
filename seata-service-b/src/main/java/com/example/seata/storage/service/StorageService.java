package com.example.seata.storage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.seata.storage.entity.Storage;
import com.example.seata.storage.exception.BusinessException;
import com.example.seata.storage.mapper.StorageMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

/**
 * AT模式库存服务
 */
@Slf4j
@Service
public class StorageService {

    @Resource
    private StorageMapper storageMapper;

    /**
     * 扣减库存（AT模式）
     *
     * @param productId 商品ID
     * @param count     扣减数量
     */
    @Transactional(rollbackFor = Exception.class)
    public void reduce(String productId, Integer count) {
        log.info("库存服务：开始扣减库存，商品ID={}，扣减数量={}", productId, count);

        // 查询库存信息
        Storage storage = storageMapper.selectOne(
                new LambdaQueryWrapper<Storage>().eq(Storage::getProductId, productId)
        );

        if (storage == null) {
            log.error("库存服务：商品不存在，商品ID={}", productId);
            throw new BusinessException("商品不存在");
        }

        if (storage.getResidue() < count) {
            log.error("库存服务：库存不足，商品ID={}，剩余库存={}，需要扣减={}", 
                    productId, storage.getResidue(), count);
            throw new BusinessException("库存不足");
        }

        // 扣减库存
        int result = storageMapper.reduce(productId, count);
        if (result <= 0) {
            log.error("库存服务：扣减库存失败，商品ID={}", productId);
            throw new BusinessException("扣减库存失败");
        }

        log.info("库存服务：扣减库存成功，商品ID={}，扣减数量={}", productId, count);
    }
}
