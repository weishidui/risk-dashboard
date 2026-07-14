import request from './request'

export function getBlacklist(params) { return request.get('/blacklist/list', { params }) }
export function getByRiskLevel(riskLevel, page = 1, pageSize = 20) { return request.get('/blacklist/by-level', { params: { riskLevel, page, pageSize } }) }
export function getRiskLevelStats() { return request.get('/blacklist/stat/risk-level') }
export function getBlacklistDetail(counterpartyId) { return request.get(`/blacklist/${counterpartyId}`) }
