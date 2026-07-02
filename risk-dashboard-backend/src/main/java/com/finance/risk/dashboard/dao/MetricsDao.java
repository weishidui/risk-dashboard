package com.finance.risk.dashboard.dao;

import com.finance.risk.dashboard.entity.MetricsSnapshot;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 指标快照数据访问层
 *
 * @author Risk Dashboard Team
 * @since 1.0.0
 */
@Mapper
public interface MetricsDao {

    /**
     * 插入指标快照 (来自 Spark Streaming 聚合计算)
     */
    @Insert("INSERT INTO t_metrics(snapshot_time, total_transactions, pass_count, " +
            "verify_count, block_count, high_risk_count, medium_risk_count, " +
            "low_risk_count, avg_risk_score, env_risk_count, amount_risk_count, " +
            "teleport_risk_count, geo_risk_count, avg_latency) " +
            "VALUES(#{snapshotTime}, #{totalTransactions}, #{passCount}, #{verifyCount}, " +
            "#{blockCount}, #{highRiskCount}, #{mediumRiskCount}, #{lowRiskCount}, " +
            "#{avgRiskScore}, #{envRiskCount}, #{amountRiskCount}, #{teleportRiskCount}, " +
            "#{geoRiskCount}, #{avgLatency})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(MetricsSnapshot metrics);

    /**
     * 获取最新一条指标快照
     */
    @Select("SELECT * FROM t_metrics ORDER BY snapshot_time DESC LIMIT 1")
    MetricsSnapshot findLatest();

    /**
     * 查询指定时间范围内的指标快照
     */
    @Select("SELECT * FROM t_metrics WHERE snapshot_time BETWEEN #{startTime} AND #{endTime} " +
            "ORDER BY snapshot_time ASC")
    List<MetricsSnapshot> findByTimeRange(@Param("startTime") Long startTime,
                                           @Param("endTime") Long endTime);

    /**
     * 查询最近N条快照
     */
    @Select("SELECT * FROM t_metrics ORDER BY snapshot_time DESC LIMIT #{limit}")
    List<MetricsSnapshot> findRecent(@Param("limit") int limit);
}
