package cn.dmego.seata.saga.order.controller;

import cn.dmego.seata.saga.order.service.OrderService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * OrderController
 *
 * @author dmego
 * @date 2021/3/31 10:51
 */
@Api(tags = "订单控制器", description = "处理订单相关操作")
@RestController
@RequestMapping("order")
public class OrderController {

    @Resource
    private OrderService orderService;

    @ApiOperation(value = "创建订单", notes = "根据用户ID、商品ID等信息创建订单")
    @RequestMapping("/createOrder")
    public Boolean createOrder(@ApiParam(name = "orderId", value = "订单ID", required = true) @RequestParam("orderId") Long orderId,
                               @ApiParam(name = "userId", value = "用户ID", required = true) @RequestParam("userId") Long userId,
                               @ApiParam(name = "productId", value = "商品ID", required = true) @RequestParam("productId") Long productId,
                               @ApiParam(name = "amount", value = "支付金额", required = true) @RequestParam("amount") Integer amount,
                               @ApiParam(name = "count", value = "购买数量", required = true) @RequestParam("count") Integer count) throws Exception {

        return orderService.createOrder(orderId, userId, productId, amount, count);
    }

    @ApiOperation(value = "撤销订单", notes = "事务回滚时撤销订单")
    @RequestMapping("/revokeOrder")
    public Boolean revokeOrder(@ApiParam(name = "orderId", value = "订单ID", required = true) @RequestParam("orderId") Long orderId) throws Exception {
        return orderService.revokeOrder(orderId);
    }

}
