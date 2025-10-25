package com.example.seata.storage.service;

/**
 * Saga模式库存服务接口
 */
public interface StorageSagaService {

    /**
     * 扣减库存（Saga正向操作）
     *
     * @param productId 商品ID
     * @param count     扣减数量
     * @return 是否成功
     */
    boolean reduceStorage(String productId, Integer count);

    /**
     * 补偿库存（Saga补偿操作）
     *
     * @param productId 商品ID
     * @param count     补偿数量
     * @return 是否成功
     */
    boolean compensateStorage(String productId, Integer count);

    /**
     * 完成库存操作（Saga完成操作）
     *
     * @param productId 商品ID
     * @return 是否成功
     */
    boolean completeStorage(String productId);
}