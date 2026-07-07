package com.finance.risk.dashboard.service;

import com.finance.risk.dashboard.dto.MetricsInputDTO;
import com.finance.risk.dashboard.entity.MetricsSnapshot;
import com.finance.risk.dashboard.vo.DashboardVO;

import java.util.List;

/**
 * 指标统计服务接口
 *
 * @author Risk Dashboard Team
 * @since 1.0.0
 */
public interface MetricsService {

    /**
     * 接收数据处理结果：指标快照
     *
     * @param metrics 指标数据
     * @return 是否成功
     */
    boolean receiveMetrics(MetricsInputDTO metrics);

    /**
     * 获取最新指标快照
     */
    MetricsSnapshot getLatestMetrics();

    /**
     * 获取仪表盘首页综合数据 (聚合所有数据)
     * 这是前端仪表盘的核心数据源
     *
     * @return 仪表盘综合数据
     */
    DashboardVO getDashboardData();

    /**
     * 获取指定时间范围的指标趋势数据
     *
     * @param hours 最近N小时
     * @return 指标列表
     */
    List<MetricsSnapshot> getMetricsTrend(int hours);
}
