package com.example.seata.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.seata.order.entity.Order;
import org.apache.ibatis.annotations.Mapper;

/**
 * AT模式订单Mapper
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {
}
