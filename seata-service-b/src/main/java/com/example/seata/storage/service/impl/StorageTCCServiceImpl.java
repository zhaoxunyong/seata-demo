package com.example.seata.storage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.seata.storage.entity.StorageTCC;
import com.example.seata.storage.exception.BusinessException;
import com.example.seata.storage.mapper.StorageTCCMapper;
import com.example.seata.storage.service.StorageTCCService;
import io.seata.rm.tcc.api.BusinessActionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

/**
 * TCC模式库存服务实现
 */
@Slf4j
@Service
public class StorageTCCServiceImpl implements StorageTCCService {

    @Resource
    private StorageTCCMapper storageTCCMapper;

    /**
     * Try阶段：尝试冻结库存
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean tryReduce(String productId, Integer count) {
        log.info("TCC库存服务 - Try阶段：开始冻结库存，商品ID={}，冻结数量={}", productId, count);

        // 查询库存信息
        StorageTCC storage = storageTCCMapper.selectOne(
                new LambdaQueryWrapper<StorageTCC>().eq(StorageTCC::getProductId, productId)
        );

        if (storage == null) {
            log.error("TCC库存服务 - Try阶段：商品不存在，商品ID={}", productId);
            throw new BusinessException("商品不存在");
        }

        if (storage.getResidue() < count) {
            log.error("TCC库存服务 - Try阶段：库存不足，商品ID={}，剩余库存={}，需要冻结={}", 
                    productId, storage.getResidue(), count);
            throw new BusinessException("库存不足");
        }

        // 冻结库存
        int result = storageTCCMapper.tryFreeze(productId, count);
        if (result <= 0) {
            log.error("TCC库存服务 - Try阶段：冻结库存失败，商品ID={}", productId);
            return false;
        }

        log.info("TCC库存服务 - Try阶段：冻结库存成功，商品ID={}，冻结数量={}", productId, count);
        return true;
    }

    /**
     * Confirm阶段：确认库存扣减（frozen转为used）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean confirmReduce(BusinessActionContext context) {
        String productId = (String) context.getActionContext("productId");
        Integer count = (Integer) context.getActionContext("count");
        
        log.info("TCC库存服务 - Confirm阶段：开始确认库存扣减，商品ID={}，确认数量={}", productId, count);

        // 确认库存扣减
        int result = storageTCCMapper.confirmReduce(productId, count);
        if (result <= 0) {
            log.error("TCC库存服务 - Confirm阶段：确认库存扣减失败，商品ID={}", productId);
            return false;
        }

        log.info("TCC库存服务 - Confirm阶段：确认库存扣减成功，商品ID={}，frozen转为used数量={}", productId, count);
        return true;
    }

    /**
     * Cancel阶段：释放冻结库存
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelReduce(BusinessActionContext context) {
        String productId = (String) context.getActionContext("productId");
        Integer count = (Integer) context.getActionContext("count");
        
        log.info("TCC库存服务 - Cancel阶段：开始释放冻结库存，商品ID={}，释放数量={}", productId, count);

        // 释放冻结库存
        int result = storageTCCMapper.cancelReduce(productId, count);
        if (result <= 0) {
            log.warn("TCC库存服务 - Cancel阶段：释放冻结库存失败（可能是空回滚），商品ID={}", productId);
            // 空回滚场景：Try未执行，直接Cancel
            // 这种情况下应返回true，避免TC重试
            return true;
        }

        log.info("TCC库存服务 - Cancel阶段：释放冻结库存成功，商品ID={}，释放数量={}", productId, count);
        return true;
    }
}
