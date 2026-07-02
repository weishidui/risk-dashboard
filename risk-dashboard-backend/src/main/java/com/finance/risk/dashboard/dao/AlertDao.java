package com.finance.risk.dashboard.dao;

import com.finance.risk.dashboard.entity.AlertResult;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 风控告警数据访问层 — 对应 risk_alert 表
 */
@Mapper
public interface AlertDao {

    @Insert("INSERT INTO risk_alert(alert_id, trans_id, user_id, hit_rules, amount, " +
            "final_score, risk_level, city, alert_loc, raw_json) " +
            "VALUES(#{alertId}, #{transId}, #{userId}, #{hitRules}, #{amount}, " +
            "#{finalScore}, #{riskLevel}, #{city}, #{alertLoc}, #{rawJson})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AlertResult alert);

    @Insert("<script>" +
            "INSERT INTO risk_alert(alert_id, trans_id, user_id, hit_rules, amount, " +
            "final_score, risk_level, city, alert_loc, raw_json) VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.alertId}, #{item.transId}, #{item.userId}, #{item.hitRules}, #{item.amount}, " +
            "#{item.finalScore}, #{item.riskLevel}, #{item.city}, #{item.alertLoc}, #{item.rawJson})" +
            "</foreach>" +
            "</script>")
    int batchInsert(@Param("list") List<AlertResult> alertList);

    @Select("SELECT * FROM risk_alert WHERE alert_id = #{alertId}")
    AlertResult findByAlertId(@Param("alertId") String alertId);

    @Select("<script>" +
            "SELECT * FROM risk_alert WHERE 1=1 " +
            "<if test='riskLevel != null and riskLevel != \"\"'>AND risk_level = #{riskLevel}</if> " +
            "<if test='startTime != null'>AND create_time &gt;= #{startTime}</if> " +
            "<if test='endTime != null'>AND create_time &lt;= #{endTime}</if> " +
            "ORDER BY create_time DESC LIMIT #{offset}, #{limit}" +
            "</script>")
    List<AlertResult> findList(@Param("riskLevel") String riskLevel,
                               @Param("startTime") String startTime,
                               @Param("endTime") String endTime,
                               @Param("offset") int offset,
                               @Param("limit") int limit);

    @Select("<script>" +
            "SELECT COUNT(*) FROM risk_alert WHERE 1=1 " +
            "<if test='riskLevel != null and riskLevel != \"\"'>AND risk_level = #{riskLevel}</if> " +
            "<if test='startTime != null'>AND create_time &gt;= #{startTime}</if> " +
            "<if test='endTime != null'>AND create_time &lt;= #{endTime}</if> " +
            "</script>")
    Long count(@Param("riskLevel") String riskLevel,
               @Param("startTime") String startTime,
               @Param("endTime") String endTime);

    @Select("SELECT risk_level, COUNT(*) as cnt FROM risk_alert " +
            "WHERE create_time > #{sinceTime} GROUP BY risk_level")
    List<RiskLevelCount> countByRiskLevel(@Param("sinceTime") String sinceTime);

    @Select("SELECT hit_rules, COUNT(*) as cnt FROM risk_alert " +
            "WHERE create_time > #{sinceTime} GROUP BY hit_rules ORDER BY cnt DESC")
    List<RuleCount> countByHitRule(@Param("sinceTime") String sinceTime);

    @Select("SELECT alert_loc, COUNT(*) as cnt FROM risk_alert " +
            "WHERE create_time > #{sinceTime} AND risk_level = '高危' AND alert_loc IS NOT NULL " +
            "GROUP BY alert_loc ORDER BY cnt DESC LIMIT #{limit}")
    List<CityAlertCount> countHighRiskByCity(@Param("sinceTime") String sinceTime,
                                              @Param("limit") int limit);

    /** 批量刷新时间戳（将旧 create_time 更新为当前时间，用于开发环境数据刷新） */
    @Update("UPDATE risk_alert SET create_time = NOW() WHERE id > 0")
    int refreshTimestamps();

    // ==================== 内部统计类 ====================
    class RiskLevelCount {
        private String riskLevel;
        private Long cnt;
        public String getRiskLevel() { return riskLevel; }
        public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
        public Long getCnt() { return cnt; }
        public void setCnt(Long cnt) { this.cnt = cnt; }
    }

    class RuleCount {
        private String hitRules;
        private Long cnt;
        public String getHitRules() { return hitRules; }
        public void setHitRules(String hitRules) { this.hitRules = hitRules; }
        public Long getCnt() { return cnt; }
        public void setCnt(Long cnt) { this.cnt = cnt; }
    }

    class CityAlertCount {
        private String alertLoc;
        private Long cnt;
        public String getAlertLoc() { return alertLoc; }
        public void setAlertLoc(String alertLoc) { this.alertLoc = alertLoc; }
        public Long getCnt() { return cnt; }
        public void setCnt(Long cnt) { this.cnt = cnt; }
    }
}
