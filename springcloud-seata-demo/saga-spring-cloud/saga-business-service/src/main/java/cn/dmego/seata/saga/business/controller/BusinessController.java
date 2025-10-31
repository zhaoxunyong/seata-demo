package cn.dmego.seata.saga.business.controller;

import cn.dmego.seata.common.dto.BusinessDTO;
import cn.dmego.seata.saga.business.service.BusinessService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * BusinessController
 *
 * @author dmego
 * @date 2021/3/31 10:48
 */
@Api(tags = "业务控制器", description = "处理业务请求")
@RestController
@RequestMapping("/saga")
public class BusinessController {

    @Autowired
    BusinessService businessService;

    @ApiOperation(value = "下单购买", notes = "处理购买业务请求，包含下单、扣减余额、扣减库存等操作")
    @RequestMapping("/buy")
    public String handlerBusiness(@ApiParam(name = "businessDTO", value = "业务请求参数", required = true) @RequestBody BusinessDTO businessDTO) {
        return businessService.handlerBusiness(businessDTO);
    }
}
