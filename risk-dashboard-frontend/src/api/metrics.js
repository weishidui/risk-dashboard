/**
 * 仪表盘/指标 API
 */
import request from './request'

/** 获取仪表盘首页综合数据 */
export function getDashboardData() {
  return request.get('/dashboard/overview')
}

/** 获取最新指标快照 */
export function getLatestMetrics() {
  return request.get('/metrics/latest')
}

/** 获取趋势数据 */
export function getMetricsTrend(hours = 24) {
  return request.get('/metrics/trend', { params: { hours } })
}
