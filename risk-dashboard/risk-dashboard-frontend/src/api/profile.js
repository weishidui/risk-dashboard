/**
 * 用户画像 API
 */
import request from './request'

export function getProfileList(params) {
  return request.get('/profile/list', { params })
}

export function getProfileDetail(userId) {
  return request.get(`/profile/${userId}`)
}

export function getProfileStats() {
  return request.get('/profile/stats')
}
