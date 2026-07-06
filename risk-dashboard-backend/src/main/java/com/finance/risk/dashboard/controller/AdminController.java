package com.finance.risk.dashboard.controller;

import com.finance.risk.dashboard.dao.AlertDao;
import com.finance.risk.dashboard.entity.SysUser;
import com.finance.risk.dashboard.service.SysUserService;
import com.finance.risk.dashboard.vo.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@Api(tags = "8. 管理接口 (仅管理员)")
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Resource
    private AlertDao alertDao;

    @Resource
    private SysUserService sysUserService;

    @PostMapping("/refresh-timestamps")
    public Result<String> refreshTimestamps() {
        int count = alertDao.refreshTimestamps();
        return Result.ok("已刷新 " + count + " 条历史告警的时间戳");
    }

    @ApiOperation("获取用户列表")
    @GetMapping("/users")
    public Result<List<SysUser>> listUsers() {
        return Result.ok(sysUserService.listUsers());
    }

    @ApiOperation("删除用户")
    @DeleteMapping("/users/{username}")
    public Result<String> deleteUser(@PathVariable String username) {
        try {
            sysUserService.deleteUser(username);
            return Result.ok("删除成功");
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        }
    }

    @ApiOperation("重置密码")
    @PutMapping("/users/{username}/reset-password")
    public Result<String> resetPassword(@PathVariable String username,
                                         @RequestBody Map<String, String> body) {
        String newPassword = body.get("password");
        if (newPassword == null || newPassword.isEmpty()) {
            return Result.fail("密码不能为空");
        }
        boolean ok = sysUserService.resetPassword(username, newPassword);
        return ok ? Result.ok("密码重置成功") : Result.fail("用户不存在");
    }
}
