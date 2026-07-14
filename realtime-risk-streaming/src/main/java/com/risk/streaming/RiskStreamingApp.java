package com.risk.streaming;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.function.VoidFunction;
import org.apache.spark.streaming.Durations;
import org.apache.spark.streaming.api.java.JavaInputDStream;
import org.apache.spark.streaming.api.java.JavaStreamingContext;
import org.apache.spark.streaming.kafka010.ConsumerStrategies;
import org.apache.spark.streaming.kafka010.KafkaUtils;
import org.apache.spark.streaming.kafka010.LocationStrategies;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.params.SetParams;

import java.io.Serializable;
import java.io.OutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;

public class RiskStreamingApp {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final long METRIC_BUCKET_MS = 10_000L;
    private static final int METRIC_TTL_SECONDS = 48 * 60 * 60;
    private static final int METRIC_DEDUP_TTL_SECONDS = 48 * 60 * 60;

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.fromArgs(args);
        SparkConf sparkConf = new SparkConf().setAppName("risk-streaming-engine");
        if (!sparkConf.contains("spark.master")) {
            sparkConf.setMaster(config.sparkMaster);
        }

        JavaStreamingContext context = new JavaStreamingContext(sparkConf, Durations.milliseconds(config.batchMillis));
        context.sparkContext().setLogLevel(config.logLevel);
        Map<String, Object> kafkaParams = new HashMap<String, Object>();
        kafkaParams.put("bootstrap.servers", config.kafkaBrokers);
        kafkaParams.put("key.deserializer", StringDeserializer.class);
        kafkaParams.put("value.deserializer", StringDeserializer.class);
        kafkaParams.put("group.id", config.kafkaGroup);
        kafkaParams.put("auto.offset.reset", config.kafkaOffsetReset);
        kafkaParams.put("enable.auto.commit", false);

        Collection<String> topics = Arrays.asList(config.kafkaTopic);
        JavaInputDStream<ConsumerRecord<String, String>> stream = KafkaUtils.createDirectStream(
                context,
                LocationStrategies.PreferConsistent(),
                ConsumerStrategies.<String, String>Subscribe(topics, kafkaParams)
        );

        stream.foreachRDD(rdd -> {
            long count = rdd.count();
            if (count == 0) {
                return;
            }
            System.out.println("[risk-streaming] received batch size=" + count);
            rdd.foreachPartition((VoidFunction<Iterator<ConsumerRecord<String, String>>>) iterator -> {
                try (RiskRuntime runtime = new RiskRuntime(config)) {
                    runtime.ensureSchema();
                    while (iterator.hasNext()) {
                        ConsumerRecord<String, String> record = iterator.next();
                        runtime.process(record.value());
                    }
                }
            });
        });

        System.out.println("Risk streaming started: topic=" + config.kafkaTopic
                + ", brokers=" + config.kafkaBrokers
                + ", mysql=" + config.mysqlUrl
                + ", dashboardAlertUrl=" + config.dashboardAlertUrl
                + ", redis=" + config.redisHost + ":" + config.redisPort
                + ", batchMillis=" + config.batchMillis
                + ", logLevel=" + config.logLevel);
        context.start();
        context.awaitTermination();
    }

    static class RiskRuntime implements AutoCloseable {
        private final AppConfig config;
        private final Connection mysql;
        private final Jedis redis;
        private final Random random = new Random();

        RiskRuntime(AppConfig config) throws Exception {
            this.config = config;
            this.mysql = DriverManager.getConnection(config.mysqlUrl, config.mysqlUser, config.mysqlPassword);
            this.mysql.setAutoCommit(true);
            this.redis = new Jedis(config.redisHost, config.redisPort, config.redisTimeoutMs);
            if (config.redisPassword != null && config.redisPassword.trim().length() > 0) {
                this.redis.auth(config.redisPassword);
            }
            if (config.redisDb >= 0) {
                this.redis.select(config.redisDb);
            }
        }

        void process(String rawJson) {
            try {
                JsonNode event = MAPPER.readTree(rawJson);
                Profile profile = loadProfile(text(event, "user_id"));
                CounterpartyRisk counterparty = loadCounterparty(text(event, "counterparty_id"));
                RuleState state = updateRealtimeState(event);
                ChainInfo chain = writeAndCheckChain(event);
                RiskResult result = evaluate(event, profile, counterparty, state, chain, rawJson);
                String decision = riskDecision(result);
                updateRealtimeMetrics(event, result, decision);
                boolean hasRiskSignal = result.hardBlock || result.finalScore > 0 || !result.rules.isEmpty();
                boolean shouldWriteAlert = hasRiskSignal
                        && (config.writeLowRisk || result.finalScore > 40 || result.hardBlock);
                if (shouldWriteAlert) {
                    postRiskAlert(event, result, rawJson);
                }
                System.out.println("[risk-alert] trans_id=" + text(event, "trans_id")
                        + ", score=" + result.finalScore
                        + ", level=" + result.riskLevel
                        + ", decision=" + decision
                        + ", rules=" + result.hitRules);
            } catch (Exception ex) {
                System.err.println("[risk-streaming] process failed: " + ex.getMessage() + ", raw=" + rawJson);
            }
        }

        RiskResult evaluate(JsonNode e, Profile p, CounterpartyRisk c, RuleState s, ChainInfo chain, String rawJson) {
            RiskResult r = new RiskResult();
            double amount = dbl(e, "amount", 0);
            long ts = lng(e, "timestamp", System.currentTimeMillis());
            long now = System.currentTimeMillis();
            LocalDateTime time = LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZONE);
            boolean newDevice = !containsCsv(p.commonDevs, text(e, "device_id"));
            boolean newCounterparty = !containsCsv(p.commonCounterparties, text(e, "counterparty_id"));

            add(r, "A1账户盗用", 60, integer(e, "login_fail_count", 0) >= 5 && !same(text(e, "ip_address"), p.lastIp) && amount > 5000);
            add(r, "A2撞库攻击", 50, s.ipFailedUsers >= 10);
            add(r, "A3新设备登录转账", 30, newDevice && amount > 5000);
            add(r, "A4休眠账户唤醒", 40, p.lastTransTs > 0 && now - p.lastTransTs > days(180) && amount > p.avgAmt30d * 5);
            add(r, "A5新注册大额转账", 50, p.registrationTime > 0 && now - p.registrationTime < days(1) && amount > 10000);
            add(r, "A6账户被标记", 35, same(p.accountStatus, "flagged") || contains(p.riskTags, "victim"));
            add(r, "A7多账号同设备", 45, s.deviceUsers >= 5);
            add(r, "A8异常快速重登录", 25, p.lastLoginTime > 0 && now - p.lastLoginTime < 60000 && !same(text(e, "ip_address"), p.lastIp));

            add(r, "B1设备Root/越狱", 50, bool(e, "root_jailbreak", false));
            add(r, "B2模拟器环境", 40, integer(e, "battery_level", -1) == 100 && !mobileScreen(text(e, "screen_resolution")) && !mobileUa(text(e, "user_agent")));
            add(r, "B3设备频繁更换", 35, s.userDevices30d >= 5);
            add(r, "B4系统版本过旧", 15, oldOs(text(e, "os_type"), text(e, "os_version")));
            add(r, "B5无SIM卡", 20, blank(text(e, "sim_operator")) && !"Web".equalsIgnoreCase(text(e, "os_type")));
            add(r, "B6自动化浏览器", 45, containsAny(text(e, "user_agent"), "HeadlessChrome", "PhantomJS", "Selenium"));

            add(r, "C1金额突变", 30, p.avgAmt30d > 0 && amount > p.avgAmt30d * 3);
            add(r, "C2大额整数转账", 15, amount >= 50000 && Math.abs(amount % 10000) < 0.01);
            add(r, "C3逼近单笔限额", 20, p.singleLimit > 0 && amount >= p.singleLimit * 0.95 && amount < p.singleLimit);
            add(r, "C4余额清零", 40, p.totalBalance > 0 && amount >= p.totalBalance * 0.98);
            add(r, "C5小额测试后大额", 10, amount < 1 && s.smallTestCount >= 1);
            add(r, "C6大额拆分", 45, p.transCount24h >= 3 && amount >= 45000 && amount <= 49999);
            add(r, "C7日累计超限", 25, p.dailyLimit > 0 && p.transAmount24h + amount > p.dailyLimit);
            add(r, "C8月累计超限", 20, p.monthlyLimit > 0 && p.transAmount7d * 4.3 + amount > p.monthlyLimit);

            add(r, "D1地理偏离", 20, !containsCsv(p.commonCities, text(e, "city")));
            add(r, "D2异地瞬移", 70, p.lastTransTs > 0 && !same(text(e, "city"), p.lastCity) && now - p.lastTransTs < 60 * 60 * 1000L);
            add(r, "D3IP与GPS不一致", 40, foreignOrProxyIp(text(e, "ip_address")) && !blank(text(e, "geo_location")));
            add(r, "D4跨境异常", 45, foreignOrProxyIp(text(e, "ip_address")));
            add(r, "D5电诈高危地区", 60, containsAny(text(e, "city"), "缅北", "佤邦", "果敢") || foreignOrProxyIp(text(e, "ip_address")));
            add(r, "D6基站位置异常", 30, "VPN".equalsIgnoreCase(text(e, "network_type")) && !blank(text(e, "sim_operator")));

            add(r, "E1深夜大额交易", 20, time.getHour() >= 2 && time.getHour() < 5 && amount > 5000);
            add(r, "E2非营业日对公转账", 25, (time.getDayOfWeek().getValue() >= 6) && same(text(e, "trans_type"), "对公转账"));
            add(r, "E3登录后极速转账", 35, lng(e, "click_duration", 0) < 10000 && amount > 10000);
            add(r, "E4高频密集交易", 40, s.userTrans60s >= 3);
            add(r, "E5页面停留过短", 30, lng(e, "click_duration", 0) < 800 && amount > 5000);

            if (same(c.riskLevel, "high")) {
                r.hardBlock = true;
                r.add("F1收款方高危黑名单", 300);
            }
            add(r, "F2收款方中风险", 50, same(c.riskLevel, "medium"));
            add(r, "F3首次转账给该收款方", 20, newCounterparty);
            add(r, "F4新注册收款方", 35, c.registrationDays >= 0 && c.registrationDays < 7);
            add(r, "F5收款方快进快出", 55, c.totalReceived7d > 0 && c.totalReceived24h / c.totalReceived7d > 0.8 && c.uniquePayers24h >= 10);
            add(r, "F6个人转对公可疑", 30, containsAny(text(e, "trans_type"), "个人") && containsAny(text(e, "counterparty_bank"), "对公", "企业"));
            add(r, "F7多对一集中收款", 60, c.uniquePayers24h >= 20 && c.totalReceived24h > 500000);
            add(r, "F8收款方被标记", 40, containsAny(c.riskTags, "fraud", "money_mule"));

            add(r, "G1密码粘贴输入", 25, same(text(e, "input_method"), "paste") && amount > 10000);
            add(r, "G2越狱设备自动填充", 30, same(text(e, "input_method"), "autofill") && same(text(e, "os_type"), "Android") && bool(e, "root_jailbreak", false));
            add(r, "G3反复修改收款方", 15, text(e, "note").contains("counterparty_changed_3"));
            add(r, "G4取消后立即重试", 35, integer(e, "cancel_retry_count", 0) >= 2);
            add(r, "G5切换支付渠道", 20, !containsCsv(p.commonPayChannels, text(e, "pay_channel")));
            add(r, "G6备注含诈骗敏感词", 40, containsAny(text(e, "note"), "投资", "返利", "导师", "稳赚", "内幕", "VIP群", "刷单"));
            add(r, "G7来源页面异常", 25, text(e, "page_url").startsWith("http://"));

            add(r, "H1资金回流", 70, chain.loop);
            add(r, "H2多级跳转", 50, chain.depth >= 3);
            add(r, "H3汇聚分散", 55, chain.inboundPayers30m >= 5 && amount > 10000);
            add(r, "H4分散汇聚", 50, chain.outboundReceivers30m >= 5 && amount < 20000);
            add(r, "H5三人以上环形链路", 65, chain.loop && chain.depth >= 3);
            add(r, "H6僵尸账户中转", 40, same(p.accountStatus, "frozen") || same(p.accountStatus, "dormant"));

            add(r, "I1VPN/代理", 35, same(text(e, "network_type"), "VPN") || foreignOrProxyIp(text(e, "ip_address")));
            add(r, "I2Tor网络", 50, containsAny(text(e, "ip_address"), "185.220.101.", "45.13.22."));
            add(r, "I3公共WiFi大额转账", 15, amount > 10000 && containsAny(text(e, "wifi_ssid"), "Free", "Public", "Guest", "Airport", "Mall", "Hotel"));
            add(r, "I4DNS异常", 20, !blank(text(e, "dns_server")) && !containsAny(text(e, "dns_server"), "114.114.114.114", "223.5.5.5"));
            add(r, "I5低安全分设备", 30, integer(e, "dev_score", 100) < 50);
            add(r, "I6HTTP明文页面", 10, text(e, "page_url").startsWith("http://"));

            r.isNewDevice = newDevice ? 1 : 0;
            r.isNewCounterparty = newCounterparty ? 1 : 0;
            r.chainId = chain.chainId;
            r.finish();
            return r;
        }

        private String riskDecision(RiskResult result) {
            if (result.hardBlock || result.finalScore > 120) {
                return "BLOCK";
            }
            if (result.finalScore >= 41) {
                return "REVIEW";
            }
            return "PASS";
        }

        private void updateRealtimeMetrics(JsonNode event, RiskResult result, String decision) {
            String transId = text(event, "trans_id");
            try {
                if (!claimMetricEvent(transId)) {
                    return;
                }

                long bucket = (System.currentTimeMillis() / METRIC_BUCKET_MS) * METRIC_BUCKET_MS;
                String metricKey = "risk:rt:metric:10s:" + bucket;
                String userKey = "risk:rt:users:10s:" + bucket;
                String cityKey = "risk:rt:city:10s:" + bucket;
                String ruleKey = "risk:rt:rule:10s:" + bucket;
                String city = metricLabel(text(event, "city"), "未知");
                String userId = metricLabel(text(event, "user_id"), "未知用户");

                Pipeline pipeline = redis.pipelined();
                pipeline.hincrBy(metricKey, "txn_total", 1);
                pipeline.hincrByFloat(metricKey, "score_sum", result.finalScore);
                pipeline.hincrBy(metricKey, "decision_" + decision.toLowerCase(Locale.ROOT), 1);
                pipeline.hincrBy(metricKey, "risk_" + riskLevelKey(result.riskLevel), 1);
                pipeline.hincrBy(cityKey, city, 1);
                pipeline.pfadd(userKey, userId);

                for (String rule : result.rules) {
                    pipeline.hincrBy(ruleKey, rule, 1);
                    incrementRuleCategory(pipeline, metricKey, rule);
                }

                pipeline.expire(metricKey, METRIC_TTL_SECONDS);
                pipeline.expire(userKey, METRIC_TTL_SECONDS);
                pipeline.expire(cityKey, METRIC_TTL_SECONDS);
                pipeline.expire(ruleKey, METRIC_TTL_SECONDS);
                pipeline.sync();
            } catch (Exception ex) {
                System.err.println("[risk-streaming] update realtime metrics failed: " + ex.getMessage());
            }
        }

        private boolean claimMetricEvent(String transId) {
            if (blank(transId)) {
                return true;
            }
            String key = "risk:rt:dedup:" + transId;
            String claimed = redis.set(key, "1", SetParams.setParams().nx().ex(METRIC_DEDUP_TTL_SECONDS));
            return "OK".equals(claimed);
        }

        private void incrementRuleCategory(Pipeline pipeline, String metricKey, String rule) {
            if (rule == null) {
                return;
            }
            if (rule.contains("异地瞬移")) {
                pipeline.hincrBy(metricKey, "teleport_risk_count", 1);
            } else if (rule.startsWith("D")) {
                pipeline.hincrBy(metricKey, "geo_risk_count", 1);
            }
            if (rule.startsWith("C") || rule.contains("金额") || rule.contains("限额")) {
                pipeline.hincrBy(metricKey, "amount_risk_count", 1);
            }
            if (rule.startsWith("B") || rule.startsWith("I") || rule.contains("VPN") || rule.contains("代理")) {
                pipeline.hincrBy(metricKey, "env_risk_count", 1);
            }
        }

        private String riskLevelKey(String riskLevel) {
            if ("极度危险".equals(riskLevel)) {
                return "critical";
            }
            if ("高危".equals(riskLevel)) {
                return "high";
            }
            if ("中危".equals(riskLevel)) {
                return "medium";
            }
            return "low";
        }

        private String metricLabel(String value, String fallback) {
            return blank(value) ? fallback : value.trim();
        }

        private void add(RiskResult r, String rule, int score, boolean hit) {
            if (hit) {
                r.add(rule, score);
            }
        }

        Profile loadProfile(String userId) {
            Profile p = new Profile();
            p.userId = userId;
            try {
                Map<String, String> values = redis.hgetAll("profile:" + userId);
                if (values != null && !values.isEmpty()) {
                    p.avgAmt30d = doubleMap(values, "avg_amt_30d", 300);
                    p.commonCities = values.get("common_cities");
                    p.commonDevs = values.get("common_devs");
                    p.commonPayChannels = values.get("common_pay_channels");
                    p.commonTransTypes = values.get("common_trans_types");
                    p.commonCounterparties = values.get("common_counterparties");
                    p.lastTransTs = longMap(values, "last_trans_ts", 0);
                    p.lastCity = values.get("last_city");
                    p.lastIp = values.get("last_ip");
                    p.lastLoginTime = longMap(values, "last_login_time", 0);
                    p.registrationTime = longMap(values, "registration_time", 0);
                    p.totalBalance = doubleMap(values, "total_balance", 100000);
                    p.singleLimit = doubleMap(values, "single_limit", 50000);
                    p.dailyLimit = doubleMap(values, "daily_limit", 200000);
                    p.monthlyLimit = doubleMap(values, "monthly_limit", 1000000);
                    p.accountStatus = values.get("account_status");
                    p.transCount24h = intMap(values, "trans_count_24h", 0);
                    p.transAmount24h = doubleMap(values, "trans_amount_24h", 0);
                    p.transAmount7d = doubleMap(values, "trans_amount_7d", 0);
                    p.riskTags = values.get("risk_tags");
                    return p;
                }
            } catch (Exception ignored) {
            }
            loadProfileFromMysql(userId, p);
            return p;
        }

        private void loadProfileFromMysql(String userId, Profile p) {
            try (PreparedStatement ps = mysql.prepareStatement("SELECT * FROM user_profile WHERE user_id = ? LIMIT 1")) {
                ps.setString(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        p.avgAmt30d = getDouble(rs, "avg_amt_30d", 300);
                        p.commonCities = getString(rs, "common_cities", "");
                        p.commonDevs = getString(rs, "common_devs", "");
                        p.commonPayChannels = getString(rs, "common_pay_channels", "");
                        p.commonTransTypes = getString(rs, "common_trans_types", "");
                        p.commonCounterparties = getString(rs, "common_counterparties", "");
                        p.lastTransTs = getLong(rs, "last_trans_ts", 0);
                        p.lastCity = getString(rs, "last_city", "");
                        p.lastIp = getString(rs, "last_ip", "");
                        p.lastLoginTime = getLong(rs, "last_login_time", 0);
                        p.registrationTime = getLong(rs, "registration_time", 0);
                        p.totalBalance = getDouble(rs, "total_balance", 100000);
                        p.singleLimit = getDouble(rs, "single_limit", 50000);
                        p.dailyLimit = getDouble(rs, "daily_limit", 200000);
                        p.monthlyLimit = getDouble(rs, "monthly_limit", 1000000);
                        p.accountStatus = getString(rs, "account_status", "normal");
                        p.transCount24h = getInt(rs, "trans_count_24h", 0);
                        p.transAmount24h = getDouble(rs, "trans_amount_24h", 0);
                        p.transAmount7d = getDouble(rs, "trans_amount_7d", 0);
                        p.riskTags = getString(rs, "risk_tags", "");
                        return;
                    }
                }
            } catch (Exception ignored) {
            }

            try (PreparedStatement ps = mysql.prepareStatement("SELECT * FROM trader_user WHERE user_id = ? LIMIT 1")) {
                ps.setString(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        p.registrationTime = getLong(rs, "registration_time", 0);
                        p.totalBalance = getDouble(rs, "total_balance", 100000);
                        p.singleLimit = getDouble(rs, "single_limit", 50000);
                        p.dailyLimit = getDouble(rs, "daily_limit", 200000);
                        p.monthlyLimit = getDouble(rs, "monthly_limit", 1000000);
                        p.accountStatus = getString(rs, "account_status", "normal");
                        p.commonCities = getString(rs, "default_city", "");
                        p.commonDevs = getString(rs, "default_device_id", "");
                        p.commonPayChannels = getString(rs, "default_pay_channel", "bank_card");
                        p.riskTags = getString(rs, "risk_tags", "");
                    }
                }
            } catch (Exception ignored) {
            }

            try (PreparedStatement ps = mysql.prepareStatement("SELECT counterparty_id FROM trader_counterparty WHERE user_id = ? LIMIT 20")) {
                ps.setString(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    List<String> ids = new ArrayList<String>();
                    while (rs.next()) {
                        ids.add(rs.getString(1));
                    }
                    p.commonCounterparties = join(ids);
                }
            } catch (Exception ignored) {
            }
        }

        CounterpartyRisk loadCounterparty(String counterpartyId) {
            CounterpartyRisk c = new CounterpartyRisk();
            c.counterpartyId = counterpartyId;
            try {
                Map<String, String> values = redis.hgetAll("counterparty:" + counterpartyId);
                if (values != null && !values.isEmpty()) {
                    c.riskLevel = values.get("risk_level");
                    c.riskTags = values.get("risk_tags");
                    c.totalReceived24h = doubleMap(values, "total_received_24h", 0);
                    c.totalReceived7d = doubleMap(values, "total_received_7d", 0);
                    c.uniquePayers24h = intMap(values, "unique_payers_24h", 0);
                    c.registrationDays = intMap(values, "registration_days", -1);
                    return c;
                }
            } catch (Exception ignored) {
            }

            try (PreparedStatement ps = mysql.prepareStatement(
                    "SELECT risk_level, risk_tags, total_received_24h, total_received_7d, unique_payers_24h, registration_days " +
                            "FROM counterparty_blacklist WHERE counterparty_id = ? AND status = 'active' LIMIT 1")) {
                ps.setString(1, counterpartyId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        c.riskLevel = getString(rs, "risk_level", "");
                        c.riskTags = getString(rs, "risk_tags", "");
                        c.totalReceived24h = getDouble(rs, "total_received_24h", 0);
                        c.totalReceived7d = getDouble(rs, "total_received_7d", 0);
                        c.uniquePayers24h = getInt(rs, "unique_payers_24h", 0);
                        c.registrationDays = getInt(rs, "registration_days", -1);
                    }
                }
            } catch (Exception ignored) {
            }
            return c;
        }

        RuleState updateRealtimeState(JsonNode e) {
            RuleState s = new RuleState();
            String userId = text(e, "user_id");
            String ip = text(e, "ip_address");
            String deviceId = text(e, "device_id");
            long minute = System.currentTimeMillis() / 60000L;
            try {
                String ipKey = "rt:ip_fail_users:" + ip + ":" + (minute / 5);
                if (integer(e, "login_fail_count", 0) >= 10) {
                    redis.sadd(ipKey, userId);
                    redis.expire(ipKey, 600);
                }
                s.ipFailedUsers = (int) redis.scard(ipKey).longValue();

                String devKey = "rt:device_users:" + deviceId;
                redis.sadd(devKey, userId);
                redis.expire(devKey, 30 * 86400);
                s.deviceUsers = (int) redis.scard(devKey).longValue();
                s.userDevices30d = s.deviceUsers;

                String user60Key = "rt:user_trans_60s:" + userId + ":" + minute;
                s.userTrans60s = redis.incr(user60Key).intValue();
                redis.expire(user60Key, 120);

                String smallKey = "rt:small_test:" + userId;
                if (dbl(e, "amount", 0) < 1) {
                    s.smallTestCount = redis.incr(smallKey).intValue();
                    redis.expire(smallKey, 86400);
                } else {
                    String existing = redis.get(smallKey);
                    s.smallTestCount = existing == null ? 0 : Integer.parseInt(existing);
                }
            } catch (Exception ignored) {
            }
            return s;
        }

        ChainInfo writeAndCheckChain(JsonNode e) {
            ChainInfo chain = new ChainInfo();
            String userId = text(e, "user_id");
            String counterpartyId = text(e, "counterparty_id");
            long now = System.currentTimeMillis();
            chain.chainId = "CHAIN" + new SimpleDateFormat("yyMMddHHmmss", Locale.CHINA).format(new Date(now)) + randomDigits(6);

            try (PreparedStatement reverse = mysql.prepareStatement(
                    "SELECT chain_id, hop_order, chain_depth FROM trans_chain " +
                            "WHERE user_id = ? AND counterparty_id = ? AND create_time >= DATE_SUB(NOW(), INTERVAL 30 MINUTE) " +
                            "ORDER BY id DESC LIMIT 1")) {
                reverse.setString(1, counterpartyId);
                reverse.setString(2, userId);
                try (ResultSet rs = reverse.executeQuery()) {
                    if (rs.next()) {
                        chain.chainId = rs.getString("chain_id");
                        chain.loop = true;
                        chain.depth = Math.max(2, rs.getInt("chain_depth") + 1);
                        chain.hopOrder = rs.getInt("hop_order") + 1;
                    }
                }
            } catch (Exception ignored) {
            }

            try (PreparedStatement inbound = mysql.prepareStatement(
                    "SELECT COUNT(DISTINCT user_id) FROM trans_chain WHERE counterparty_id = ? AND create_time >= DATE_SUB(NOW(), INTERVAL 30 MINUTE)")) {
                inbound.setString(1, userId);
                try (ResultSet rs = inbound.executeQuery()) {
                    if (rs.next()) {
                        chain.inboundPayers30m = rs.getInt(1);
                    }
                }
            } catch (Exception ignored) {
            }

            try (PreparedStatement outbound = mysql.prepareStatement(
                    "SELECT COUNT(DISTINCT counterparty_id) FROM trans_chain WHERE user_id = ? AND create_time >= DATE_SUB(NOW(), INTERVAL 30 MINUTE)")) {
                outbound.setString(1, userId);
                try (ResultSet rs = outbound.executeQuery()) {
                    if (rs.next()) {
                        chain.outboundReceivers30m = rs.getInt(1);
                    }
                }
            } catch (Exception ignored) {
            }

            try (PreparedStatement insert = mysql.prepareStatement(
                    "INSERT INTO trans_chain (chain_id, trans_id, user_id, counterparty_id, prev_trans_id, hop_order, chain_depth, is_loop, chain_type, amount) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                insert.setString(1, chain.chainId);
                insert.setString(2, text(e, "trans_id"));
                insert.setString(3, userId);
                insert.setString(4, counterpartyId);
                insert.setString(5, null);
                insert.setInt(6, chain.hopOrder);
                insert.setInt(7, chain.depth);
                insert.setInt(8, chain.loop ? 1 : 0);
                insert.setString(9, chain.loop ? "H1" : "normal");
                insert.setDouble(10, dbl(e, "amount", 0));
                insert.executeUpdate();
            } catch (Exception ex) {
                System.err.println("[risk-streaming] insert trans_chain failed: " + ex.getMessage());
            }
            return chain;
        }

        void postRiskAlert(JsonNode e, RiskResult r, String rawJson) throws Exception {
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("alertId", alertId());
            payload.put("transId", text(e, "trans_id"));
            payload.put("userId", text(e, "user_id"));
            payload.put("amount", dbl(e, "amount", 0));
            payload.put("city", text(e, "city"));
            payload.put("hitRules", r.hitRules);
            payload.put("finalScore", r.finalScore);
            payload.put("riskLevel", r.riskLevel);
            payload.put("alertLoc", text(e, "city"));
            payload.put("status", r.hardBlock ? "blocked" : "pending");
            payload.put("counterpartyId", text(e, "counterparty_id"));
            payload.put("ipAddress", text(e, "ip_address"));
            payload.put("isNewDevice", r.isNewDevice);
            payload.put("isNewCounterparty", r.isNewCounterparty);
            payload.put("chainId", r.chainId);
            payload.put("rawJson", rawJson);

            byte[] body = MAPPER.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);
            HttpURLConnection conn = (HttpURLConnection) new URL(config.dashboardAlertUrl).openConnection();
            conn.setConnectTimeout(config.dashboardApiTimeoutMs);
            conn.setReadTimeout(config.dashboardApiTimeoutMs);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body);
            }
            int status = conn.getResponseCode();
            String response = readResponse(conn, status);
            if (status < 200 || status >= 300) {
                throw new SQLException("dashboard alert api returned HTTP " + status + ": " + response);
            }
            if (response != null && response.trim().length() > 0) {
                JsonNode json = MAPPER.readTree(response);
                if (json.has("code") && json.get("code").asInt() != 200) {
                    throw new SQLException("dashboard alert api rejected alert: " + response);
                }
            }
        }

        String readResponse(HttpURLConnection conn, int status) {
            InputStream in = null;
            try {
                in = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
                if (in == null) {
                    return "";
                }
                byte[] buffer = new byte[2048];
                StringBuilder sb = new StringBuilder();
                int n;
                while ((n = in.read(buffer)) >= 0) {
                    sb.append(new String(buffer, 0, n, StandardCharsets.UTF_8));
                }
                return sb.toString();
            } catch (Exception ex) {
                return "";
            } finally {
                try {
                    if (in != null) {
                        in.close();
                    }
                } catch (Exception ignored) {
                }
            }
        }

        void ensureSchema() throws SQLException {
            try (Statement st = mysql.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS trans_chain ("
                        + "id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                        + "chain_id VARCHAR(64) NOT NULL,"
                        + "trans_id VARCHAR(64) NOT NULL,"
                        + "user_id VARCHAR(64) NOT NULL,"
                        + "counterparty_id VARCHAR(64) NOT NULL,"
                        + "prev_trans_id VARCHAR(64) NULL,"
                        + "hop_order INT NOT NULL,"
                        + "chain_depth INT NOT NULL,"
                        + "is_loop TINYINT(1) NOT NULL,"
                        + "chain_type VARCHAR(32) NOT NULL,"
                        + "amount DOUBLE NOT NULL,"
                        + "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                        + "INDEX idx_chain_id (chain_id), INDEX idx_trans_id (trans_id), INDEX idx_user_id (user_id), INDEX idx_counterparty_id (counterparty_id), INDEX idx_create_time (create_time)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            }
            addColumnIfMissing("counterparty_blacklist", "registration_days", "INT NOT NULL DEFAULT 0");
        }

        void addColumnIfMissing(String table, String column, String definition) {
            try (PreparedStatement ps = mysql.prepareStatement(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?")) {
                ps.setString(1, table);
                ps.setString(2, column);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt(1) == 0) {
                        try (Statement st = mysql.createStatement()) {
                            st.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        @Override
        public void close() {
            try {
                redis.close();
            } catch (Exception ignored) {
            }
            try {
                mysql.close();
            } catch (Exception ignored) {
            }
        }

        String alertId() {
            return "ALT" + new SimpleDateFormat("yyMMddHHmmss", Locale.CHINA).format(new Date()) + randomDigits(6);
        }

        String randomDigits(int length) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < length; i++) {
                sb.append(random.nextInt(10));
            }
            return sb.toString();
        }
    }

    static class AppConfig implements Serializable {
        String sparkMaster = "local[2]";
        String kafkaBrokers = "192.168.154.104:9092";
        String kafkaTopic = "trans-event";
        String kafkaGroup = "risk-streaming-engine";
        String kafkaOffsetReset = "latest";
        long batchMillis = 100;
        String mysqlUrl = "jdbc:mysql://192.168.154.104:3306/risk_control?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        String mysqlUser = "root";
        String mysqlPassword = "123456";
        String redisHost = "192.168.154.104";
        int redisPort = 6379;
        String redisPassword = "123456";
        int redisDb = 0;
        int redisTimeoutMs = 3000;
        String dashboardAlertUrl = "http://192.168.154.113:8080/api/data/alert";
        int dashboardApiTimeoutMs = 3000;
        boolean writeLowRisk = true;
        String logLevel = "WARN";

        static AppConfig fromArgs(String[] args) {
            Map<String, String> m = new HashMap<String, String>();
            for (int i = 0; i < args.length; i++) {
                if (args[i].startsWith("--")) {
                    String key = args[i].substring(2);
                    String value = "true";
                    if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                        value = args[++i];
                    }
                    m.put(key, value);
                }
            }
            AppConfig c = new AppConfig();
            c.sparkMaster = m.getOrDefault("spark-master", c.sparkMaster);
            c.kafkaBrokers = m.getOrDefault("kafka-brokers", c.kafkaBrokers);
            c.kafkaTopic = m.getOrDefault("kafka-topic", c.kafkaTopic);
            c.kafkaGroup = m.getOrDefault("kafka-group", c.kafkaGroup);
            c.kafkaOffsetReset = m.getOrDefault("kafka-offset-reset", c.kafkaOffsetReset);
            c.batchMillis = Long.parseLong(m.getOrDefault("batch-ms", String.valueOf(c.batchMillis)));
            c.mysqlUrl = m.getOrDefault("mysql-url", c.mysqlUrl);
            c.mysqlUser = m.getOrDefault("mysql-user", c.mysqlUser);
            c.mysqlPassword = m.getOrDefault("mysql-password", c.mysqlPassword);
            c.redisHost = m.getOrDefault("redis-host", c.redisHost);
            c.redisPort = Integer.parseInt(m.getOrDefault("redis-port", String.valueOf(c.redisPort)));
            c.redisPassword = m.getOrDefault("redis-password", c.redisPassword);
            c.redisDb = Integer.parseInt(m.getOrDefault("redis-db", String.valueOf(c.redisDb)));
            c.dashboardAlertUrl = m.getOrDefault("dashboard-alert-url", c.dashboardAlertUrl);
            c.dashboardApiTimeoutMs = Integer.parseInt(m.getOrDefault("dashboard-api-timeout-ms", String.valueOf(c.dashboardApiTimeoutMs)));
            c.writeLowRisk = Boolean.parseBoolean(m.getOrDefault("write-low-risk", String.valueOf(c.writeLowRisk)));
            c.logLevel = m.getOrDefault("log-level", c.logLevel);
            return c;
        }
    }

    static class Profile implements Serializable {
        String userId = "";
        double avgAmt30d = 300;
        String commonCities = "";
        String commonDevs = "";
        String commonPayChannels = "bank_card";
        String commonTransTypes = "";
        String commonCounterparties = "";
        long lastTransTs = 0;
        String lastCity = "";
        String lastIp = "";
        long lastLoginTime = 0;
        long registrationTime = 0;
        double totalBalance = 100000;
        double singleLimit = 50000;
        double dailyLimit = 200000;
        double monthlyLimit = 1000000;
        String accountStatus = "normal";
        int transCount24h = 0;
        double transAmount24h = 0;
        double transAmount7d = 0;
        String riskTags = "";
    }

    static class CounterpartyRisk implements Serializable {
        String counterpartyId = "";
        String riskLevel = "";
        String riskTags = "";
        double totalReceived24h = 0;
        double totalReceived7d = 0;
        int uniquePayers24h = 0;
        int registrationDays = -1;
    }

    static class RuleState implements Serializable {
        int ipFailedUsers = 0;
        int deviceUsers = 0;
        int userDevices30d = 0;
        int userTrans60s = 0;
        int smallTestCount = 0;
    }

    static class ChainInfo implements Serializable {
        String chainId = "";
        int hopOrder = 1;
        int depth = 1;
        boolean loop = false;
        int inboundPayers30m = 0;
        int outboundReceivers30m = 0;
    }

    static class RiskResult implements Serializable {
        List<String> rules = new ArrayList<String>();
        int finalScore = 0;
        boolean hardBlock = false;
        String riskLevel = "低危";
        String hitRules = "无";
        int isNewDevice = 0;
        int isNewCounterparty = 0;
        String chainId = "";

        void add(String rule, int score) {
            rules.add(rule);
            finalScore += score;
        }

        void finish() {
            if (rules.isEmpty()) {
                hitRules = "无";
            } else {
                hitRules = join(rules);
            }
            if (hardBlock) {
                riskLevel = "极度危险";
            } else if (finalScore > 120) {
                riskLevel = "极度危险";
            } else if (finalScore >= 71) {
                riskLevel = "高危";
            } else if (finalScore >= 41) {
                riskLevel = "中危";
            } else {
                riskLevel = "低危";
            }
        }
    }

    static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? "" : v.asText("");
    }

    static int integer(JsonNode node, String field, int def) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? def : v.asInt(def);
    }

    static long lng(JsonNode node, String field, long def) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? def : v.asLong(def);
    }

    static double dbl(JsonNode node, String field, double def) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? def : v.asDouble(def);
    }

    static boolean bool(JsonNode node, String field, boolean def) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? def : v.asBoolean(def);
    }

    static boolean containsCsv(String csv, String value) {
        if (blank(csv) || blank(value)) {
            return false;
        }
        for (String part : csv.split(",")) {
            if (part.trim().equalsIgnoreCase(value.trim())) {
                return true;
            }
        }
        return false;
    }

    static boolean contains(String text, String token) {
        return text != null && token != null && text.toLowerCase(Locale.ROOT).contains(token.toLowerCase(Locale.ROOT));
    }

    static boolean containsAny(String text, String... tokens) {
        if (text == null) {
            return false;
        }
        for (String token : tokens) {
            if (contains(text, token)) {
                return true;
            }
        }
        return false;
    }

    static boolean same(String a, String b) {
        return a != null && b != null && a.trim().equalsIgnoreCase(b.trim());
    }

    static boolean blank(String s) {
        return s == null || s.trim().length() == 0 || "null".equalsIgnoreCase(s.trim());
    }

    static long days(int n) {
        return n * 86400L * 1000L;
    }

    static boolean mobileUa(String ua) {
        return containsAny(ua, "Mobile", "Android", "iPhone");
    }

    static boolean mobileScreen(String resolution) {
        return containsAny(resolution, "720x1600", "1080x1920", "1080x2400", "1170x2532", "1440x3200");
    }

    static boolean oldOs(String osType, String osVersion) {
        if (same(osType, "Android")) {
            return extractVersion(osVersion) < 8.0;
        }
        if (same(osType, "iOS")) {
            return extractVersion(osVersion) < 12.0;
        }
        return false;
    }

    static double extractVersion(String s) {
        if (s == null) {
            return 99;
        }
        String cleaned = s.replaceAll("[^0-9.]", "");
        if (cleaned.length() == 0) {
            return 99;
        }
        try {
            return Double.parseDouble(cleaned);
        } catch (Exception ex) {
            return 99;
        }
    }

    static boolean foreignOrProxyIp(String ip) {
        return ip.startsWith("45.") || ip.startsWith("185.") || ip.startsWith("103.") || ip.startsWith("8.219.");
    }

    static String join(Collection<String> values) {
        StringBuilder sb = new StringBuilder();
        for (String v : values) {
            if (sb.length() > 0) {
                sb.append(";");
            }
            sb.append(v);
        }
        return sb.toString();
    }

    static double doubleMap(Map<String, String> map, String key, double def) {
        try {
            String value = map.get(key);
            return value == null ? def : Double.parseDouble(value);
        } catch (Exception ex) {
            return def;
        }
    }

    static long longMap(Map<String, String> map, String key, long def) {
        try {
            String value = map.get(key);
            return value == null ? def : Long.parseLong(value);
        } catch (Exception ex) {
            return def;
        }
    }

    static int intMap(Map<String, String> map, String key, int def) {
        try {
            String value = map.get(key);
            return value == null ? def : Integer.parseInt(value);
        } catch (Exception ex) {
            return def;
        }
    }

    static String getString(ResultSet rs, String column, String def) {
        try {
            String value = rs.getString(column);
            return value == null ? def : value;
        } catch (Exception ex) {
            return def;
        }
    }

    static int getInt(ResultSet rs, String column, int def) {
        try {
            return rs.getInt(column);
        } catch (Exception ex) {
            return def;
        }
    }

    static long getLong(ResultSet rs, String column, long def) {
        try {
            return rs.getLong(column);
        } catch (Exception ex) {
            return def;
        }
    }

    static double getDouble(ResultSet rs, String column, double def) {
        try {
            return rs.getDouble(column);
        } catch (Exception ex) {
            return def;
        }
    }
}
