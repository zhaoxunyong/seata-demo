package com.example.seata.storage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.seata.storage.entity.StorageTCC;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * TCC模式库存Mapper
 */
@Mapper
public interface StorageTCCMapper extends BaseMapper<StorageTCC> {

    /**
     * Try阶段：冻结库存
     *
     * @param productId 商品ID
     * @param count     冻结数量
     * @return 影响行数
     */
    @Update("UPDATE t_storage_tcc SET frozen = frozen + #{count}, residue = residue - #{count} " +
            "WHERE product_id = #{productId} AND residue >= #{count}")
    int tryFreeze(@Param("productId") String productId, @Param("count") Integer count);

    /**
     * Confirm阶段：确认库存扣减（frozen转为used）
     *
     * @param productId 商品ID
     * @param count     确认数量
     * @return 影响行数
     */
    @Update("UPDATE t_storage_tcc SET frozen = frozen - #{count}, used = used + #{count} " +
            "WHERE product_id = #{productId} AND frozen >= #{count}")
    int confirmReduce(@Param("productId") String productId, @Param("count") Integer count);

    /**
     * Cancel阶段：释放冻结库存
     *
     * @param productId 商品ID
     * @param count     释放数量
     * @return 影响行数
     */
    @Update("UPDATE t_storage_tcc SET frozen = frozen - #{count}, residue = residue + #{count} " +
            "WHERE product_id = #{productId} AND frozen >= #{count}")
    int cancelReduce(@Param("productId") String productId, @Param("count") Integer count);
}
