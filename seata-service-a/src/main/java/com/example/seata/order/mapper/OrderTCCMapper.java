package com.example.seata.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.seata.order.entity.OrderTCC;
import org.apache.ibatis.annotations.Mapper;

/**
 * TCC模式订单Mapper
 */
@Mapper
public interface OrderTCCMapper extends BaseMapper<OrderTCC> {
}
