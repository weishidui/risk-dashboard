import request from './request'

export function getChainList(page = 1, pageSize = 20) { return request.get('/chain/list', { params: { page, pageSize } }) }
export function getChainDetail(chainId) { return request.get(`/chain/${chainId}`) }
export function getLoopChains(limit = 20) { return request.get('/chain/loops', { params: { limit } }) }
export function getChainStats() { return request.get('/chain/stats') }
