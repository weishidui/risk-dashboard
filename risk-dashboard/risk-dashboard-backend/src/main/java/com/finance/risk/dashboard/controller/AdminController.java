package com.finance.risk.dashboard.controller;

import com.finance.risk.dashboard.dao.AlertDao;
import com.finance.risk.dashboard.entity.SysUser;
import com.finance.risk.dashboard.service.SysUserService;
import com.finance.risk.dashboard.vo.Result;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

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

    @GetMapping("/users")
    public Result<List<SysUser>> listUsers() {
        return Result.ok(sysUserService.listUsers());
    }

    @DeleteMapping("/users/{username}")
    public Result<String> deleteUser(@PathVariable String username) {
        try {
            sysUserService.deleteUser(username);
            return Result.ok("删除成功");
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        }
    }

    @PutMapping("/users/{username}/reset-password")
    public Result<String> resetPassword(@PathVariable String username, @RequestBody Map<String, String> body) {
        String pwd = body.get("password");
        if (pwd == null || pwd.isEmpty()) return Result.fail("密码不能为空");
        return sysUserService.resetPassword(username, pwd) ? Result.ok("密码重置成功") : Result.fail("用户不存在");
    }
}
