package com.example.seata.storage.service;

import io.seata.rm.tcc.api.BusinessActionContext;
import io.seata.rm.tcc.api.BusinessActionContextParameter;
import io.seata.rm.tcc.api.LocalTCC;
import io.seata.rm.tcc.api.TwoPhaseBusinessAction;

/**
 * TCC模式库存服务接口
 */
@LocalTCC
public interface StorageTCCService {

    /**
     * Try阶段：尝试冻结库存
     *
     * @param productId 商品ID
     * @param count     扣减数量
     * @return 是否成功
     */
    @TwoPhaseBusinessAction(name = "StorageTCCService", commitMethod = "confirmReduce", rollbackMethod = "cancelReduce")
    boolean tryReduce(@BusinessActionContextParameter(paramName = "productId") String productId,
                      @BusinessActionContextParameter(paramName = "count") Integer count);

    /**
     * Confirm阶段：确认库存扣减
     *
     * @param context 事务上下文
     * @return 是否成功
     */
    boolean confirmReduce(BusinessActionContext context);

    /**
     * Cancel阶段：释放冻结库存
     *
     * @param context 事务上下文
     * @return 是否成功
     */
    boolean cancelReduce(BusinessActionContext context);
}
