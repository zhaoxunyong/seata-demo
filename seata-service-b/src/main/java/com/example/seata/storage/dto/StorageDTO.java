package com.example.seata.storage.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 库存扣减请求DTO
 */
@Data
@ApiModel("库存扣减请求DTO")
public class StorageDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "商品ID", required = true, example = "P001")
    private String productId;

    @ApiModelProperty(value = "扣减数量", required = true, example = "10")
    private Integer count;
}
