package com.finance.risk.dashboard.controller;

import com.finance.risk.dashboard.common.Constants;
import com.finance.risk.dashboard.dto.LoginDTO;
import com.finance.risk.dashboard.dto.RegisterDTO;
import com.finance.risk.dashboard.service.SysUserService;
import com.finance.risk.dashboard.vo.LoginVO;
import com.finance.risk.dashboard.vo.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

@Api(tags = "0. 认证接口")
@RestController
@RequestMapping(Constants.API_AUTH_PREFIX)
public class AuthController {

    @Resource
    private SysUserService sysUserService;

    @ApiOperation("用户登录")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        try {
            return Result.ok(sysUserService.login(dto));
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        }
    }

    @ApiOperation("用户注册")
    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterDTO dto) {
        try {
            return Result.ok(sysUserService.register(dto));
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        }
    }
}
