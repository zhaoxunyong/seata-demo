package com.example.seata.order.service;

/**
 * Saga模式订单服务接口
 */
public interface OrderSagaService {

    /**
     * 创建订单（Saga正向操作）
     *
     * @param userId    用户ID
     * @param productId 商品ID
     * @param count     购买数量
     * @param amount    订单金额
     * @return 是否成功
     */
    boolean createOrder(String userId, String productId, Integer count, String amount);

    /**
     * 补偿订单（Saga补偿操作）
     *
     * @param userId    用户ID
     * @param productId 商品ID
     * @return 是否成功
     */
    boolean compensateOrder(String userId, String productId);

    /**
     * 完成订单（Saga完成操作）
     *
     * @param userId    用户ID
     * @param productId 商品ID
     * @return 是否成功
     */
    boolean completeOrder(String userId, String productId);
}