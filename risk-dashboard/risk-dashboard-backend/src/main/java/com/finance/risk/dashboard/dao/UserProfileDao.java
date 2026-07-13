        package com.finance.risk.dashboard.dao;

import com.finance.risk.dashboard.entity.UserProfile;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface UserProfileDao {

    @Insert("INSERT INTO user_profile(user_id, avg_amt_30d, common_cities, common_devs, " +
            "common_pay_channels, common_trans_types, common_counterparties, " +
            "last_trans_ts, last_city, last_ip, last_login_time, " +
            "registration_time, total_balance, single_limit, daily_limit, monthly_limit, " +
            "account_status, login_count_24h, trans_count_24h, trans_amount_24h, trans_count_7d, " +
            "cancel_retry_count, risk_tags, risk_score) " +
            "VALUES(#{userId}, #{avgAmt30d}, #{commonCities}, #{commonDevs}, " +
            "#{commonPayChannels}, #{commonTransTypes}, #{commonCounterparties}, " +
            "#{lastTransTs}, #{lastCity}, #{lastIp}, #{lastLoginTime}, " +
            "#{registrationTime}, #{totalBalance}, #{singleLimit}, #{dailyLimit}, #{monthlyLimit}, " +
            "#{accountStatus}, #{loginCount24h}, #{transCount24h}, #{transAmount24h}, #{transCount7d}, " +
            "#{cancelRetryCount}, #{riskTags}, #{riskScore})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserProfile profile);

    @Update("UPDATE user_profile SET avg_amt_30d = #{avgAmt30d}, " +
            "common_cities = #{commonCities}, common_devs = #{commonDevs}, " +
            "common_pay_channels = #{commonPayChannels}, common_trans_types = #{commonTransTypes}, " +
            "common_counterparties = #{commonCounterparties}, " +
            "last_trans_ts = #{lastTransTs}, last_city = #{lastCity}, " +
            "last_ip = #{lastIp}, last_login_time = #{lastLoginTime}, " +
            "registration_time = #{registrationTime}, total_balance = #{totalBalance}, " +
            "single_limit = #{singleLimit}, daily_limit = #{dailyLimit}, monthly_limit = #{monthlyLimit}, " +
            "account_status = #{accountStatus}, login_count_24h = #{loginCount24h}, " +
            "trans_count_24h = #{transCount24h}, trans_amount_24h = #{transAmount24h}, " +
            "trans_count_7d = #{transCount7d}, cancel_retry_count = #{cancelRetryCount}, " +
            "risk_tags = #{riskTags}, risk_score = #{riskScore} " +
            "WHERE user_id = #{userId}")
    int updateByUserId(UserProfile profile);

    @Select("SELECT * FROM user_profile WHERE user_id = #{userId}")
    UserProfile findByUserId(@Param("userId") String userId);

    @Select("SELECT * FROM user_profile ORDER BY update_time DESC LIMIT #{offset}, #{limit}")
    List<UserProfile> findList(@Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM user_profile")
    Long count();

    @Select("<script>" +
            "SELECT * FROM user_profile WHERE 1=1 " +
            "<if test='accountStatus != null'>AND account_status = #{accountStatus}</if> " +
            "<if test='riskLevel != null and riskLevel == \"high\"'>AND risk_score &gt;= 80</if> " +
            "<if test='riskLevel != null and riskLevel == \"medium\"'>AND risk_score &gt;= 40 AND risk_score &lt; 80</if> " +
            "<if test='riskLevel != null and riskLevel == \"low\"'>AND risk_score &lt; 40</if> " +
            "ORDER BY risk_score DESC LIMIT #{offset}, #{limit}" +
            "</script>")
    List<UserProfile> findListByFilter(@Param("accountStatus") String accountStatus,
                                       @Param("riskLevel") String riskLevel,
                                       @Param("offset") int offset,
                                       @Param("limit") int limit);

    @Select("<script>" +
            "SELECT COUNT(*) FROM user_profile WHERE 1=1 " +
            "<if test='accountStatus != null'>AND account_status = #{accountStatus}</if> " +
            "<if test='riskLevel != null and riskLevel == \"high\"'>AND risk_score &gt;= 80</if> " +
            "<if test='riskLevel != null and riskLevel == \"medium\"'>AND risk_score &gt;= 40 AND risk_score &lt; 80</if> " +
            "<if test='riskLevel != null and riskLevel == \"low\"'>AND risk_score &lt; 40</if> " +
            "</script>")
    Long countByFilter(@Param("accountStatus") String accountStatus,
                       @Param("riskLevel") String riskLevel);

    @Select("SELECT CASE " +
            "WHEN risk_score <= 0 THEN '≤0' WHEN risk_score <= 20 THEN '1-20' " +
            "WHEN risk_score <= 40 THEN '21-40' WHEN risk_score <= 60 THEN '41-60' " +
            "WHEN risk_score <= 80 THEN '61-80' ELSE '81-100' END as rng, COUNT(*) as cnt " +
            "FROM user_profile GROUP BY rng ORDER BY MIN(risk_score)")
    List<ScoreRange> countByScoreRange();

    class ScoreRange {
        private String rng; private Long cnt;
        public String getRng() { return rng; } public void setRng(String v) { this.rng = v; }
        public Long getCnt() { return cnt; } public void setCnt(Long v) { this.cnt = v; }
    }

    @Select("SELECT account_status, COUNT(*) as cnt FROM user_profile GROUP BY account_status")
    List<StatusCount> countByStatus();

    class StatusCount {
        private String accountStatus; private Long cnt;
        public String getAccountStatus() { return accountStatus; }
        public void setAccountStatus(String v) { this.accountStatus = v; }
        public Long getCnt() { return cnt; }
        public void setCnt(Long v) { this.cnt = v; }
    }

    @Delete("DELETE FROM user_profile WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") String userId);
}
