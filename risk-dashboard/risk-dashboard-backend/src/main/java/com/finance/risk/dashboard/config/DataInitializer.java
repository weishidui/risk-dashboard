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

        long alertCount = alertDao.count(null, null, null, null);
        if (alertCount > 20) {
            log.info("告警数据已充足 ({} 条)，跳过初始化", alertCount);
            initMetrics(System.currentTimeMillis());
            return;
        }

        long now = System.currentTimeMillis();
        log.info("开始初始化演示数据...");
        insertTransactions(now);
        insertAlerts(now);
        initMetrics(now);
        log.info("演示数据初始化完成");
    }

    private void insertTransactions(long now) {
        // 19 笔交易，覆盖 19 个城市，匹配 19 条告警
        String[][] txns = {
            // 极度危险交易
            {"TXN_01", "USER_D", "45000", "深圳",   "114.07,22.55", "VPN",   "18", "跨行转账", "bank_card", "paste",   "Android 14.0", "10.0.2.15"},
            {"TXN_02", "USER_K", "52000", "哈尔滨", "126.54,45.80", "VPN",   "12", "对公转账", "bank_card", "paste",   "Android 12.0", "45.33.xx.xx"},
            // 高危交易
            {"TXN_03", "USER_H", "22000", "武汉",   "114.30,30.60", "WiFi",   "87", "同行转账", "wechat",    "manual",  "iOS 17.5",     "172.16.0.1"},
            {"TXN_04", "USER_L", "38000", "福州",   "119.30,26.07", "VPN",   "22", "跨行转账", "alipay",    "manual",  "iOS 16.3",     "103.xx.xx.xx"},
            {"TXN_05", "USER_M", "25000", "南宁",   "108.37,22.82", "4G",    "28", "对公转账", "bank_card", "paste",   "Android 13.0", "10.0.1.1"},
            {"TXN_06", "USER_N", "19000", "昆明",   "102.83,24.88", "WiFi",   "32", "跨行转账", "wechat",    "manual",  "Android 14.0", "192.168.1.100"},
            // 中危交易
            {"TXN_07", "USER_G", "15000", "南京",   "118.79,32.06", "5G",    "90", "跨行转账", "balance",   "manual",  "iOS 17.5",     "172.16.5.8"},
            {"TXN_08", "USER_J", "9800",  "西安",   "108.94,34.26", "4G",    "84", "对公转账", "alipay",    "manual",  "Android 14.0", "10.0.1.1"},
            {"TXN_09", "USER_O", "8500",  "长沙",   "112.94,28.23", "5G",    "65", "同行转账", "bank_card", "autofill","Android 12.0", "172.16.0.1"},
            {"TXN_10", "USER_P", "12000", "郑州",   "113.62,34.75", "4G",    "55", "跨行转账", "alipay",    "manual",  "iOS 16.3",     "192.168.1.100"},
            {"TXN_11", "USER_Q", "7800",  "沈阳",   "123.46,41.68", "WiFi",   "48", "同行转账", "bank_card", "paste",   "Android 13.0", "10.0.2.15"},
            {"TXN_12", "USER_R", "9200",  "济南",   "117.12,36.65", "5G",    "52", "跨行转账", "wechat",    "autofill","iOS 17.5",     "172.16.5.8"},
            {"TXN_13", "USER_S", "11000", "合肥",   "117.23,31.82", "4G",    "58", "对公转账", "alipay",    "manual",  "Android 14.0", "10.0.1.1"},
            // 低危交易
            {"TXN_14", "USER_A", "3500",  "北京",   "116.40,39.90", "5G",    "92", "同行转账", "bank_card", "manual",  "iOS 16.3",     "10.0.1.1"},
            {"TXN_15", "USER_B", "1200",  "上海",   "121.47,31.23", "WiFi",   "95", "跨行转账", "balance",   "autofill","Android 14.0", "172.16.0.1"},
            {"TXN_16", "USER_C", "800",   "杭州",   "120.15,30.28", "4G",    "88", "同行转账", "wechat",    "manual",  "iOS 17.5",     "192.168.1.100"},
            {"TXN_17", "USER_E", "28000", "广州",   "113.26,23.13", "VPN",   "25", "对公转账", "alipay",    "manual",  "Android 13.0", "10.0.2.15"},
            {"TXN_18", "USER_F", "32000", "成都",   "104.06,30.67", "4G",    "35", "同行转账", "bank_card", "autofill","iOS 16.3",     "172.16.5.8"},
            {"TXN_19", "USER_I", "6000",  "重庆",   "106.55,29.57", "5G",    "91", "跨行转账", "bank_card", "autofill","Android 13.0", "192.168.1.100"},
        };

        for (int i = 0; i < txns.length; i++) {
            String[] d = txns[i];
            Transaction t = Transaction.builder()
                    .transId(d[0]).userId(d[1])
                    .amount(Double.parseDouble(d[2]))
                    .transTimestamp(now - i * 90000L).city(d[3]).geoLocation(d[4])
                    .deviceId("DEV_" + d[1]).networkType(d[5])
                    .devScore(Integer.parseInt(d[6]))
                    .transType(d[7]).payChannel(d[8]).inputMethod(d[9])
                    .ipAddress(d[11])
                    .osType(d[10].startsWith("Android") ? "Android" : d[10].startsWith("iOS") ? "iOS" : "Web")
                    .osVersion(d[10])
                    .screenResolution("1080×2400")
                    .batteryLevel(50 + RANDOM.nextInt(50))
                    .rootJailbreak(RANDOM.nextInt(2))
                    .simOperator(RANDOM.nextBoolean() ? "移动" : "联通")
                    .userAgent("Mozilla/5.0")
                    .clickDuration(2000L + RANDOM.nextInt(30000))
                    .note(i <= 1 ? "紧急转账" : i >= 7 && i <= 12 ? "日常消费" : "")
                    .pageUrl("https://bank.example.com/transfer")
                    .counterpartyId("CP_" + d[3])
                    .counterpartyName("张*三")
                    .counterpartyBank("招商银行")
                    .loginSessionId("SESS_" + d[1])
                    .loginFailCount(i <= 1 ? 3 : RANDOM.nextInt(2))
                    .build();
            transactionDao.insert(t);
        }
        log.info("已插入 {} 条交易流水 (含完整29个业务字段)", txns.length);
    }

    private void insertAlerts(long now) {
        List<AlertResult> alerts = new ArrayList<>();

        // 每条告警的 transId 指向真实交易，city/userId/amount 与交易一致
        // 极度危险
        alerts.add(buildAlert("ALT_001", "TXN_01", "USER_D", "A1账户盗用;C1金额突变;D2异地瞬移;I1_VPN代理",    "深圳",   "145", "极度危险", "pending"));
        alerts.add(buildAlert("ALT_002", "TXN_02", "USER_K", "A3新设备登录转账;B1设备Root越狱;E3登录后极速转账;H1资金回流", "哈尔滨", "135", "极度危险", "pending"));

        // 高危
        alerts.add(buildAlert("ALT_003", "TXN_03", "USER_H", "A1账户盗用;D1地理偏离;E1深夜大额交易",            "武汉",   "95",  "高危", "processing"));
        alerts.add(buildAlert("ALT_004", "TXN_04", "USER_L", "C1金额突变;D2异地瞬移;I5低安全分设备",            "福州",   "88",  "高危", "processing"));
        alerts.add(buildAlert("ALT_005", "TXN_05", "USER_M", "B1设备Root越狱;F2收款方中风险;G1密码粘贴输入",   "南宁",   "82",  "高危", "processing"));
        alerts.add(buildAlert("ALT_006", "TXN_06", "USER_N", "A5新注册大额转账;D3_IP与GPS不一致;F4新注册收款方","昆明",   "78",  "高危", "pending"));

        // 中危
        alerts.add(buildAlert("ALT_007", "TXN_07", "USER_G", "C1金额突变;D1地理偏离",                          "南京",   "55",  "中危", "pending"));
        alerts.add(buildAlert("ALT_008", "TXN_08", "USER_J", "A3新设备登录转账;G5切换支付渠道",                 "西安",   "60",  "中危", "pending"));
        alerts.add(buildAlert("ALT_009", "TXN_09", "USER_O", "D1地理偏离;I1_VPN代理",                          "长沙",   "48",  "中危", "verified"));
        alerts.add(buildAlert("ALT_010", "TXN_10", "USER_P", "C1金额突变;E5页面停留过短",                       "郑州",   "65",  "中危", "pending"));
        alerts.add(buildAlert("ALT_011", "TXN_11", "USER_Q", "B5无SIM卡;I4_DNS异常",                           "沈阳",   "42",  "中危", "verified"));
        alerts.add(buildAlert("ALT_012", "TXN_12", "USER_R", "G6备注含诈骗敏感词",                              "济南",   "45",  "中危", "closed"));
        alerts.add(buildAlert("ALT_013", "TXN_13", "USER_S", "F3首次转账给该收款方",                            "合肥",   "38",  "中危", "verified"));

        // 低危
        alerts.add(buildAlert("ALT_014", "TXN_14", "USER_A", "无",                                            "北京",   "10",  "低危", "closed"));
        alerts.add(buildAlert("ALT_015", "TXN_15", "USER_B", "无",                                            "上海",   "8",   "低危", "closed"));
        alerts.add(buildAlert("ALT_016", "TXN_16", "USER_C", "C5小额测试",                                    "杭州",   "12",  "低危", "closed"));
        alerts.add(buildAlert("ALT_017", "TXN_17", "USER_E", "无",                                            "广州",   "15",  "低危", "closed"));
        alerts.add(buildAlert("ALT_018", "TXN_18", "USER_F", "无",                                            "成都",   "5",   "低危", "closed"));
        alerts.add(buildAlert("ALT_019", "TXN_19", "USER_I", "无",                                            "重庆",   "18",  "低危", "closed"));

        for (AlertResult a : alerts) {
            alertDao.insert(a);
        }
        log.info("已插入 {} 条告警 (关联真实交易流水)", alerts.size());
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

    private void initMetrics(long now) {
        try {
            long since24h = now - 24 * 3600000L;
            int existingCount = metricsDao.countByTimeRange(since24h, now);
            if (existingCount >= 20) {
                log.info("近24H指标快照已充足 ({} 条)，跳过初始化", existingCount);
                return;
            }
            log.info("近24H指标快照不足 ({} 条)，开始生成历史数据...", existingCount);

            Long realTotal = transactionDao.countByTimeRange(now - 3600000L, now);
            Long realPass = transactionDao.countByDevScore(now - 3600000L, now, 80);
            Long realBlock = transactionDao.countByDevScore(now - 3600000L, now, 0)
                    - transactionDao.countByDevScore(now - 3600000L, now, 50);
            Long realUsers = transactionDao.countDistinctUsers(now - 3600000L, now);
            Double realLat = transactionDao.avgClickDuration(now - 3600000L, now);

            List<MetricsSnapshot> list = new ArrayList<>();
            for (int i = 24; i >= 0; i--) {
                long t = now - i * 3600000L;
                boolean isCurrent = (i == 0);
                long total = isCurrent && realTotal != null ? realTotal : 300 + RANDOM.nextInt(200);
                long pass = isCurrent && realPass != null ? realPass : total * 74 / 100;
                long block = isCurrent && realBlock != null ? realBlock : total * 8 / 100;
                long med = total - pass - block;
                long high = block * 60 / 100;
                long low = pass;
                // 系统延迟用合理范围(180-450ms)，而不是点击耗时
                double latency = 180.0 + RANDOM.nextDouble() * 270;

                // 风险评分用告警等级加权: 高危90 + 中危65 + 低危20
                double riskScore = 0;
                if (isCurrent) {
                    long totalAlerts = high + med + low;
                    riskScore = totalAlerts > 0 ? (high * 90.0 + med * 65.0 + low * 20.0) / totalAlerts : 0;
                } else {
                    riskScore = 35.0 + RANDOM.nextDouble() * 20;
                }

                MetricsSnapshot m = MetricsSnapshot.builder()
                        .snapshotTime(t).totalTransactions(total)
                        .passCount(pass).verifyCount(med).blockCount(block)
                        .highRiskCount(high).mediumRiskCount(med).lowRiskCount(low)
                        .avgRiskScore(Math.round(riskScore * 100.0) / 100.0)
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
