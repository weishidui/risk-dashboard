package com.finance.risk.dashboard.config;

import com.finance.risk.dashboard.dao.SysUserDao;
import com.finance.risk.dashboard.entity.SysUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * Initializes only the default administrator account for a new deployment.
 * Business transactions, alerts, and metric snapshots are produced by the
 * offline and real-time processing chains, never by the dashboard service.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Resource
    private SysUserDao sysUserDao;

    @Override
    public void run(String... args) {
        try {
            if (sysUserDao.findByUsername("admin") == null) {
                sysUserDao.insert(SysUser.builder()
                        .username("admin").password("admin123").role("admin").build());
                log.info("Default administrator account created: admin");
            }
        } catch (Exception e) {
            log.warn("Skipped default administrator initialization: {}", e.getMessage());
        }
    }
}
