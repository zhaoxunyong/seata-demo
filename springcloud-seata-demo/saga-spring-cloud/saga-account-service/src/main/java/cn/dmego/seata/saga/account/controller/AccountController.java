package cn.dmego.seata.saga.account.controller;

import cn.dmego.seata.saga.account.service.AccountService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AccountController
 * 
 * @author dmego
 * @date 2021/3/31 10:51
 */
@Api(tags = "账户控制器", description = "处理账户相关操作")
@RestController
@RequestMapping("/account")
public class AccountController {

    @Autowired
    AccountService accountService;

    @ApiOperation(value = "扣减余额", notes = "从用户账户中扣减指定金额")
    @RequestMapping("/reduceBalance")
    Boolean reduceBalance(@ApiParam(name = "userId", value = "用户ID", required = true) @RequestParam("userId") Long userId, 
                         @ApiParam(name = "amount", value = "扣减金额", required = true) @RequestParam("amount") Integer amount) throws Exception {
        return accountService.reduceBalance(userId, amount);
    }

    @ApiOperation(value = "补偿余额", notes = "事务回滚时补偿用户余额")
    @RequestMapping("/compensateBalance")
    Boolean compensateBalance(@ApiParam(name = "userId", value = "用户ID", required = true) @RequestParam("userId") Long userId, 
                            @ApiParam(name = "amount", value = "补偿金额", required = true) @RequestParam("amount") Integer amount) throws Exception {
        return accountService.compensateBalance(userId, amount);
    }
}
