package com.example.seata.storage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.seata.storage.entity.Storage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * AT模式库存Mapper
 */
@Mapper
public interface StorageMapper extends BaseMapper<Storage> {

    /**
     * 扣减库存
     *
     * @param productId 商品ID
     * @param count     扣减数量
     * @return 影响行数
     */
    @Update("UPDATE t_storage SET used = used + #{count}, residue = residue - #{count} " +
            "WHERE product_id = #{productId} AND residue >= #{count}")
    int reduce(@Param("productId") String productId, @Param("count") Integer count);
}
