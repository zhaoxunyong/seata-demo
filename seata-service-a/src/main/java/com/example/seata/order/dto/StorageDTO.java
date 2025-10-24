package com.example.seata.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 库存扣减请求DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StorageDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 商品ID
     */
    private String productId;

    /**
     * 扣减数量
     */
    private Integer count;
}
