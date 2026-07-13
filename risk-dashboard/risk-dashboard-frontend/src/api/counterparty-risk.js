/**
 * 收款方风险分析 API
 */
import request from './request'

export function getCptyRiskStats() {
  return request.get('/counterparty-risk/stats')
}

export function getCptyRiskList(page = 1, pageSize = 20) {
  return request.get('/counterparty-risk/list', { params: { page, pageSize } })
}
