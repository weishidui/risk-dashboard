package com.finance.risk.dashboard.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 仪表盘首页综合视图对象
 * 聚合实时指标 + 趋势 + 分布数据
 *
 * @author Risk Dashboard Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "仪表盘综合数据")
public class DashboardVO implements Serializable {

    private static final long serialVersionUID = 1L;

    // ==================== 核心指标卡片 ====================

    @ApiModelProperty(value = "当前秒级交易总量")
    private Long totalTransactions;

    @ApiModelProperty(value = "正常放行数")
    private Long passCount;

    @ApiModelProperty(value = "待核验数")
    private Long verifyCount;

    @ApiModelProperty(value = "拦截数")
    private Long blockCount;

    @ApiModelProperty(value = "当前在线用户数")
    private Long activeUsers;

    @ApiModelProperty(value = "平均风险评分")
    private Double avgRiskScore;

    @ApiModelProperty(value = "平均处理延迟(毫秒)")
    private Double avgLatency;

    @ApiModelProperty(value = "系统运行时长(秒)")
    private Long uptimeSeconds;

    // ==================== 趋势数据 ====================

    @ApiModelProperty(value = "近24小时交易量趋势")
    private List<TrendPointVO> transactionTrend;

    @ApiModelProperty(value = "近24小时告警趋势")
    private List<TrendPointVO> alertTrend;

    @ApiModelProperty(value = "近24小时拦截率趋势")
    private List<TrendPointVO> blockRateTrend;

    // ==================== 分布数据 ====================

    @ApiModelProperty(value = "风险等级分布 (饼图)")
    private List<DistributionVO> riskLevelDistribution;

    @ApiModelProperty(value = "风险类型分布 (饼图)")
    private List<DistributionVO> ruleTypeDistribution;

    @ApiModelProperty(value = "城市风险分布 (柱状图)")
    private List<DistributionVO> cityDistribution;

    // ==================== 地理告警数据 ====================

    @ApiModelProperty(value = "最近高危告警地理位置 (地图红点)")
    private List<GeoAlertVO> geoAlerts;

    // ==================== 最新告警列表 (Top 10) ====================
    @ApiModelProperty(value = "最新告警列表")
    private List<AlertVO> recentAlerts;
}
