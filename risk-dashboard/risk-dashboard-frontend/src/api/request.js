/**
 * Axios 请求封装
 * 统一拦截器、错误处理、超时配置
 */
import axios from 'axios'
import { Message } from 'element-ui'

const request = axios.create({
  baseURL: process.env.VUE_APP_API_BASE || '/api',
  timeout: 60000,
  headers: { 'Content-Type': 'application/json' }
})

// 请求拦截器
request.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers['Authorization'] = 'Bearer ' + token
    }
    return config
  },
  error => Promise.reject(error)
)

// 响应拦截器
request.interceptors.response.use(
  response => {
    const res = response.data
    if (res.code === 401) {
      const hadToken = !!localStorage.getItem('token')
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      if (hadToken) {
        Message.error('登录已过期，请重新登录')
      }
      window.location.hash = '#/login'
      return Promise.reject(new Error('未授权'))
    }
    if (res.code !== 200 && res.code !== undefined) {
      Message.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message))
    }
    return res
  },
  error => {
    // 401 未授权
    if (error.response && error.response.status === 401) {
      const hadToken = !!localStorage.getItem('token')
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      if (hadToken) {
        Message.error('登录已过期，请重新登录')
      }
      window.location.hash = '#/login'
      return Promise.reject(error)
    }

    // 有服务器响应 -> 根据状态码和返回内容报错
    if (error.response) {
      const status = error.response.status
      const data = error.response.data
      let msg = ''
      if (data) {
        if (typeof data === 'string') msg = data
        else if (data.message) msg = data.message
        else if (data.error) msg = data.error
      }
      if (msg) {
        Message.error('[' + status + '] ' + msg)
      } else if (status === 500) {
        Message.error('服务器内部错误 (500)，请检查后端日志')
      } else if (status === 404) {
        Message.error('接口不存在 (404): ' + (error.config?.url || ''))
      } else if (status === 403) {
        Message.error('权限不足 (403)')
      } else {
        Message.error('请求失败 (' + status + ')')
      }
      return Promise.reject(error)
    }

    // 请求超时
    if (error.code === 'ECONNABORTED' && error.message.includes('timeout')) {
      Message.error('请求超时，服务器响应过慢')
      return Promise.reject(error)
    }

    // 无法连接
    if (error.message === 'Network Error' || error.code === 'ERR_NETWORK') {
      Message.error('无法连接服务器，请检查服务是否启动')
      return Promise.reject(error)
    }

    // 兜底
    Message.error(error.message || '网络异常，请稍后重试')
    return Promise.reject(error)
  }
)

export default request
