package com.example.seata.order.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 订单请求DTO
 */
@Data
@ApiModel("订单请求DTO")
public class OrderDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "用户ID", required = true, example = "U001")
    private String userId;

    @ApiModelProperty(value = "商品ID", required = true, example = "P001")
    private String productId;

    @ApiModelProperty(value = "购买数量", required = true, example = "10")
    private Integer count;

    @ApiModelProperty(value = "订单金额", required = true, example = "100.00")
    private BigDecimal amount;
}
