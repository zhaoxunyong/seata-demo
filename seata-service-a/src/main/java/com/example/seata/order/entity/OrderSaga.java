package com.example.seata.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Saga模式订单实体
 */
@Data
@TableName("t_order_saga")
public class OrderSaga implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 订单ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 商品ID
     */
    private String productId;

    /**
     * 购买数量
     */
    private Integer count;

    /**
     * 订单金额
     */
    private BigDecimal amount;

    /**
     * 订单状态：INIT-初始化，PROCESSING-处理中，SUCCESS-成功，FAIL-失败
     */
    private String status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}