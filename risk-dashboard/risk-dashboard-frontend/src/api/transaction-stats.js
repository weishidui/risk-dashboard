/**
 * 交易行为统计 API
 */
import request from './request'

export function getDailyTrend(days = 30) {
  return request.get('/transaction-stats/daily-trend', { params: { days } })
}

export function getPayChannelDist() {
  return request.get('/transaction-stats/pay-channel')
}

export function getTransTypeDist() {
  return request.get('/transaction-stats/trans-type')
}

export function getAmountRangeDist() {
  return request.get('/transaction-stats/amount-range')
}

export function getCityRank(limit = 15) {
  return request.get('/transaction-stats/city', { params: { limit } })
}

export function getTransSummary() {
  return request.get('/transaction-stats/summary')
}
