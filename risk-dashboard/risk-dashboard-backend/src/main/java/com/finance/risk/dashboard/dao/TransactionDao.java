package com.finance.risk.dashboard.dao;

import com.finance.risk.dashboard.entity.Transaction;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface TransactionDao {

    @Insert("INSERT INTO transaction_history(trans_id, user_id, amount, trans_timestamp, city, " +
            "geo_location, device_id, network_type, dev_score, " +
            "ip_address, os_type, os_version, screen_resolution, battery_level, root_jailbreak, " +
            "sim_operator, user_agent, dns_server, wifi_ssid, " +
            "trans_type, pay_channel, input_method, click_duration, note, page_url, " +
            "counterparty_id, counterparty_name, counterparty_bank, " +
            "login_session_id, login_fail_count) " +
            "VALUES(#{transId}, #{userId}, #{amount}, #{transTimestamp}, #{city}, " +
            "#{geoLocation}, #{deviceId}, #{networkType}, #{devScore}, " +
            "#{ipAddress}, #{osType}, #{osVersion}, #{screenResolution}, #{batteryLevel}, #{rootJailbreak}, " +
            "#{simOperator}, #{userAgent}, #{dnsServer}, #{wifiSsid}, " +
            "#{transType}, #{payChannel}, #{inputMethod}, #{clickDuration}, #{note}, #{pageUrl}, " +
            "#{counterpartyId}, #{counterpartyName}, #{counterpartyBank}, " +
            "#{loginSessionId}, #{loginFailCount})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Transaction transaction);

    @Select("SELECT * FROM transaction_history WHERE trans_id = #{transId}")
    Transaction findByTransId(@Param("transId") String transId);

    @Select("SELECT * FROM transaction_history ORDER BY create_time DESC LIMIT #{limit}")
    List<Transaction> findRecent(@Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM transaction_history WHERE trans_timestamp BETWEEN #{startTime} AND #{endTime}")
    Long countByTimeRange(@Param("startTime") Long startTime, @Param("endTime") Long endTime);

    @Select("SELECT city, COUNT(*) as cnt FROM transaction_history " +
            "WHERE trans_timestamp > #{sinceTime} AND city IS NOT NULL " +
            "GROUP BY city ORDER BY cnt DESC LIMIT #{limit}")
    List<CityCount> countByCity(@Param("sinceTime") Long sinceTime, @Param("limit") int limit);

    /** 近一段时间内不重复用户数 */
    @Select("SELECT COUNT(DISTINCT user_id) FROM transaction_history WHERE trans_timestamp BETWEEN #{startTime} AND #{endTime}")
    Long countDistinctUsers(@Param("startTime") Long startTime, @Param("endTime") Long endTime);

    /** 近一段时间内平均点击耗时(ms)，作为延迟的代理指标 */
    @Select("SELECT COALESCE(AVG(click_duration), 0) FROM transaction_history WHERE trans_timestamp BETWEEN #{startTime} AND #{endTime}")
    Double avgClickDuration(@Param("startTime") Long startTime, @Param("endTime") Long endTime);

    /** 按设备安全分统计安全/可疑交易 */
    @Select("SELECT COALESCE(COUNT(*), 0) FROM transaction_history WHERE trans_timestamp BETWEEN #{startTime} AND #{endTime} AND dev_score >= #{minScore}")
    Long countByDevScore(@Param("startTime") Long startTime, @Param("endTime") Long endTime, @Param("minScore") int minScore);

    /** 按网络类型统计 */
    @Select("SELECT COALESCE(COUNT(*), 0) FROM transaction_history WHERE trans_timestamp BETWEEN #{startTime} AND #{endTime} AND network_type = #{networkType}")
    Long countByNetworkType(@Param("startTime") Long startTime, @Param("endTime") Long endTime, @Param("networkType") String networkType);

    // ========== 交易行为统计（离线分析模块6） ==========

    /** 每日交易量趋势 */
    @Select("SELECT DATE(FROM_UNIXTIME(trans_timestamp/1000)) as dt, COUNT(*) as cnt " +
            "FROM transaction_history WHERE trans_timestamp >= #{sinceTs} " +
            "GROUP BY dt ORDER BY dt")
    List<DailyCount> countByDay(@Param("sinceTs") long sinceTs);

    /** 支付渠道分布 */
    @Select("SELECT pay_channel, COUNT(*) as cnt FROM transaction_history " +
            "WHERE pay_channel IS NOT NULL GROUP BY pay_channel ORDER BY cnt DESC")
    List<ChannelCount> countByPayChannel();

    /** 交易类型分布 */
    @Select("SELECT trans_type, COUNT(*) as cnt FROM transaction_history " +
            "WHERE trans_type IS NOT NULL GROUP BY trans_type ORDER BY cnt DESC")
    List<TypeCount> countByTransType();

    /** 金额区间分布 */
    @Select("SELECT CASE " +
            "WHEN amount < 1000 THEN '0-1千' " +
            "WHEN amount < 5000 THEN '1千-5千' " +
            "WHEN amount < 10000 THEN '5千-1万' " +
            "WHEN amount < 50000 THEN '1万-5万' " +
            "WHEN amount < 100000 THEN '5万-10万' " +
            "ELSE '10万以上' END as rng, COUNT(*) as cnt " +
            "FROM transaction_history GROUP BY rng ORDER BY MIN(amount)")
    List<RangeCount> countByAmountRange();

    /** 设备聚合（代替 Redis device_risk 扫描） */
    @Select("SELECT device_id, COUNT(DISTINCT user_id) as user_count, COUNT(*) as trans_count, " +
            "AVG(dev_score) as avg_score, SUM(CASE WHEN root_jailbreak=1 THEN 1 ELSE 0 END) as jailbreak_count, " +
            "COUNT(DISTINCT city) as city_count " +
            "FROM transaction_history WHERE device_id IS NOT NULL " +
            "GROUP BY device_id ORDER BY trans_count DESC LIMIT #{offset}, #{limit}")
    List<DeviceAgg> aggregateByDevice(@Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(DISTINCT device_id) FROM transaction_history WHERE device_id IS NOT NULL")
    Long countDistinctDevices();

    /** 收款方聚合（代替 HDFS dws_counterparty_daily） */
    @Select("SELECT counterparty_id, COUNT(*) as trans_count, SUM(amount) as total_amount, " +
            "COUNT(DISTINCT user_id) as payer_count " +
            "FROM transaction_history WHERE counterparty_id IS NOT NULL " +
            "GROUP BY counterparty_id ORDER BY total_amount DESC LIMIT #{offset}, #{limit}")
    List<CounterpartyAgg> aggregateByCounterparty(@Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(DISTINCT counterparty_id) FROM transaction_history WHERE counterparty_id IS NOT NULL")
    Long countDistinctCounterparties();

    // ========== 统计内部类 ==========
    class CityCount {
        private String city; private Long cnt;
        public String getCity() { return city; } public void setCity(String city) { this.city = city; }
        public Long getCnt() { return cnt; } public void setCnt(Long cnt) { this.cnt = cnt; }
    }
    class DailyCount {
        private String dt; private Long cnt;
        public String getDt() { return dt; } public void setDt(String dt) { this.dt = dt; }
        public Long getCnt() { return cnt; } public void setCnt(Long cnt) { this.cnt = cnt; }
    }
    class ChannelCount {
        private String payChannel; private Long cnt;
        public String getPayChannel() { return payChannel; } public void setPayChannel(String v) { this.payChannel = v; }
        public Long getCnt() { return cnt; } public void setCnt(Long v) { this.cnt = v; }
    }
    class TypeCount {
        private String transType; private Long cnt;
        public String getTransType() { return transType; } public void setTransType(String v) { this.transType = v; }
        public Long getCnt() { return cnt; } public void setCnt(Long v) { this.cnt = v; }
    }
    class RangeCount {
        private String rng; private Long cnt;
        public String getRng() { return rng; } public void setRng(String v) { this.rng = v; }
        public Long getCnt() { return cnt; } public void setCnt(Long v) { this.cnt = v; }
    }
    class DeviceAgg {
        private String deviceId; private Long userCount; private Long transCount;
        private Double avgScore; private Long jailbreakCount; private Long cityCount;
        public String getDeviceId() { return deviceId; } public void setDeviceId(String v) { this.deviceId = v; }
        public Long getUserCount() { return userCount; } public void setUserCount(Long v) { this.userCount = v; }
        public Long getTransCount() { return transCount; } public void setTransCount(Long v) { this.transCount = v; }
        public Double getAvgScore() { return avgScore; } public void setAvgScore(Double v) { this.avgScore = v; }
        public Long getJailbreakCount() { return jailbreakCount; } public void setJailbreakCount(Long v) { this.jailbreakCount = v; }
        public Long getCityCount() { return cityCount; } public void setCityCount(Long v) { this.cityCount = v; }
    }
    class CounterpartyAgg {
        private String counterpartyId; private Long transCount; private Double totalAmount; private Long payerCount;
        public String getCounterpartyId() { return counterpartyId; } public void setCounterpartyId(String v) { this.counterpartyId = v; }
        public Long getTransCount() { return transCount; } public void setTransCount(Long v) { this.transCount = v; }
        public Double getTotalAmount() { return totalAmount; } public void setTotalAmount(Double v) { this.totalAmount = v; }
        public Long getPayerCount() { return payerCount; } public void setPayerCount(Long v) { this.payerCount = v; }
    }
}
