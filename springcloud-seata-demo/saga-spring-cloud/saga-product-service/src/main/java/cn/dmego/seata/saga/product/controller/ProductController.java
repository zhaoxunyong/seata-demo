package cn.dmego.seata.saga.product.controller;

import cn.dmego.seata.saga.product.service.ProductService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ProductController
 *
 * @author dmego
 * @date 2021/3/31 10:52
 */
@Api(tags = "产品控制器", description = "处理产品相关操作")
@RestController
@RequestMapping("/product")
public class ProductController {

    @Autowired
    ProductService productService;

    @ApiOperation(value = "扣减库存", notes = "从商品库存中扣减指定数量")
    @RequestMapping("/reduceStock")
    Boolean reduceStock(@ApiParam(name = "productId", value = "产品ID", required = true) @RequestParam("productId") Long productId, 
                       @ApiParam(name = "count", value = "扣减数量", required = true) @RequestParam("count") Integer count) throws Exception {
        return productService.reduceStock(productId, count);
    }

    @ApiOperation(value = "补偿库存", notes = "事务回滚时补偿产品库存")
    @RequestMapping("/compensateStock")
    Boolean compensateStock(@ApiParam(name = "productId", value = "产品ID", required = true) @RequestParam("productId") Long productId, 
                          @ApiParam(name = "count", value = "补偿数量", required = true) @RequestParam("count") Integer count) throws Exception {
        return productService.compensateStock(productId, count);
    }

    @ApiOperation(value = "获取产品价格", notes = "根据产品ID获取产品价格")
    @GetMapping("/getPrice")
    Integer getPrice(@ApiParam(name = "productId", value = "产品ID", required = true) @RequestParam("productId") Long productId) {
        return productService.getPriceById(productId);
    }

}
