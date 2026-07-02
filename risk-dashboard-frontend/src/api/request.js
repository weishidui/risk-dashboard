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
    // 可在此添加 Token 认证
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器
request.interceptors.response.use(
  response => {
    const res = response.data
    // 统一错误提示
    if (res.code !== 200 && res.code !== undefined) {
      Message.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message))
    }
    return res
  },
  error => {
    Message.error('网络异常，请稍后重试')
    return Promise.reject(error)
  }
)

export default request
