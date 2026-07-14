<template>
  <div id="app">
    <template v-if="!isLoggedIn">
      <router-view />
    </template>

    <div v-else class="app-shell">
      <!-- ===== 顶栏：模式切换 + 用户区 ===== -->
      <header class="topbar">
        <div class="topbar-left">
          <!-- 模式切换 -->
          <div class="mode-tabs">
            <span v-if="canAccess('/dashboard')" class="mode-tab" :class="{ active: mode === 'realtime' }" @click="switchMode('realtime')">
              <span class="mode-dot" :class="{ live: mode === 'realtime' }"></span>实时监控
            </span>
            <span v-if="canAccess('/offline-overview')" class="mode-tab" :class="{ active: mode === 'offline' }" @click="switchMode('offline')">
              <i class="el-icon-s-data"></i> 离线分析
            </span>
          </div>
        </div>
        <div class="topbar-right">
          <span v-if="canAccess('/dashboard')" class="health-dots" @click="showHealthDetail = true" style="cursor:pointer;" title="点击查看链路详情">
            <span :class="['hd', health.server ? 'ok' : 'fail']">S</span>
            <span :class="['hd', health.mysql ? 'ok' : 'fail']">M</span>
            <span :class="['hd', health.redis ? 'ok' : 'fail']">R</span>
          </span>
          <span class="ws-dot" v-if="mode === 'realtime'" :class="wsStatus === '已连接' ? 'online' : 'offline'" :title="wsStatus"></span>
          <span class="topbar-time">{{ nowTime }}</span>
          <el-dropdown trigger="click" @command="handleCommand">
            <span class="user-btn">
              <i class="el-icon-user-solid"></i>
              <span>{{ username }}</span>
              <i class="el-icon-arrow-down"></i>
            </span>
            <el-dropdown-menu slot="dropdown">
              <el-dropdown-item disabled>
                <div class="dd-user-info"><div class="dd-name">{{ username }}</div><div class="dd-role">{{ roleLabel }}</div></div>
              </el-dropdown-item>
              <li class="dd-divider"></li>
              <el-dropdown-item command="password"><i class="el-icon-lock"></i> 修改密码</el-dropdown-item>
              <el-dropdown-item v-if="canAccess('/users')" command="users"><i class="el-icon-user"></i> 账号管理</el-dropdown-item>
              <el-dropdown-item v-if="canAccess('/config')" command="config"><i class="el-icon-setting"></i> 评分规则</el-dropdown-item>
              <li class="dd-divider"></li>
              <el-dropdown-item disabled class="dd-section-label"><span>主题设置</span></el-dropdown-item>
              <el-dropdown-item command=""><ThemeSwitcher /></el-dropdown-item>
              <li class="dd-divider"></li>
              <el-dropdown-item command="logout"><i class="el-icon-switch-button"></i> 退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </el-dropdown>
        </div>
      </header>

      <!-- ===== 主内容区 ===== -->
      <main class="main-content">
        <router-view />
      </main>

      <!-- 链路状态详情弹窗 -->
      <el-dialog title="🔗 链路状态详情" :visible.sync="showHealthDetail" width="380px" append-to-body>
        <div class="health-detail">
          <div class="hd-row"><span class="hd-dot" :class="health.server ? 'ok' : 'fail'"></span><span class="hd-name">后端服务 (SpringBoot)</span><span :class="['hd-status', health.server ? 'ok' : 'fail']">{{ health.server ? '正常' : '异常' }}</span></div>
          <div class="hd-row"><span class="hd-dot" :class="health.mysql ? 'ok' : 'fail'"></span><span class="hd-name">MySQL 数据库</span><span :class="['hd-status', health.mysql ? 'ok' : 'fail']">{{ health.mysql ? '正常' : '异常' }}</span></div>
          <div class="hd-row"><span class="hd-dot" :class="health.redis ? 'ok' : 'fail'"></span><span class="hd-name">Redis 缓存</span><span :class="['hd-status', health.redis ? 'ok' : 'fail']">{{ health.redis ? '正常' : '异常' }}</span></div>
          <el-divider></el-divider>
          <div class="hd-note">数据来源: GET /api/dashboard/health<br>每30秒自动刷新，点击 S M R 三个圆点手动查看</div>
        </div>
      </el-dialog>

      <!-- 修改密码弹窗 -->
      <el-dialog title="修改密码" :visible.sync="pwdDialogVisible" width="360px" append-to-body>
        <el-form label-width="80px" size="small">
          <el-form-item label="新密码"><el-input v-model="newPassword" type="password" show-password /></el-form-item>
        </el-form>
        <span slot="footer">
          <el-button size="small" @click="pwdDialogVisible = false">取消</el-button>
          <el-button size="small" type="primary" @click="changePassword">确认</el-button>
        </span>
      </el-dialog>
    </div>
  </div>
</template>

<script>
import ThemeSwitcher from '@/components/ThemeSwitcher.vue'
import { ROLE_LABELS, ROLE_ROUTES } from '@/utils/constants'

const REALTIME_ROUTES = ['/dashboard', '/transaction', '/alerts', '/risk-map']
const OFFLINE_ROUTES  = ['/offline-overview', '/profiles', '/high-risk-users', '/device-risk', '/counterparty-risk', '/transaction-stats', '/blacklist', '/chain']

export default {
  name: 'App',
  components: { ThemeSwitcher },
  data() {
    return {
      mode: 'realtime',
      wsStatus: '未连接', ws: null,
      health: { server: false, mysql: false, redis: false }, healthTimer: null, showHealthDetail: false,
      pwdDialogVisible: false, newPassword: '',
      nowTime: '', timeTimer: null
    }
  },
  computed: {
    isLoggedIn() { return this.$store.getters.isLoggedIn },
    username()   { return this.$store.getters.username },
    roleLabel()  { return ROLE_LABELS[this.$store.getters.userRole] || '' },
    realtimeNav() { return [
      { to: '/dashboard', label: '仪表盘' },
      { to: '/transaction', label: '交易流水' },
      { to: '/alerts', label: '告警管理' },
      { to: '/risk-map', label: '风险地图' },
    ]},
    offlineNav() { return [
      { to: '/offline-overview', label: '总览' },
      { to: '/profiles', label: '用户画像' },
      { to: '/high-risk-users', label: '高风险用户' },
      { to: '/device-risk', label: '设备风险' },
      { to: '/counterparty-risk', label: '收款方风险' },
      { to: '/transaction-stats', label: '交易统计' },
      { to: '/blacklist', label: '黑名单' },
      { to: '/chain', label: '资金链路' },
    ]}
  },
  watch: {
    isLoggedIn(val) {
      if (val) this.connectWebSocket()
      else if (this.ws) { this.ws.close(); this.ws = null }
    },
    '$route.path'(path) {
      if (REALTIME_ROUTES.includes(path)) this.mode = 'realtime'
      else if (OFFLINE_ROUTES.includes(path)) this.mode = 'offline'
    }
  },
  mounted() {
    // 初始化模式
    if (OFFLINE_ROUTES.includes(this.$route.path)) this.mode = 'offline'
    if (this.isLoggedIn) this.connectWebSocket()
    this.nowTime = new Date().toLocaleTimeString('zh-CN')
    this.timeTimer = setInterval(() => { this.nowTime = new Date().toLocaleTimeString('zh-CN') }, 1000)
    if (this.canAccess('/dashboard')) {
      this.fetchHealth()
      this.healthTimer = setInterval(() => this.fetchHealth(), 30000)
    }
  },
  beforeDestroy() {
    if (this.ws) this.ws.close()
    clearInterval(this.timeTimer); clearInterval(this.healthTimer)
  },
  methods: {
    canAccess(path) {
      const allowed = ROLE_ROUTES[this.$store.getters.userRole] || []
      return allowed.includes(path)
    },
    switchMode(m) {
      if (m === 'realtime' && !this.canAccess('/dashboard')) return
      if (m === 'offline' && !this.canAccess('/offline-overview')) return
      this.mode = m
      if (m === 'realtime') this.$router.push('/dashboard')
      else this.$router.push('/offline-overview')
    },
    handleCommand(cmd) {
      if (cmd === 'logout') {
        this.$store.dispatch('logout')
        if (this.ws) { this.ws.close(); this.ws = null }
        this.wsStatus = '未连接'
        this.$router.push('/login')
      } else if (cmd === 'users') {
        this.$router.push('/users')
      } else if (cmd === 'config') {
        this.$router.push('/config')
      } else if (cmd === 'password') {
        this.pwdDialogVisible = true; this.newPassword = ''
      }
    },
    async fetchHealth() {
      try {
        const r = await fetch('/api/dashboard/health', { headers: { 'Authorization': 'Bearer ' + localStorage.getItem('token') } })
        if (r.ok) {
          const d = await r.json()
          if (d.code === 200) this.health = { server: d.data.server === 'ok', mysql: d.data.mysql === 'ok', redis: d.data.redis === 'ok' }
        }
      } catch (e) {}
    },
    changePassword() {
      if (!this.newPassword || this.newPassword.length < 3) { this.$message.warning('密码至少3位'); return }
      const user = JSON.parse(localStorage.getItem('user') || '{}')
      fetch('/api/admin/users/' + user.username + '/reset-password', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + localStorage.getItem('token') },
        body: JSON.stringify({ password: this.newPassword })
      }).then(r => r.json()).then(res => {
        if (res.code === 200) { this.$message.success('密码已修改'); this.pwdDialogVisible = false }
        else this.$message.error(res.message || '修改失败')
      }).catch(() => this.$message.error('网络错误'))
    },
    connectWebSocket() {
      const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
      const wsHost = process.env.NODE_ENV === 'development' ? 'localhost:8080' : window.location.host
      const wsUrl = `${protocol}//${wsHost}/ws/risk`
      this.ws = new WebSocket(wsUrl)
      this.ws.onopen = () => { this.wsStatus = '已连接' }
      this.ws.onmessage = (event) => {
        try { const msg = JSON.parse(event.data); this.$store.dispatch('handleWsMessage', msg) } catch (e) {}
      }
      this.ws.onclose = () => { this.wsStatus = '断开'; if (this.isLoggedIn) setTimeout(() => this.connectWebSocket(), 5000) }
      this.ws.onerror = () => { this.wsStatus = '异常' }
    }
  }
}
</script>

<style>
*, *::before, *::after { margin: 0; padding: 0; box-sizing: border-box; }
html, body, #app { height: 100%; overflow: hidden; }
body { font-family: var(--font-body); font-size: var(--text-base); background: var(--color-bg-deep); color: var(--color-text-primary); }

/* ===== App Shell ===== */
.app-shell { display: flex; flex-direction: column; height: 100%; }

/* ===== Top Bar ===== */
.topbar { display: flex; justify-content: space-between; align-items: center; height: 48px; padding: 0 var(--space-4); background: var(--color-bg-elevated); border-bottom: 1px solid var(--color-border); flex-shrink: 0; }
.topbar-left { display: flex; align-items: center; gap: var(--space-3); }
.topbar-right { display: flex; align-items: center; gap: var(--space-3); }
.topbar-time { font-size: 11px; color: var(--color-text-muted); font-family: var(--font-mono); }

/* ===== Mode Tabs ===== */
.mode-tabs { display: flex; gap: 2px; }
.mode-tab {
  display: flex; align-items: center; gap: 6px;
  padding: 6px 16px; border-radius: var(--radius-sm); cursor: pointer;
  font-size: 13px; font-weight: 500; color: var(--color-text-muted);
  transition: all 0.2s;
}
.mode-tab:hover { color: var(--color-text-primary); background: var(--color-bg-hover); }
.mode-tab.active { color: var(--color-primary); background: var(--color-primary-bg, rgba(59,130,246,0.1)); }
.mode-dot { width: 6px; height: 6px; border-radius: 50%; background: var(--color-text-muted); }
.mode-dot.live { background: #DC2626; animation: live-pulse 1.5s ease-in-out infinite; }
@keyframes live-pulse {
  0%, 100% { opacity: 1; box-shadow: 0 0 2px #DC2626; }
  50% { opacity: 0.4; box-shadow: 0 0 8px #DC2626; }
}

/* ===== Sub Navigation ===== */
.subnav {
  display: flex; align-items: center; gap: 2px;
  height: 36px; padding: 0 var(--space-4);
  background: var(--color-bg-surface); border-bottom: 1px solid var(--color-border);
  flex-shrink: 0; overflow-x: auto;
}
.subnav-item {
  display: inline-flex; align-items: center;
  padding: 4px 14px; border-radius: var(--radius-sm);
  font-size: 12px; color: var(--color-text-muted); text-decoration: none;
  white-space: nowrap; transition: all 0.2s;
}
.subnav-item:hover { color: var(--color-text-primary); background: var(--color-bg-hover); }
.subnav-active { color: var(--color-primary); background: var(--color-primary-bg, rgba(59,130,246,0.1)); font-weight: 500; }

/* ===== WS Dot ===== */
.ws-dot { width: 6px; height: 6px; border-radius: 50%; flex-shrink: 0; }
.ws-dot.online { background: var(--color-success); }
.ws-dot.offline { background: var(--color-danger); }

.health-dots { display: flex; gap: 4px; margin-right: 2px; }
.hd { width: 16px; height: 16px; border-radius: 50%; font-size: 9px; font-weight: 700; display: flex; align-items: center; justify-content: center; cursor: default; }
.hd.ok { background: rgba(34,197,94,0.15); color: var(--color-success); }
.hd.fail { background: rgba(220,38,38,0.15); color: var(--color-danger); }

.health-detail { padding: 0 8px; }
.hd-row { display: flex; align-items: center; gap: 12px; padding: 10px 0; border-bottom: 1px solid var(--color-border); }
.hd-dot { width: 10px; height: 10px; border-radius: 50%; }
.hd-dot.ok { background: var(--color-success); }
.hd-dot.fail { background: var(--color-danger); }
.hd-name { flex: 1; font-size: var(--text-sm); color: var(--color-text-secondary); }
.hd-status { font-size: var(--text-sm); font-weight: 600; }
.hd-status.ok { color: var(--color-success); }
.hd-status.fail { color: var(--color-danger); }
.hd-note { font-size: 11px; color: var(--color-text-muted); line-height: 1.6; }

/* ===== User Dropdown ===== */
.user-btn { display: flex; align-items: center; gap: 8px; cursor: pointer; padding: 6px 12px; border-radius: var(--radius-sm); color: var(--color-text-secondary); font-size: 13px; transition: background 0.2s; }
.user-btn:hover { background: var(--color-bg-surface); }
.user-btn .el-icon-user-solid { font-size: 16px; color: var(--color-primary); }
.user-btn .el-icon-arrow-down { font-size: 10px; color: var(--color-text-muted); }
.dd-user-info { text-align: center; padding: 4px 0; }
.dd-name { font-size: var(--text-sm); font-weight: 600; color: var(--color-text-primary); }
.dd-role { font-size: 11px; color: var(--color-text-muted); margin-top: 2px; }
.dd-section-label span { font-size: 10px; color: var(--color-text-muted); letter-spacing: 0.05em; }

/* ===== Main Content ===== */
.main-content { flex: 1; min-height: 0; background: var(--color-bg-base); padding: var(--space-4); overflow: hidden; }

/* ===== Global Element UI Overrides ===== */
.el-dropdown-menu__item i { margin-right: 6px; }
.el-table, .el-table__body-wrapper, .el-table__header-wrapper { background: var(--color-bg-elevated) !important; color: var(--color-text-secondary) !important; }
.el-table th.el-table__cell, .el-table th { background: var(--color-bg-deep) !important; color: var(--color-text-muted) !important; font-size: var(--text-xs); font-weight: 500; border-bottom: 1px solid var(--color-border) !important; padding: 7px 0 !important; text-transform: none; }
.el-table td.el-table__cell, .el-table td { background: var(--color-bg-elevated) !important; border-bottom: 1px solid var(--color-border) !important; color: var(--color-text-secondary) !important; font-size: var(--text-sm); padding: 6px 0 !important; }
.el-table tr, .el-table__body tr { background: var(--color-bg-elevated) !important; }
.el-table--striped .el-table__body tr.el-table__row--striped td { background: var(--color-bg-surface) !important; }
.el-table__body tr:hover > td { background: var(--color-bg-hover) !important; }
.el-table::before { display: none; }
.el-table__empty-block { background: var(--color-bg-elevated) !important; }
.el-table__empty-text { color: var(--color-text-muted) !important; }
.el-pagination { background: transparent !important; padding: 6px 0; }
.el-pagination button, .el-pagination .btn-prev, .el-pagination .btn-next { background: var(--color-bg-surface) !important; color: var(--color-text-muted) !important; border: 1px solid var(--color-border) !important; border-radius: var(--radius-sm) !important; min-width: 26px; height: 26px; padding: 0 6px; }
.el-pagination button:hover { color: var(--color-text-primary) !important; border-color: var(--color-border-light) !important; }
.el-pager li { background: var(--color-bg-surface) !important; color: var(--color-text-muted) !important; border-radius: var(--radius-sm) !important; min-width: 26px; height: 26px; line-height: 26px; }
.el-pager li.active { background: var(--color-primary) !important; color: #fff !important; }
.el-pagination__total, .el-pagination__jump { color: var(--color-text-muted) !important; font-size: var(--text-xs); }
.el-button { border-radius: var(--radius-sm); font-size: var(--text-sm); padding: 5px 12px; }
.el-button--default { background: var(--color-bg-surface) !important; border: 1px solid var(--color-border) !important; color: var(--color-text-secondary) !important; }
.el-button--default:hover { border-color: var(--color-border-light) !important; color: var(--color-text-primary) !important; }
.el-button--primary { background: var(--color-primary) !important; border-color: var(--color-primary) !important; }
.el-button--text { color: var(--color-primary) !important; }
.el-button--text:hover { color: var(--color-primary-hover) !important; }
.el-button--mini { padding: 3px 8px; font-size: var(--text-xs); }
.el-input__inner { background: var(--color-bg-deep) !important; border: 1px solid var(--color-border) !important; color: var(--color-text-primary) !important; border-radius: var(--radius-sm); font-size: var(--text-sm); height: 30px; line-height: 30px; }
.el-input__inner:focus { border-color: var(--color-primary) !important; }
.el-select .el-input__inner { background: var(--color-bg-deep) !important; border: 1px solid var(--color-border) !important; color: var(--color-text-primary) !important; }
.el-select-dropdown { background: var(--color-bg-surface) !important; border: 1px solid var(--color-border) !important; border-radius: var(--radius-sm); }
.el-select-dropdown__item { color: var(--color-text-secondary) !important; font-size: var(--text-sm); }
.el-select-dropdown__item.hover, .el-select-dropdown__item:hover { background: var(--color-bg-hover) !important; }
.el-select-dropdown__item.selected { color: var(--color-primary) !important; }
.el-tag { border-radius: var(--radius-sm); font-size: 10px; padding: 0 6px; height: 20px; line-height: 18px; border: none; }
.el-tag--danger { background: var(--color-danger-bg); color: var(--color-danger); }
.el-tag--warning { background: var(--color-warning-bg); color: var(--color-warning); }
.el-tag--success { background: var(--color-success-bg); color: var(--color-success); }
.el-tag--info { background: var(--color-info-bg); color: var(--color-text-secondary); }
.el-dialog { background: var(--color-bg-elevated) !important; border: 1px solid var(--color-border); border-radius: var(--radius-lg); }
.el-dialog__title { color: var(--color-text-primary) !important; }
.el-dialog__body { color: var(--color-text-secondary) !important; }
.el-message-box { background: var(--color-bg-elevated) !important; border: 1px solid var(--color-border); border-radius: var(--radius-lg); }
.el-message-box__title { color: var(--color-text-primary) !important; }
.el-message-box__message { color: var(--color-text-secondary) !important; }
.el-collapse { border: none; }
.el-collapse-item__header { background: transparent !important; color: var(--color-primary) !important; border-color: var(--color-border) !important; font-size: var(--text-sm); padding-left: 0; }
.el-collapse-item__wrap { background: transparent !important; border-color: var(--color-border) !important; }
.el-collapse-item__content { color: var(--color-text-secondary) !important; padding: 8px 0 8px 8px; }
.el-radio-button__inner { background: var(--color-bg-surface); border-color: var(--color-border); color: var(--color-text-muted); font-size: var(--text-xs); border-radius: 0; }
.el-radio-button__orig-radio:checked + .el-radio-button__inner { background: var(--color-primary); border-color: var(--color-primary); color: #fff; box-shadow: none; }
.el-divider--horizontal { border-color: var(--color-border); margin: 10px 0; }
::-webkit-scrollbar { width: 5px; height: 5px; }
::-webkit-scrollbar-track { background: var(--color-bg-deep); }
::-webkit-scrollbar-thumb { background: var(--color-border-light); border-radius: 2px; }
</style>
