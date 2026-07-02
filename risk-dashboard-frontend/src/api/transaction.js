/**
 * 交易流水 API
 */
import request from './request'

/** 获取最新交易流水 */
export function getRecentTransactions(limit = 50) {
  return request.get('/transaction/recent', { params: { limit } })
}

/** 城市交易量分布 */
export function getCityTransactionStat(limit = 10) {
  return request.get('/transaction/stat/city', { params: { limit } })
}
