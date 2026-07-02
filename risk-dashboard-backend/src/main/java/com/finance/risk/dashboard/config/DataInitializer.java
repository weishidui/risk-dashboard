package com.finance.risk.dashboard.config;

import com.finance.risk.dashboard.dao.AlertDao;
import com.finance.risk.dashboard.dao.MetricsDao;
import com.finance.risk.dashboard.dao.TransactionDao;
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

/**
 * 启动时自动初始化演示数据
 */
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

    @Override
    public void run(String... args) {

        // 1. 刷新旧数据时间戳
        int refreshed = alertDao.refreshTimestamps();
        log.info("时间戳刷新: {} 条历史告警", refreshed);

        // 2. 只有告警表为空时才灌入数据
        long alertCount = alertDao.count(null, null, null);
        if (alertCount > 20) {
            log.info("告警数据已充足 ({} 条)，跳过初始化", alertCount);
            initMetrics(System.currentTimeMillis());
            return;
        }

        // 3. 补充告警与交易数据
        long now = System.currentTimeMillis();
        log.info("开始初始化演示数据...");
        insertAlerts(now);
        insertTransactions(now);
        initMetrics(now);
        log.info("演示数据初始化完成");
    }

    private void insertAlerts(long now) {
        List<AlertResult> alerts = new ArrayList<>();

        String[][] highCities = {
            {"深圳", "金额突变;异地瞬移;环境风险", "95"},
            {"武汉", "异地瞬移;环境风险", "88"},
            {"哈尔滨", "金额突变;环境风险", "82"},
            {"福州", "金额突变;异地瞬移;环境风险", "93"},
            {"南宁", "异地瞬移;环境风险", "86"},
        };
        for (String[] c : highCities) {
            alerts.add(buildAlert("HI_" + c[0], "TH_" + c[0], "U_HIGH", c[1], c[0], c[0], c[2], "高危"));
            alerts.add(buildAlert("HI2_" + c[0], "TH2_" + c[0], "U_HIGH2", c[1], c[0], c[0], c[2], "高危"));
        }

        String[][] midCities = {
            {"南京", "地理偏离", "40"}, {"西安", "金额突变;地理偏离", "55"},
            {"长沙", "环境风险", "48"}, {"郑州", "金额突变", "60"},
            {"昆明", "环境风险;地理偏离", "52"}, {"沈阳", "地理偏离", "38"},
            {"济南", "金额突变", "45"}, {"合肥", "环境风险", "42"},
        };
        for (String[] c : midCities) {
            alerts.add(buildAlert("MI_" + c[0], "TM_" + c[0], "U_MID", c[1], c[0], c[0], c[2], "中危"));
        }

        String[][] lowCities = {
            {"北京", "无", "10"}, {"上海", "无", "8"}, {"杭州", "无", "12"},
            {"广州", "无", "15"}, {"成都", "无", "5"}, {"重庆", "无", "18"},
        };
        for (String[] c : lowCities) {
            alerts.add(buildAlert("LO_" + c[0], "TL_" + c[0], "U_LOW", c[1], c[0], c[0], c[2], "低危"));
        }

        for (AlertResult a : alerts) {
            alertDao.insert(a);
        }
        log.info("已插入 {} 条告警", alerts.size());
    }

    private AlertResult buildAlert(String alertId, String transId, String userId,
                                    String hitRules, String city, String alertLoc,
                                    String score, String level) {
        return AlertResult.builder()
                .alertId(alertId).transId(transId).userId(userId)
                .hitRules(hitRules).amount(2000.0 + RANDOM.nextDouble() * 48000)
                .finalScore(Integer.parseInt(score)).riskLevel(level)
                .city(city).alertLoc(alertLoc)
                .rawJson("{\"trans_id\":\"" + transId + "\",\"amount\":" + (2000 + RANDOM.nextInt(48000)) + "}")
                .build();
    }

    private void insertTransactions(long now) {
        String[][] txns = {
            {"TXN_01", "USER_A", "3500", "北京", "116.40,39.90", "5G", "92"},
            {"TXN_02", "USER_B", "1200", "上海", "121.47,31.23", "WiFi", "95"},
            {"TXN_03", "USER_C", "800", "杭州", "120.15,30.28", "4G", "88"},
            {"TXN_04", "USER_D", "45000", "深圳", "114.07,22.55", "VPN", "18"},
            {"TXN_05", "USER_E", "28000", "广州", "113.26,23.13", "VPN", "25"},
            {"TXN_06", "USER_F", "32000", "成都", "104.06,30.67", "4G", "35"},
            {"TXN_07", "USER_G", "15000", "南京", "118.79,32.06", "5G", "90"},
            {"TXN_08", "USER_H", "22000", "武汉", "114.30,30.60", "WiFi", "87"},
            {"TXN_09", "USER_I", "6000", "重庆", "106.55,29.57", "5G", "91"},
            {"TXN_10", "USER_J", "9800", "西安", "108.94,34.26", "4G", "84"},
        };

        for (String[] d : txns) {
            Transaction t = Transaction.builder()
                    .transId(d[0]).userId(d[1])
                    .amount(Double.parseDouble(d[2]))
                    .transTimestamp(now).city(d[3]).geoLocation(d[4])
                    .deviceId("DEV_" + d[1]).networkType(d[5])
                    .devScore(Integer.parseInt(d[6]))
                    .build();
            transactionDao.insert(t);
        }
        log.info("已插入 {} 条交易流水", txns.length);
    }

    private void initMetrics(long now) {
        if (metricsDao.findLatest() != null) {
            log.info("指标数据已存在，跳过");
            return;
        }

        List<MetricsSnapshot> list = new ArrayList<>();
        for (int i = 24; i >= 0; i--) {
            long t = now - i * 3600000L;
            int base = 300 + RANDOM.nextInt(200);
            int high = base * 8 / 100;
            int med = base * 18 / 100;
            int low = base - high - med;

            MetricsSnapshot m = MetricsSnapshot.builder()
                    .snapshotTime(t).totalTransactions((long) base)
                    .passCount((long) low).verifyCount((long) med).blockCount((long) high)
                    .highRiskCount((long) high).mediumRiskCount((long) med).lowRiskCount((long) low)
                    .avgRiskScore(35.0 + RANDOM.nextDouble() * 20)
                    .avgLatency(280.0 + RANDOM.nextDouble() * 200)
                    .envRiskCount((long) (high / 3))
                    .amountRiskCount((long) (high / 2))
                    .teleportRiskCount((long) (high / 4))
                    .geoRiskCount((long) (med / 2))
                    .build();
            metricsDao.insert(m);
        }
        log.info("已插入 25 个指标快照点");
    }
}
