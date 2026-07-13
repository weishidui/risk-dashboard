package com.finance.risk.dashboard.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/** Redis 时间桶聚合出的首页实时指标。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RealtimeMetricsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean available;
    private long windowSeconds;
    private long windowEndTime;
    private long totalTransactions;
    private long passCount;
    private long verifyCount;
    private long blockCount;
    private long activeUsers;
    private double avgRiskScore;
    private double riskIndex;
    private long lowRiskCount;
    private long mediumRiskCount;
    private long highRiskCount;
    private long criticalRiskCount;
    private long envRiskCount;
    private long amountRiskCount;
    private long teleportRiskCount;
    private long geoRiskCount;
    private List<DistributionVO> riskLevelDistribution;
    private List<DistributionVO> cityDistribution;
    private List<DistributionVO> ruleTypeDistribution;
}
