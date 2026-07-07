/**
 * Axios 请求封装
 * 统一拦截器、错误处理、超时配置
 */
import axios from 'axios'
import { Message } from 'element-ui'

const request = axios.create({
  baseURL: process.env.VUE_APP_API_BASE || '/api',
  timeout: 15000,
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
    if (error.response && error.response.status === 401) {
      const hadToken = !!localStorage.getItem('token')
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      if (hadToken) {
        Message.error('登录已过期，请重新登录')
      }
      window.location.hash = '#/login'
    } else {
      Message.error('网络异常，请稍后重试')
    }
    return Promise.reject(error)
  }
)

export default request
