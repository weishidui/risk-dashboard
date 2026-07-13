package com.finance.risk.dashboard.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class OfflineAdsDao {
    @Resource
    private JdbcTemplate jdbcTemplate;

    public Map<String, Object> findLatestOverview() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT * FROM ads_offline_overview_metrics ORDER BY dt DESC LIMIT 1");
            return rows.isEmpty() ? Collections.<String, Object>emptyMap() : new LinkedHashMap<String, Object>(rows.get(0));
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }

    public List<Map<String, Object>> findScoreDistribution(String dt) {
        if (dt == null || dt.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return jdbcTemplate.queryForList(
                    "SELECT score_bucket AS name, risk_count AS value FROM ads_offline_score_distribution WHERE dt=? ORDER BY sort_order",
                    dt);
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    public List<Map<String, Object>> findProvinceRanking(String dt, int limit) {
        return list("SELECT region_name AS name,risk_count,high_risk_count,extreme_risk_count,risk_amount,avg_risk_score "
                + "FROM ads_province_risk_rank WHERE dt=? ORDER BY risk_count DESC,risk_amount DESC LIMIT ?", dt, limit);
    }

    public List<Map<String, Object>> findCityRanking(String dt, int limit) {
        return list("SELECT region_name AS name,risk_count,high_risk_count,extreme_risk_count,risk_amount,avg_risk_score "
                + "FROM ads_city_risk_rank WHERE dt=? ORDER BY risk_count DESC,risk_amount DESC LIMIT ?", dt, limit);
    }

    public List<Map<String, Object>> findRuleRanking(String dt, int limit) {
        return list("SELECT rule_code,rule_name,rule_category,risk_count,high_risk_count,extreme_risk_count,risk_amount,avg_risk_score "
                + "FROM ads_rule_risk_rank WHERE dt=? ORDER BY risk_count DESC,high_risk_count DESC LIMIT ?", dt, limit);
    }

    public List<Map<String, Object>> findTrend(String dt) {
        return list("SELECT stat_hour,risk_count,high_risk_count,extreme_risk_count,risk_amount,avg_risk_score "
                + "FROM ads_risk_time_trend WHERE dt=? ORDER BY stat_hour", dt);
    }

    public List<Map<String, Object>> findBehaviorDistribution(String dt) {
        return list("SELECT behavior_type,behavior_name,risk_count,high_risk_count,extreme_risk_count,risk_amount,avg_risk_score "
                + "FROM ads_risk_behavior_distribution WHERE dt=? ORDER BY behavior_type,risk_count DESC", dt);
    }

    public List<Map<String, Object>> findFeatureDistributions(String dt) {
        return list("SELECT feature_key,feature_value,total_count,risk_count,high_risk_count,extreme_risk_count,"
                + "risk_rate,high_risk_rate,risk_amount,avg_risk_score FROM ads_risk_feature_distribution "
                + "WHERE dt=? ORDER BY feature_key,risk_count DESC,feature_value", dt);
    }

    public List<Map<String, Object>> findEntityRanking(String table, String dt, int limit) {
        if (!"ads_high_risk_user_rank".equals(table) && !"ads_device_risk_rank".equals(table)
                && !"ads_counterparty_risk_rank".equals(table)) {
            return Collections.emptyList();
        }
        return list("SELECT entity_id AS name,risk_count,high_risk_count,extreme_risk_count,risk_amount,avg_risk_score FROM "
                + table + " WHERE dt=? ORDER BY risk_count DESC,high_risk_count DESC LIMIT ?", dt, limit);
    }

    public List<Map<String, Object>> findHighRiskTransactions(String dt, int limit) {
        return list("SELECT trans_id,user_id,province,city,risk_score,risk_level,hit_rules,amount,channel,trans_type,event_time "
                + "FROM ads_high_risk_transaction WHERE dt=? ORDER BY risk_score DESC,event_time DESC LIMIT ?", dt, limit);
    }

    public List<Map<String, Object>> findCrossRegionFlows(String dt, int limit) {
        return list("SELECT from_province,from_city,to_province,to_city,risk_count,high_risk_count,extreme_risk_count,risk_amount,avg_risk_score "
                + "FROM ads_cross_region_risk_flow WHERE dt=? ORDER BY risk_count DESC,risk_amount DESC LIMIT ?", dt, limit);
    }

    private List<Map<String, Object>> list(String sql, String dt, int limit) {
        if (dt == null || dt.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return jdbcTemplate.queryForList(sql, dt, limit);
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> list(String sql, String dt) {
        if (dt == null || dt.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return jdbcTemplate.queryForList(sql, dt);
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }
}
