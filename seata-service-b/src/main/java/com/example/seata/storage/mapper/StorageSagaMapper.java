package com.example.seata.storage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.seata.storage.entity.StorageSaga;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * Saga模式库存Mapper接口
 */
public interface StorageSagaMapper extends BaseMapper<StorageSaga> {

    /**
     * 扣减库存
     *
     * @param productId 商品ID
     * @param count     扣减数量
     * @return 更新记录数
     */
    @Update("UPDATE t_storage_saga SET used = used + #{count}, residue = residue - #{count}, status = 'PROCESSING', update_time = NOW() WHERE product_id = #{productId} AND residue >= #{count} AND status = 'INIT'")
    int reduceStorage(@Param("productId") String productId, @Param("count") Integer count);

    /**
     * 补偿库存（增加库存）
     *
     * @param productId 商品ID
     * @param count     补偿数量
     * @return 更新记录数
     */
    @Update("UPDATE t_storage_saga SET used = used - #{count}, residue = residue + #{count}, update_time = NOW() WHERE product_id = #{productId} AND used >= #{count}")
    int compensateStorage(@Param("productId") String productId, @Param("count") Integer count);
}