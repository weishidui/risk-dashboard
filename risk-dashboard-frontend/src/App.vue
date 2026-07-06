<template>
  <div id="app">
    <!-- 未登录：仅显示登录/注册页面 -->
    <template v-if="!isLoggedIn">
      <router-view />
    </template>

    <!-- 已登录：侧边栏 + 主内容区 -->
    <el-container v-else class="app-container">
      <el-aside width="200px" class="sidebar">
        <div class="logo">
          <span>RISK MONITOR</span>
        </div>

        <div class="user-info">
          <div class="user-name">{{ username }}</div>
          <div class="user-role">{{ roleLabel }}</div>
          <div v-if="isAuditor" class="auditor-tag">只读模式</div>
        </div>

        <el-menu
          :default-active="activeMenu"
          router
          background-color="var(--color-sidebar-bg)"
          text-color="var(--color-sidebar-text)"
          active-text-color="var(--color-sidebar-text-active)"
          class="sidebar-menu">
          <el-menu-item v-if="canAccess('/dashboard')" index="/dashboard">
            <i class="el-icon-s-data"></i><span>实时监控仪表盘</span>
          </el-menu-item>
          <el-menu-item v-if="canAccess('/transaction')" index="/transaction">
            <i class="el-icon-s-order"></i><span>交易流水监控</span>
          </el-menu-item>
          <el-menu-item v-if="canAccess('/alerts')" index="/alerts">
            <i class="el-icon-warning"></i><span>风险告警管理</span>
          </el-menu-item>
          <el-menu-item v-if="canAccess('/risk-map')" index="/risk-map">
            <i class="el-icon-location-outline"></i><span>风险地理分布</span>
          </el-menu-item>
          <el-menu-item v-if="canAccess('/analysis')" index="/analysis">
            <i class="el-icon-pie-chart"></i><span>数据统计分析</span>
          </el-menu-item>
          <el-menu-item v-if="canAccess('/config')" index="/config">
            <i class="el-icon-setting"></i><span>系统配置</span>
          </el-menu-item>
        </el-menu>

        <div class="sidebar-bottom">
          <div class="system-status">
            <span class="dot" :class="wsStatus === '已连接' ? 'online' : 'offline'"></span>
            <span>{{ wsStatus === '已连接' ? 'WS 已连接' : wsStatus }}</span>
          </div>
          <div class="logout-btn" @click="handleLogout">
            <i class="el-icon-switch-button"></i><span>退出登录</span>
          </div>
          <ThemeSwitcher />
        </div>
      </el-aside>

      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>
  </div>
</template>

<script>
import ThemeSwitcher from '@/components/ThemeSwitcher.vue'
import { ROLE_LABELS, ROLE_ROUTES } from '@/utils/constants'

export default {
  name: 'App',
  components: { ThemeSwitcher },
  data() {
    return { wsStatus: '未连接', ws: null }
  },
  computed: {
    isLoggedIn() { return this.$store.getters.isLoggedIn },
    username()   { return this.$store.getters.username },
    roleLabel()  { return ROLE_LABELS[this.$store.getters.userRole] || '' },
    isAuditor()  { return this.$store.getters.userRole === 'auditor' },
    activeMenu() { return this.$route.path }
  },
  watch: {
    isLoggedIn(val) {
      if (val) this.connectWebSocket()
      else if (this.ws) { this.ws.close(); this.ws = null }
    }
  },
  mounted() {
    if (this.isLoggedIn) this.connectWebSocket()
  },
  beforeDestroy() {
    if (this.ws) this.ws.close()
  },
  methods: {
    canAccess(path) {
      const allowed = ROLE_ROUTES[this.$store.getters.userRole] || []
      return allowed.includes(path)
    },
    handleLogout() {
      this.$store.dispatch('logout')
      if (this.ws) { this.ws.close(); this.ws = null }
      this.wsStatus = '未连接'
      this.$router.push('/login')
    },
    connectWebSocket() {
      const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
      const wsHost = process.env.NODE_ENV === 'development' ? 'localhost:8080' : window.location.host
      const wsUrl = `${protocol}//${wsHost}/ws/risk`
      this.ws = new WebSocket(wsUrl)
      this.ws.onopen = () => { this.wsStatus = '已连接' }
      this.ws.onmessage = (event) => {
        try {
          const msg = JSON.parse(event.data)
          this.$store.dispatch('handleWsMessage', msg)
        } catch (e) {}
      }
      this.ws.onclose = () => {
        this.wsStatus = '断开'
        if (this.isLoggedIn) setTimeout(() => this.connectWebSocket(), 5000)
      }
      this.ws.onerror = () => { this.wsStatus = '异常' }
    }
  }
}
</script>

<style>
*,
*::before,
*::after {
  margin: 0; padding: 0; box-sizing: border-box;
}

body {
  font-family: var(--font-body);
  font-size: var(--text-base);
  background: var(--color-bg-deep);
  color: var(--color-text-primary);
}

.app-container { height: 100vh; }

/* ===== Sidebar ===== */
.sidebar {
  background: var(--color-sidebar-bg) !important;
  border-right: 1px solid var(--color-border);
  display: flex; flex-direction: column; overflow-y: auto;
}

.logo {
  padding: 14px 14px 12px;
  border-bottom: 1px solid var(--color-border);
  color: var(--color-sidebar-text);
  font-size: 11px; font-weight: 600;
  letter-spacing: 0.15em; text-align: center;
}

/* User info */
.user-info {
  padding: 10px 14px 8px;
  border-bottom: 1px solid var(--color-border);
  text-align: center;
}

.user-name {
  color: var(--color-text-primary);
  font-size: var(--text-sm); font-weight: 500;
}

.user-role {
  color: var(--color-text-muted);
  font-size: var(--text-xs); margin-top: 2px;
}

.auditor-tag {
  margin-top: 4px;
  display: inline-block;
  padding: 0 6px; height: 18px; line-height: 18px;
  font-size: 10px; color: var(--color-warning);
  background: var(--color-warning-bg);
  border-radius: 2px;
}

.sidebar-menu { border-right: none !important; flex: 1; }
.sidebar-menu .el-menu-item {
  font-family: var(--font-body);
  font-size: var(--text-base); height: 40px; line-height: 40px;
  margin: 0; border-radius: 0;
}

.sidebar-menu .el-menu-item:hover { background: var(--color-bg-surface) !important; }
.sidebar-menu .el-menu-item.is-active {
  background: var(--color-bg-surface) !important;
  border-left: 2px solid var(--color-primary);
  color: var(--color-primary) !important;
}

/* Sidebar bottom */
.sidebar-bottom { border-top: 1px solid var(--color-border); }

.system-status {
  padding: 8px 14px;
  display: flex; align-items: center; gap: 6px;
  font-size: var(--text-xs); color: var(--color-sidebar-text);
}

.logout-btn {
  padding: 6px 14px;
  display: flex; align-items: center; gap: 6px;
  font-size: var(--text-xs); color: var(--color-sidebar-text);
  cursor: pointer;
}

.logout-btn:hover { color: var(--color-danger); }

.dot {
  width: 6px; height: 6px; border-radius: 50%; flex-shrink: 0;
}

.dot.online { background: var(--color-success); }
.dot.offline { background: var(--color-danger); }

/* ===== Main Content ===== */
.main-content {
  background: var(--color-bg-base);
  padding: var(--space-4); overflow-y: auto;
}

/* ===== Global Element UI Overrides ===== */
.el-table, .el-table__body-wrapper, .el-table__header-wrapper {
  background: var(--color-bg-elevated) !important;
  color: var(--color-text-secondary) !important;
}

.el-table th.el-table__cell, .el-table th {
  background: var(--color-bg-deep) !important;
  color: var(--color-text-muted) !important;
  font-size: var(--text-xs); font-weight: 500;
  border-bottom: 1px solid var(--color-border) !important;
  padding: 7px 0 !important; text-transform: none;
}

.el-table td.el-table__cell, .el-table td {
  background: var(--color-bg-elevated) !important;
  border-bottom: 1px solid var(--color-border) !important;
  color: var(--color-text-secondary) !important;
  font-size: var(--text-sm); padding: 6px 0 !important;
}

.el-table tr, .el-table__body tr { background: var(--color-bg-elevated) !important; }
.el-table--striped .el-table__body tr.el-table__row--striped td { background: var(--color-bg-surface) !important; }
.el-table__body tr:hover > td { background: var(--color-bg-hover) !important; }
.el-table::before { display: none; }
.el-table__empty-block { background: var(--color-bg-elevated) !important; }
.el-table__empty-text { color: var(--color-text-muted) !important; }

.el-pagination { background: transparent !important; padding: 6px 0; }
.el-pagination button, .el-pagination .btn-prev, .el-pagination .btn-next {
  background: var(--color-bg-surface) !important; color: var(--color-text-muted) !important;
  border: 1px solid var(--color-border) !important; border-radius: var(--radius-sm) !important;
  min-width: 26px; height: 26px; padding: 0 6px;
}

.el-pagination button:hover { color: var(--color-text-primary) !important; border-color: var(--color-border-light) !important; }
.el-pager li {
  background: var(--color-bg-surface) !important; color: var(--color-text-muted) !important;
  border-radius: var(--radius-sm) !important; min-width: 26px; height: 26px; line-height: 26px;
}

.el-pager li.active { background: var(--color-primary) !important; color: #fff !important; }
.el-pagination__total, .el-pagination__jump { color: var(--color-text-muted) !important; font-size: var(--text-xs); }

.el-button { border-radius: var(--radius-sm); font-size: var(--text-sm); padding: 5px 12px; }
.el-button--default {
  background: var(--color-bg-surface) !important; border: 1px solid var(--color-border) !important;
  color: var(--color-text-secondary) !important;
}

.el-button--default:hover { border-color: var(--color-border-light) !important; color: var(--color-text-primary) !important; }
.el-button--primary { background: var(--color-primary) !important; border-color: var(--color-primary) !important; }
.el-button--text { color: var(--color-primary) !important; }
.el-button--text:hover { color: var(--color-primary-hover) !important; }
.el-button--mini { padding: 3px 8px; font-size: var(--text-xs); }

.el-input__inner {
  background: var(--color-bg-deep) !important; border: 1px solid var(--color-border) !important;
  color: var(--color-text-primary) !important; border-radius: var(--radius-sm);
  font-size: var(--text-sm); height: 30px; line-height: 30px;
}

.el-input__inner:focus { border-color: var(--color-primary) !important; }
.el-select .el-input__inner {
  background: var(--color-bg-deep) !important; border: 1px solid var(--color-border) !important;
  color: var(--color-text-primary) !important;
}

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
.el-collapse-item__header {
  background: transparent !important; color: var(--color-primary) !important;
  border-color: var(--color-border) !important; font-size: var(--text-sm); padding-left: 0;
}

.el-collapse-item__wrap { background: transparent !important; border-color: var(--color-border) !important; }
.el-collapse-item__content { color: var(--color-text-secondary) !important; padding: 8px 0 8px 8px; }

.el-radio-button__inner {
  background: var(--color-bg-surface); border-color: var(--color-border);
  color: var(--color-text-muted); font-size: var(--text-xs); border-radius: 0;
}

.el-radio-button__orig-radio:checked + .el-radio-button__inner {
  background: var(--color-primary); border-color: var(--color-primary); color: #fff; box-shadow: none;
}

.el-divider--horizontal { border-color: var(--color-border); margin: 10px 0; }

::-webkit-scrollbar { width: 5px; height: 5px; }
::-webkit-scrollbar-track { background: var(--color-bg-deep); }
::-webkit-scrollbar-thumb { background: var(--color-border-light); border-radius: 2px; }
</style>
