/**
 * 离线分析 API
 */
import request from './request'

/** 离线总览 */
export function getOfflineOverview() {
  return request.get('/offline/overview')
}

/** 风险评分分布 */
export function getRiskDist() {
  return request.get('/offline/risk-distribution')
}

/** 离线分析大屏数据，全部来自 ADS 快照表 */
export function getOfflineDashboardData() {
  return request.get('/offline/dashboard-data')
}

/** 离线任务状态 */
export function getTaskStatus(dt) {
  return request.get('/offline/task-status', { params: dt ? { dt } : {} })
}

/** 触发近30天离线分析 */
export function startAnalysis() {
  return request.post('/offline/analyze/recent-30-days')
}

/** 查询分析任务状态 */
export function getAnalysisStatus(dt) {
  return request.get('/offline/analyze/status', { params: { dt } })
}

export function getCurrentAnalysisStatus() {
  return request.get('/offline/analyze/current')
}

export function pauseAnalysis() {
  return request.post('/offline/analyze/pause')
}

export function resumeAnalysis() {
  return request.post('/offline/analyze/resume')
}

export function cancelAnalysis() {
  return request.post('/offline/analyze/cancel')
}
