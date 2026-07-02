package com.finance.risk.dashboard.controller;

import com.finance.risk.dashboard.dao.AlertDao;
import com.finance.risk.dashboard.vo.Result;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 管理接口（仅开发/调试用）
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Resource
    private AlertDao alertDao;

    /** 刷新历史告警 create_time 为当前时间 */
    @PostMapping("/refresh-timestamps")
    public Result<String> refreshTimestamps() {
        int count = alertDao.refreshTimestamps();
        return Result.ok("已刷新 " + count + " 条历史告警的时间戳");
    }
}
