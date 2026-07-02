/**
 * Vuex 状态管理
 * 集中管理实时数据，各组件共享
 */
import Vue from 'vue'
import Vuex from 'vuex'
import { getDashboardData } from '@/api/metrics'
import { getRecentAlerts } from '@/api/alert'

Vue.use(Vuex)

export default new Vuex.Store({
  state: {
    // 仪表盘数据
    dashboard: null,
    // 最新告警列表
    recentAlerts: [],
    // 实时交易 (WebSocket 推送)
    liveTransactions: [],
    // 实时告警 (WebSocket 推送)
    liveAlerts: [],
    // 数据加载状态
    loading: false
  },
  mutations: {
    SET_DASHBOARD(state, data) {
      state.dashboard = data
    },
    SET_RECENT_ALERTS(state, alerts) {
      state.recentAlerts = alerts
    },
    ADD_LIVE_TRANSACTION(state, txn) {
      state.liveTransactions.unshift(txn)
      if (state.liveTransactions.length > 100) {
        state.liveTransactions.pop()
      }
    },
    ADD_LIVE_ALERT(state, alert) {
      state.liveAlerts.unshift(alert)
      if (state.liveAlerts.length > 50) {
        state.liveAlerts.pop()
      }
    },
    SET_LOADING(state, loading) {
      state.loading = loading
    }
  },
  actions: {
    /**
     * 获取仪表盘数据
     */
    async fetchDashboard({ commit }) {
      commit('SET_LOADING', true)
      try {
        const res = await getDashboardData()
        if (res.code === 200) {
          commit('SET_DASHBOARD', res.data)
        }
      } catch (e) {
        console.error('获取仪表盘数据失败:', e)
      } finally {
        commit('SET_LOADING', false)
      }
    },

    /**
     * 获取最新告警
     */
    async fetchRecentAlerts({ commit }, limit = 20) {
      try {
        const res = await getRecentAlerts(limit)
        if (res.code === 200) {
          commit('SET_RECENT_ALERTS', res.data)
        }
      } catch (e) {
        console.error('获取告警列表失败:', e)
      }
    },

    /**
     * 处理 WebSocket 实时消息
     */
    handleWsMessage({ commit }, msg) {
      if (msg.topic === '/topic/transaction') {
        try {
          const txn = JSON.parse(msg.data)
          commit('ADD_LIVE_TRANSACTION', txn)
        } catch (e) { /* ignore */ }
      } else if (msg.topic === '/topic/alert') {
        try {
          const alert = JSON.parse(msg.data)
          commit('ADD_LIVE_ALERT', alert)
        } catch (e) { /* ignore */ }
      }
    }
  }
})
