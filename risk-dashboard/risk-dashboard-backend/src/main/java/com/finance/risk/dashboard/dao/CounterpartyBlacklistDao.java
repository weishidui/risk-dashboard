package com.finance.risk.dashboard.dao;

import com.finance.risk.dashboard.entity.CounterpartyBlacklist;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface CounterpartyBlacklistDao {

    @Select("SELECT * FROM counterparty_blacklist ORDER BY update_time DESC LIMIT #{offset}, #{limit}")
    List<CounterpartyBlacklist> findList(@Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM counterparty_blacklist")
    Long count();

    @Select("SELECT * FROM counterparty_blacklist WHERE counterparty_id = #{counterpartyId}")
    CounterpartyBlacklist findByCounterpartyId(@Param("counterpartyId") String counterpartyId);

    @Select("SELECT * FROM counterparty_blacklist WHERE risk_level = #{riskLevel} " +
            "ORDER BY update_time DESC LIMIT #{offset}, #{limit}")
    List<CounterpartyBlacklist> findByRiskLevel(@Param("riskLevel") String riskLevel,
                                                 @Param("offset") int offset,
                                                 @Param("limit") int limit);

    @Insert("INSERT INTO counterparty_blacklist(counterparty_id, counterparty_name, risk_level, " +
            "risk_type, source, total_received_24h, total_received_7d, unique_payers_24h, " +
            "registration_days, risk_tags) " +
            "VALUES(#{counterpartyId}, #{counterpartyName}, #{riskLevel}, " +
            "#{riskType}, #{source}, #{totalReceived24h}, #{totalReceived7d}, #{uniquePayers24h}, " +
            "#{registrationDays}, #{riskTags})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(CounterpartyBlacklist entity);

    @Update("UPDATE counterparty_blacklist SET risk_level = #{riskLevel}, risk_type = #{riskType}, " +
            "risk_tags = #{riskTags} WHERE counterparty_id = #{counterpartyId}")
    int update(@Param("counterpartyId") String counterpartyId,
               @Param("riskLevel") String riskLevel,
               @Param("riskType") String riskType,
               @Param("riskTags") String riskTags);

    @Select("<script>" +
            "SELECT risk_level, COUNT(*) as cnt FROM counterparty_blacklist " +
            "WHERE 1=1 " +
            "<if test='riskLevel != null'>AND risk_level = #{riskLevel}</if> " +
            "GROUP BY risk_level" +
            "</script>")
    List<RiskLevelCount> countByRiskLevel(@Param("riskLevel") String riskLevel);

    class RiskLevelCount {
        private String riskLevel;
        private Long cnt;
        public String getRiskLevel() { return riskLevel; }
        public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
        public Long getCnt() { return cnt; }
        public void setCnt(Long cnt) { this.cnt = cnt; }
    }
}
