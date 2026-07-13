package com.finance.risk.dashboard.controller;

import com.finance.risk.dashboard.entity.UserProfile;
import com.finance.risk.dashboard.service.ProfileService;
import com.finance.risk.dashboard.vo.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;

@Api(tags = "8. 用户画像查询接口")
@RestController
@RequestMapping("/api/profile")
public class UserProfileController {

    @Resource
    private ProfileService profileService;

    @ApiOperation("分页查询用户画像列表")
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @ApiParam("账户状态") @RequestParam(required = false) String accountStatus,
            @ApiParam("风险等级") @RequestParam(required = false) String riskLevel,
            @ApiParam("页码") @RequestParam(defaultValue = "1") int page,
            @ApiParam("每页大小") @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(profileService.queryProfileList(accountStatus, riskLevel, page, pageSize));
    }

    @ApiOperation("按用户ID查询画像详情")
    @GetMapping("/{userId}")
    public Result<UserProfile> getByUserId(@ApiParam("用户ID") @PathVariable String userId) {
        UserProfile profile = profileService.getProfileByUserId(userId);
        return profile != null ? Result.ok(profile) : Result.fail("用户画像不存在");
    }

    @ApiOperation("用户画像统计概览")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.ok(profileService.getProfileStats());
    }
}
