package com.finance.risk.dashboard.config;

import com.finance.risk.dashboard.dao.AlertDao;
import com.finance.risk.dashboard.dao.MetricsDao;
import com.finance.risk.dashboard.dao.SysUserDao;
import com.finance.risk.dashboard.dao.TransactionDao;
import com.finance.risk.dashboard.entity.SysUser;
import com.finance.risk.dashboard.entity.AlertResult;
import com.finance.risk.dashboard.entity.MetricsSnapshot;
import com.finance.risk.dashboard.entity.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final Random RANDOM = new Random();

    @Resource
    private AlertDao alertDao;
    @Resource
    private TransactionDao transactionDao;
    @Resource
    private MetricsDao metricsDao;

    @Resource
    private SysUserDao sysUserDao;

    @Override
    public void run(String... args) {
        try {
            if (sysUserDao.findByUsername("admin") == null) {
                sysUserDao.insert(SysUser.builder()
                        .username("admin").password("admin123").role("admin").build());
                log.info("默认管理员账号已创建: admin / admin123");
            }
        } catch (Exception e) {
            log.warn("sys_user 表初始化跳过: {}", e.getMessage());
        }

        int refreshed = alertDao.refreshTimestamps();
        log.info("时间戳刷新: {} 条历史告警", refreshed);

        long alertCount = alertDao.count(null, null, null, null);
        if (alertCount > 20) {
            log.info("告警数据已充足 ({} 条)，跳过初始化", alertCount);
            initMetrics(System.currentTimeMillis());
            return;
        }

        long now = System.currentTimeMillis();
        log.info("开始初始化演示数据...");
        insertAlerts(now);
        insertTransactions(now);
        initMetrics(now);
        log.info("演示数据初始化完成");
    }

    private void insertAlerts(long now) {
        List<AlertResult> alerts = new ArrayList<>();

        // 极度危险 (score > 120)
        String[][] criticalCities = {
            {"深圳", "A1账户盗用;C1金额突变;D2异地瞬移;I1_VPN代理", "145"},
            {"哈尔滨", "A3新设备登录转账;B1设备Root越狱;E3登录后极速转账;H1资金回流", "135"},
        };
        for (String[] c : criticalCities) {
            alerts.add(buildAlert("CR_" + c[0], "TH_CR_" + c[0], "U_CRIT", c[1], c[0], c[2], "极度危险", "pending"));
        }

        // 高危 (71-120)
        String[][] highCities = {
            {"武汉", "A1账户盗用;D1地理偏离;E1深夜大额交易", "95"},
            {"福州", "C1金额突变;D2异地瞬移;I5低安全分设备", "88"},
            {"南宁", "B1设备Root越狱;F2收款方中风险;G1密码粘贴输入", "82"},
            {"昆明", "A5新注册大额转账;D3_IP与GPS不一致;F4新注册收款方", "78"},
        };
        for (String[] c : highCities) {
            alerts.add(buildAlert("HI_" + c[0], "TH_HI_" + c[0], "U_HIGH", c[1], c[0], c[2], "高危", "processing"));
        }

        // 中危 (41-70)
        String[][] midCities = {
            {"南京", "C1金额突变;D1地理偏离", "55"},
            {"西安", "A3新设备登录转账;G5切换支付渠道", "60"},
            {"长沙", "D1地理偏离;I1_VPN代理", "48"},
            {"郑州", "C1金额突变;E5页面停留过短", "65"},
            {"沈阳", "B5无SIM卡;I4_DNS异常", "42"},
            {"济南", "G6备注含诈骗敏感词", "45"},
            {"合肥", "F3首次转账给该收款方", "38"},
        };
        for (String[] c : midCities) {
            alerts.add(buildAlert("MI_" + c[0], "TM_MI_" + c[0], "U_MID", c[1], c[0], c[2], "中危", "pending"));
        }

        // 低危 (0-40)
        String[][] lowCities = {
            {"北京", "无", "10"}, {"上海", "无", "8"}, {"杭州", "C5小额测试", "12"},
            {"广州", "无", "15"}, {"成都", "无", "5"}, {"重庆", "无", "18"},
        };
        for (String[] c : lowCities) {
            alerts.add(buildAlert("LO_" + c[0], "TL_LO_" + c[0], "U_LOW", c[1], c[0], c[2], "低危", "closed"));
        }

        for (AlertResult a : alerts) {
            alertDao.insert(a);
        }
        log.info("已插入 {} 条告警 (含4个风险等级+工作流状态)", alerts.size());
    }

    private AlertResult buildAlert(String alertId, String transId, String userId,
                                    String hitRules, String city, String score, String level, String status) {
        return AlertResult.builder()
                .alertId(alertId).transId(transId).userId(userId)
                .hitRules(hitRules).amount(2000.0 + RANDOM.nextDouble() * 48000)
                .finalScore(Integer.parseInt(score)).riskLevel(level)
                .city(city).alertLoc(city)
                .status(status)
                .counterpartyId("CP_" + city)
                .ipAddress("192.168." + RANDOM.nextInt(256) + "." + RANDOM.nextInt(256))
                .isNewDevice(RANDOM.nextInt(2))
                .isNewCounterparty(RANDOM.nextInt(2))
                .rawJson("{\"trans_id\":\"" + transId + "\",\"amount\":" + (2000 + RANDOM.nextInt(48000)) + "}")
                .build();
    }

    private void insertTransactions(long now) {
        String[][] txns = {
            {"TXN_01", "USER_A", "3500", "北京", "116.40,39.90", "5G", "92", "同行转账", "bank_card", "manual"},
            {"TXN_02", "USER_B", "1200", "上海", "121.47,31.23", "WiFi", "95", "跨行转账", "balance", "autofill"},
            {"TXN_03", "USER_C", "800", "杭州", "120.15,30.28", "4G", "88", "同行转账", "wechat", "manual"},
            {"TXN_04", "USER_D", "45000", "深圳", "114.07,22.55", "VPN", "18", "跨行转账", "bank_card", "paste"},
            {"TXN_05", "USER_E", "28000", "广州", "113.26,23.13", "VPN", "25", "对公转账", "alipay", "manual"},
            {"TXN_06", "USER_F", "32000", "成都", "104.06,30.67", "4G", "35", "同行转账", "bank_card", "autofill"},
            {"TXN_07", "USER_G", "15000", "南京", "118.79,32.06", "5G", "90", "跨行转账", "balance", "manual"},
            {"TXN_08", "USER_H", "22000", "武汉", "114.30,30.60", "WiFi", "87", "同行转账", "wechat", "manual"},
            {"TXN_09", "USER_I", "6000", "重庆", "106.55,29.57", "5G", "91", "跨行转账", "bank_card", "autofill"},
            {"TXN_10", "USER_J", "9800", "西安", "108.94,34.26", "4G", "84", "对公转账", "alipay", "manual"},
        };

        String[] oss = {"Android 14.0", "iOS 17.5", "Android 12.0", "iOS 16.3", "Android 13.0"};
        String[] ips = {"10.0.1.1", "172.16.0.1", "192.168.1.100", "10.0.2.15", "172.16.5.8"};

        for (int i = 0; i < txns.length; i++) {
            String[] d = txns[i];
            Transaction t = Transaction.builder()
                    .transId(d[0]).userId(d[1])
                    .amount(Double.parseDouble(d[2]))
                    .transTimestamp(now - i * 60000L).city(d[3]).geoLocation(d[4])
                    .deviceId("DEV_" + d[1]).networkType(d[5])
                    .devScore(Integer.parseInt(d[6]))
                    .transType(d[7]).payChannel(d[8]).inputMethod(d[9])
                    .ipAddress(ips[i % ips.length])
                    .osType(i % 3 == 0 ? "Android" : i % 3 == 1 ? "iOS" : "Web")
                    .osVersion(oss[i % oss.length])
                    .screenResolution("1080×2400")
                    .batteryLevel(50 + RANDOM.nextInt(50))
                    .rootJailbreak(RANDOM.nextInt(2))
                    .simOperator(RANDOM.nextBoolean() ? "移动" : "联通")
                    .userAgent("Mozilla/5.0")
                    .clickDuration(2000L + RANDOM.nextInt(30000))
                    .note(i % 3 == 0 ? "投资返利" : "")
                    .pageUrl("https://bank.example.com/transfer")
                    .counterpartyId("CP_" + d[3])
                    .counterpartyName("张*三")
                    .counterpartyBank("招商银行")
                    .loginSessionId("SESS_" + d[1])
                    .loginFailCount(RANDOM.nextInt(3))
                    .build();
            transactionDao.insert(t);
        }
        log.info("已插入 {} 条交易流水 (含完整29个业务字段)", txns.length);
    }

    private void initMetrics(long now) {
        try {
            // 检查近24小时内是否已有足够的历史快照（至少20个点才能画出有意义的趋势图）
            long since24h = now - 24 * 3600000L;
            int existingCount = metricsDao.countByTimeRange(since24h, now);
            if (existingCount >= 20) {
                log.info("近24H指标快照已充足 ({} 条)，跳过初始化", existingCount);
                return;
            }
            log.info("近24H指标快照不足 ({} 条)，开始生成历史数据...", existingCount);

            // 使用真实交易数据填充快照（仅当前时刻有数据，历史时刻用0或估算）
            Long realTotal = transactionDao.countByTimeRange(now - 3600000L, now);
            Long realPass = transactionDao.countByDevScore(now - 3600000L, now, 80);
            Long realBlock = transactionDao.countByDevScore(now - 3600000L, now, 0)
                    - transactionDao.countByDevScore(now - 3600000L, now, 50);
            Long realUsers = transactionDao.countDistinctUsers(now - 3600000L, now);
            Double realLat = transactionDao.avgClickDuration(now - 3600000L, now);

            List<MetricsSnapshot> list = new ArrayList<>();
            for (int i = 24; i >= 0; i--) {
                long t = now - i * 3600000L;
                // 当前时刻用真实值，历史时刻用模拟值
                boolean isCurrent = (i == 0);
                long total = isCurrent && realTotal != null ? realTotal : 300 + RANDOM.nextInt(200);
                long pass = isCurrent && realPass != null ? realPass : total * 74 / 100;
                long block = isCurrent && realBlock != null ? realBlock : total * 8 / 100;
                long med = total - pass - block;
                long high = block * 60 / 100;
                long low = pass;
                double latency = isCurrent && realLat != null ? realLat : 280.0 + RANDOM.nextDouble() * 200;

                MetricsSnapshot m = MetricsSnapshot.builder()
                        .snapshotTime(t).totalTransactions(total)
                        .passCount(pass).verifyCount(med).blockCount(block)
                        .highRiskCount(high).mediumRiskCount(med).lowRiskCount(low)
                        .avgRiskScore(35.0 + RANDOM.nextDouble() * 20)
                        .avgLatency(latency)
                        .envRiskCount(high / 3)
                        .amountRiskCount(high / 2)
                        .teleportRiskCount(high / 4)
                        .geoRiskCount(med / 2)
                        .build();
                metricsDao.insert(m);
            }
            log.info("已插入 25 个指标快照点");
        } catch (Exception e) {
            log.warn("t_metrics 表不存在或数据库异常，跳过指标初始化: {}", e.getMessage());
        }
    }
}
