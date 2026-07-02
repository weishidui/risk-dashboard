package com.finance.risk.dashboard.dao;

import com.finance.risk.dashboard.entity.Transaction;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 交易流水数据访问层 — 对应 transaction_history 表
 */
@Mapper
public interface TransactionDao {

    @Insert("INSERT INTO transaction_history(trans_id, user_id, amount, trans_timestamp, city, " +
            "geo_location, device_id, network_type, dev_score) " +
            "VALUES(#{transId}, #{userId}, #{amount}, #{transTimestamp}, #{city}, " +
            "#{geoLocation}, #{deviceId}, #{networkType}, #{devScore})")
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

    class CityCount {
        private String city;
        private Long cnt;
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        public Long getCnt() { return cnt; }
        public void setCnt(Long cnt) { this.cnt = cnt; }
    }
}
