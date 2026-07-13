/**
 * 设备风险分析 API
 */
import request from './request'

export function getDeviceStats() {
  return request.get('/device-risk/stats')
}

export function getDeviceList(page = 1, pageSize = 20) {
  return request.get('/device-risk/list', { params: { page, pageSize } })
}

export function getDeviceDetail(deviceId) {
  return request.get(`/device-risk/${deviceId}`)
}
