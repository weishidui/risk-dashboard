import request from './request'

/** 分页查询告警列表 */
export function getAlertList(params) {
  return request.get('/alert/list', { params })
}

/** 获取最新告警 */
export function getRecentAlerts(limit = 20) {
  return request.get('/alert/recent', { params: { limit } })
}

/** 更新告警处理状态 */
export function updateAlertStatus(alertId, status, handler = 'admin', remark = '') {
  return request.put(`/alert/${alertId}/status`, null, { params: { status, handler, remark } })
}

/** 风险等级分布统计 */
export function getRiskLevelStat() {
  return request.get('/alert/stat/risk-level')
}

/** 风险类型分布统计 */
export function getRuleTypeStat() {
  return request.get('/alert/stat/rule-type')
}

/** 城市风险分布 */
export function getCityRiskStat(limit = 20) {
  return request.get('/alert/stat/city-risk', { params: { limit } })
}
