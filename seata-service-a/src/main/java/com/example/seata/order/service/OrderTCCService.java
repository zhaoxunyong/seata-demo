package com.example.seata.order.service;

import io.seata.rm.tcc.api.BusinessActionContext;
import io.seata.rm.tcc.api.BusinessActionContextParameter;
import io.seata.rm.tcc.api.LocalTCC;
import io.seata.rm.tcc.api.TwoPhaseBusinessAction;

/**
 * TCC模式订单服务接口
 */
@LocalTCC
public interface OrderTCCService {

    /**
     * Try阶段：尝试创建订单
     *
     * @param userId    用户ID
     * @param productId 商品ID
     * @param count     购买数量
     * @param amount    订单金额
     * @return 是否成功
     */
    @TwoPhaseBusinessAction(name = "OrderTCCService", commitMethod = "confirmCreate", rollbackMethod = "cancelCreate")
    boolean tryCreate(@BusinessActionContextParameter(paramName = "userId") String userId,
                      @BusinessActionContextParameter(paramName = "productId") String productId,
                      @BusinessActionContextParameter(paramName = "count") Integer count,
                      @BusinessActionContextParameter(paramName = "amount") String amount);

    /**
     * Confirm阶段：确认订单创建
     *
     * @param context 事务上下文
     * @return 是否成功
     */
    boolean confirmCreate(BusinessActionContext context);

    /**
     * Cancel阶段：取消订单创建
     *
     * @param context 事务上下文
     * @return 是否成功
     */
    boolean cancelCreate(BusinessActionContext context);
}
