import Vue from 'vue'
import Vuex from 'vuex'
import { getDashboardData } from '@/api/metrics'
import { getRecentAlerts } from '@/api/alert'
import { login as loginApi, register as registerApi } from '@/api/auth'

Vue.use(Vuex)

export default new Vuex.Store({
  state: {
    token: localStorage.getItem('token') || '',
    user: JSON.parse(localStorage.getItem('user') || 'null'),
    dashboard: null,
    recentAlerts: [],
    liveTransactions: [],
    liveAlerts: [],
    loading: false
  },
  getters: {
    isLoggedIn: state => !!state.token,
    userRole: state => state.user ? state.user.role : null,
    username: state => state.user ? state.user.username : null
  },
  mutations: {
    SET_TOKEN(state, token) {
      state.token = token
      localStorage.setItem('token', token)
    },
    SET_USER(state, user) {
      state.user = user
      localStorage.setItem('user', JSON.stringify(user))
    },
    CLEAR_AUTH(state) {
      state.token = ''
      state.user = null
      localStorage.removeItem('token')
      localStorage.removeItem('user')
    },
    SET_DASHBOARD(state, data) { state.dashboard = data },
    SET_RECENT_ALERTS(state, alerts) { state.recentAlerts = alerts },
    ADD_LIVE_TRANSACTION(state, txn) {
      state.liveTransactions.unshift(txn)
      if (state.liveTransactions.length > 100) state.liveTransactions.pop()
    },
    ADD_LIVE_ALERT(state, alert) {
      state.liveAlerts.unshift(alert)
      if (state.liveAlerts.length > 50) state.liveAlerts.pop()
    },
    SET_LOADING(state, loading) { state.loading = loading }
  },
  actions: {
    async login({ commit }, { username, password }) {
      const res = await loginApi(username, password)
      if (res.code === 200) {
        commit('SET_TOKEN', res.data.token)
        commit('SET_USER', res.data.user)
      }
      return res
    },
    async register({ commit }, { username, password, role }) {
      const res = await registerApi(username, password, role)
      if (res.code === 200) {
        commit('SET_TOKEN', res.data.token)
        commit('SET_USER', res.data.user)
      }
      return res
    },
    logout({ commit }) {
      commit('CLEAR_AUTH')
    },
    async fetchDashboard({ commit }) {
      commit('SET_LOADING', true)
      try {
        const res = await getDashboardData()
        if (res.code === 200) commit('SET_DASHBOARD', res.data)
      } catch (e) { console.error('获取仪表盘数据失败:', e) }
      finally { commit('SET_LOADING', false) }
    },
    async fetchRecentAlerts({ commit }, limit = 20) {
      try {
        const res = await getRecentAlerts(limit)
        if (res.code === 200) commit('SET_RECENT_ALERTS', res.data)
      } catch (e) { console.error('获取告警列表失败:', e) }
    },
    handleWsMessage({ commit }, msg) {
      if (msg.topic === '/topic/transaction') {
        try { commit('ADD_LIVE_TRANSACTION', JSON.parse(msg.data)) } catch (e) {}
      } else if (msg.topic === '/topic/alert') {
        try { commit('ADD_LIVE_ALERT', JSON.parse(msg.data)) } catch (e) {}
      }
    }
  }
})
