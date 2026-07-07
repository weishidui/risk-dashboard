<template>
  <div class="dashboard">
    <!-- Header -->
    <div class="page-header">
      <div class="header-left">
        <span class="live-badge"><span class="live-dot"></span>LIVE</span>
        <h2 class="page-title">实时风险监控仪表盘</h2>
      </div>
      <div class="header-right">
        <span class="ws-status" :class="{ connected: wsConnected }">数据流 {{ wsConnected ? '●' : '○' }}</span>
        <span class="meta-refresh">{{ lastRefresh }}</span>
      </div>
    </div>

    <!-- Metric Cards Strip -->
    <div class="metric-strip">
      <div class="metric-card" v-for="card in metricCards" :key="card.key" :class="card.variant">
        <div class="card-accent"></div>
        <div class="card-body">
          <div class="card-label">{{ card.label }}</div>
          <div class="card-value">{{ card.value }}</div>
          <div class="card-trend" v-if="card.trend !== null" :class="card.trend >= 0 ? 'up' : 'down'">
            <span class="trend-arrow">{{ card.trend >= 0 ? '↑' : '↓' }}</span>
            <span>{{ formatTrend(card.trend) }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Main Section: Map + Right Panel -->
    <el-row :gutter="10" class="row-gap">
      <el-col :span="16">
        <div class="panel map-panel" @click="goToRiskMap">
          <div class="panel-header">
            <span class="panel-title">🇨🇳 全国风险热力分布</span>
            <div class="map-header-right">
              <span class="map-subtitle">{{ geoAlertCount }} 个实时告警点位</span>
              <router-link to="/risk-map" class="map-detail-link">详情 →</router-link>
            </div>
          </div>
          <div v-if="!mapReady" class="map-loading">
            <i class="el-icon-loading"></i> 地图加载中...
          </div>
          <div ref="mapChart" class="map-chart-box" v-show="mapReady"></div>
          <div class="map-legend">
            <span class="legend-item"><span class="legend-dot critical"></span>极度危险</span>
            <span class="legend-item"><span class="legend-dot danger"></span>高危</span>
          </div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="panel panel-side">
          <div class="panel-header">
            <span class="panel-title">风险等级分布</span>
          </div>
          <div ref="riskLevelPie" class="chart-box chart-box-sm"></div>
          <div class="alert-feed">
            <div class="alert-feed-header">
              <span>🚨 实时告警推送</span>
              <router-link to="/alerts" class="map-detail-link">详情 →</router-link>
            </div>
            <div class="alert-feed-list" ref="alertFeedList">
              <transition-group name="feed-item" tag="div">
                <div
                  class="alert-feed-item"
                  v-for="a in liveAlertFeed"
                  :key="a.alertId || a.transId || Math.random()"
                  :class="'risk-' + alertRiskClass(a.riskLevel)">
                  <span class="feed-time">{{ shortTime(a.createTime) }}</span>
                  <span class="feed-level">{{ a.riskLevel || '--' }}</span>
                  <span class="feed-city">{{ a.alertLoc || a.city || '--' }}</span>
                  <span class="feed-amount">¥{{ fmtK(a.amount) }}</span>
                </div>
              </transition-group>
              <div v-if="!liveAlertFeed.length" class="alert-feed-empty">
                <i class="el-icon-loading"></i> 等待实时数据推送...
              </div>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- Bottom Row: 3 Charts -->
    <el-row :gutter="10" class="row-gap">
      <el-col :span="10">
        <div class="panel">
          <div class="panel-header">
            <span class="panel-title">24H 交易与告警趋势</span>
            <div class="trend-tags">
              <span class="tag-tx">— 交易量</span>
              <span class="tag-al">— 告警量</span>
            </div>
          </div>
          <div ref="trendChart" class="chart-box"></div>
        </div>
      </el-col>
      <el-col :span="7">
        <div class="panel">
          <div class="panel-header">
            <span class="panel-title">城市活跃度 Top10</span>
            <router-link to="/analysis" class="map-detail-link">其他数据 →</router-link>
          </div>
          <div ref="cityBar" class="chart-box"></div>
        </div>
      </el-col>
      <el-col :span="7">
        <div class="panel">
          <div class="panel-header">
            <span class="panel-title">触发规则 Top8</span>
            <router-link to="/config" class="map-detail-link">评分规则 →</router-link>
          </div>
          <div ref="ruleBar" class="chart-box"></div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import * as echarts from 'echarts'
import { getDashboardData } from '@/api/metrics'
import { REFRESH_INTERVAL, CHART_COLORS } from '@/utils/constants'

const RISK_COLOR_MAP = {
  '极度危险': '#DC2626',
  '高危': '#F97316',
  '中危': '#F59E0B',
  '低危': '#22C55E'
}

export default {
  name: 'Dashboard',
  data() {
    return {
      dashboardData: null,
      prevMetrics: null,
      lastRefresh: '--',
      refreshTimer: null,
      mapReady: false,
      chinaGeoJson: null,
      charts: {}
    }
  },
  computed: {
    wsConnected() {
      return this.$store.state.liveTransactions?.length > 0 ||
             this.$store.state.liveAlerts?.length > 0
    },
    metricCards() {
      const d = this.dashboardData || {}
      const total = d.totalTransactions || 0
      const passRate = total > 0 ? ((d.passCount || 0) / total * 100).toFixed(1) : '0.0'
      const blockRate = total > 0 ? ((d.blockCount || 0) / total * 100).toFixed(1) : '0.0'
      const prev = this.prevMetrics

      const mkCard = (key, label, value, variant, trendKey) => {
        let trend = null
        if (prev && trendKey) {
          const oldVal = typeof prev[trendKey] === 'number' ? prev[trendKey] : 0
          if (oldVal !== 0) trend = ((value - oldVal) / Math.abs(oldVal) * 100).toFixed(0)
        }
        return { key, label, value, variant, trend: trend !== null ? parseInt(trend) : null }
      }

      return [
        mkCard('total', '实时交易量', this.fmtNum(total), 'neutral', 'totalTransactions'),
        mkCard('pass', '放行率', passRate + '%', 'success', 'passRate'),
        mkCard('block', '拦截率', blockRate + '%', 'danger', 'blockRate'),
        mkCard('users', '活跃用户', this.fmtNum(d.activeUsers || 0), 'info', 'activeUsers'),
        mkCard('score', '平均风险评分', d.avgRiskScore ? d.avgRiskScore.toFixed(1) : '0', 'warning', 'avgRiskScore')
      ]
    },
    geoAlertCount() {
      return (this.dashboardData?.geoAlerts || []).length
    },
    liveAlertFeed() {
      // Merge WebSocket live alerts with recent alerts, deduplicate, take top 10
      const wsAlerts = this.$store.state.liveAlerts || []
      const apiAlerts = this.dashboardData?.recentAlerts || []
      const seen = new Set()
      const merged = []
      for (const a of [...wsAlerts, ...apiAlerts]) {
        const id = a.alertId || a.transId
        if (!id || seen.has(id)) continue
        seen.add(id)
        merged.push(a)
        if (merged.length >= 10) break
      }
      return merged
    }
  },
  mounted() {
    this.initMap()
    this.fetchData()
    this.refreshTimer = setInterval(() => this.fetchData(), REFRESH_INTERVAL)
    window.addEventListener('resize', this.handleResize)
    document.addEventListener('theme-changed', this.handleThemeChange)
  },
  beforeDestroy() {
    clearInterval(this.refreshTimer)
    window.removeEventListener('resize', this.handleResize)
    document.removeEventListener('theme-changed', this.handleThemeChange)
    Object.values(this.charts).forEach(c => c.dispose())
  },
  watch: {
    dashboardData() {
      this.$nextTick(() => this.renderAllCharts())
    }
  },
  methods: {
    async initMap() {
      try {
        const resp = await fetch('/map/china.json')
        this.chinaGeoJson = await resp.json()
        echarts.registerMap('china', this.chinaGeoJson)
        this.mapReady = true
        this.$nextTick(() => {
          if (this.dashboardData) this.renderMapChart()
        })
      } catch (e) {
        console.error('Map load failed:', e)
      }
    },

    async fetchData() {
      try {
        const res = await getDashboardData()
        if (res.code === 200 && res.data) {
          // Store previous metrics for trend calculation
          if (this.dashboardData) {
            const d = this.dashboardData
            const total = d.totalTransactions || 0
            this.prevMetrics = {
              totalTransactions: d.totalTransactions,
              passRate: total > 0 ? (d.passCount || 0) / total * 100 : 0,
              blockRate: total > 0 ? (d.blockCount || 0) / total * 100 : 0,
              activeUsers: d.activeUsers,
              avgRiskScore: d.avgRiskScore,
              avgLatency: d.avgLatency
            }
          }
          this.dashboardData = res.data
          this.lastRefresh = new Date().toLocaleTimeString('zh-CN')
        }
      } catch (e) { /* ignore */ }
    },

    handleResize() {
      Object.values(this.charts).forEach(c => {
        try { c.resize() } catch (_) { /* ignore */ }
      })
    },

    handleThemeChange() {
      this.$nextTick(() => {
        Object.values(this.charts).forEach(c => {
          try { c.dispose() } catch (_) { /* ignore */ }
        })
        this.charts = {}
        this.renderAllCharts()
      })
    },

    renderAllCharts() {
      this.renderMapChart()
      this.renderRiskLevelPie()
      this.renderTrendChart()
      this.renderCityBar()
      this.renderRuleBar()
    },

    /* ========== Map Chart ========== */
    mapColors() {
      const isLight = (localStorage.getItem('rd-mode') || 'dark') === 'light'
      return {
        geoFill: isLight ? '#F0F0F0' : '#162339',
        geoBorder: isLight ? '#D9D9D9' : '#1C2B42',
        tooltipBg: isLight ? '#FFFFFF' : '#121E33',
        tooltipText: isLight ? 'rgba(0,0,0,0.88)' : '#D8DFE8',
        emphasisFill: isLight ? '#E0E0E0' : '#1A3050',
        labelColor: isLight ? 'rgba(0,0,0,0.65)' : '#D8DFE8'
      }
    },

    renderMapChart() {
      const el = this.$refs.mapChart
      if (!el || !this.mapReady) return
      if (!this.charts.map) this.charts.map = echarts.init(el)

      const geoAlerts = this.dashboardData?.geoAlerts || []
      const mc = this.mapColors()

      // Build scatter data: color by risk level
      const scatterData = geoAlerts.map(a => ({
        name: a.city || '未知',
        value: [a.longitude || 0, a.latitude || 0, a.riskLevel || '中危'],
        alertId: a.alertId,
        city: a.city,
        riskLevel: a.riskLevel,
        hitRules: a.hitRules
      }))

      // Only show 极度危险 + 高危 on the dashboard map
      const criticalPoints = scatterData.filter(d => d.riskLevel === '极度危险')
      const highPoints = scatterData.filter(d => d.riskLevel === '高危')

      this.charts.map.setOption({
        tooltip: {
          trigger: 'item',
          backgroundColor: mc.tooltipBg,
          borderColor: mc.geoBorder,
          textStyle: { color: mc.tooltipText, fontSize: 11 },
          formatter: function(p) {
            if (p.seriesType === 'effectScatter' || p.seriesType === 'scatter') {
              const d = p.data
              return `<b>${d.city || d.name}</b><br/>
                风险等级: <span style="color:${RISK_COLOR_MAP[d.riskLevel] || '#999'}">${d.riskLevel || '--'}</span><br/>
                触发规则: ${d.hitRules || '--'}`
            }
            return p.name
          }
        },
        geo: {
          map: 'china',
          roam: false,
          zoom: 0.85,
          center: [105, 30],
          top: 5,
          bottom: 5,
          aspectScale: 0.82,
          label: { show: false },
          emphasis: {
            label: { show: true, color: mc.labelColor, fontSize: 11 },
            itemStyle: { areaColor: mc.emphasisFill }
          },
          itemStyle: {
            areaColor: mc.geoFill,
            borderColor: mc.geoBorder,
            borderWidth: 1
          }
        },
        series: [
          {
            name: '极度危险',
            type: 'effectScatter',
            coordinateSystem: 'geo',
            data: criticalPoints,
            symbolSize: 12,
            showEffectOn: 'render',
            rippleEffect: {
              brushType: 'stroke',
              scale: 4,
              period: 2.5
            },
            itemStyle: { color: '#DC2626', shadowBlur: 12, shadowColor: '#DC2626' },
            zlevel: 1
          },
          {
            name: '高危',
            type: 'effectScatter',
            coordinateSystem: 'geo',
            data: highPoints,
            symbolSize: 9,
            showEffectOn: 'render',
            rippleEffect: {
              brushType: 'stroke',
              scale: 3.5,
              period: 3
            },
            itemStyle: { color: '#F97316', shadowBlur: 8, shadowColor: '#F97316' },
            zlevel: 1
          }
        ]
      }, true)
    },

    /* ========== Risk Level Donut ========== */
    renderRiskLevelPie() {
      const el = this.$refs.riskLevelPie
      if (!el) return
      if (!this.charts.riskLevelPie) this.charts.riskLevelPie = echarts.init(el)
      const raw = this.dashboardData?.riskLevelDistribution || []
      const data = raw.map(d => ({
        name: d.name,
        value: d.value,
        itemStyle: { color: d.color || RISK_COLOR_MAP[d.name] || '#5D6F85' }
      }))

      // Click handler: navigate to alert management with risk level filter
      this.charts.riskLevelPie.off('click')
      this.charts.riskLevelPie.on('click', (params) => {
        if (params.name) {
          this.$router.push({ path: '/alerts', query: { riskLevel: params.name } })
        }
      })

      this.charts.riskLevelPie.setOption({
        tooltip: {
          trigger: 'item',
          formatter: '{b}: {c} ({d}%)',
          backgroundColor: '#0F1A2C',
          borderColor: '#1C2B42',
          textStyle: { color: '#D8DFE8', fontSize: 11 },
          confine: true
        },
        legend: {
          bottom: 0,
          textStyle: { color: '#5D6F85', fontSize: 10 },
          itemWidth: 8, itemHeight: 8,
          itemGap: 8
        },
        series: [{
          type: 'pie',
          radius: ['50%', '72%'],
          center: ['50%', '48%'],
          avoidLabelOverlap: false,
          label: { show: false },
          emphasis: {
            label: { show: true, fontSize: 12, fontWeight: 'bold' }
          },
          data
        }]
      }, true)
    },

    /* ========== 24H Dual-Line Trend ========== */
    renderTrendChart() {
      const el = this.$refs.trendChart
      if (!el) return
      if (!this.charts.trend) this.charts.trend = echarts.init(el)
      const d = this.dashboardData
      const txData = d?.transactionTrend || []
      const alData = d?.alertTrend || []

      this.charts.trend.setOption({
        tooltip: {
          trigger: 'axis',
          backgroundColor: '#0F1A2C',
          borderColor: '#1C2B42',
          textStyle: { color: '#D8DFE8', fontSize: 11 }
        },
        legend: {
          data: ['交易量', '告警量'],
          bottom: 0,
          textStyle: { color: '#5D6F85', fontSize: 10 },
          itemWidth: 12, itemHeight: 2
        },
        grid: { left: 48, right: 48, top: 8, bottom: 38 },
        xAxis: {
          type: 'category',
          data: txData.map(p => p.time),
          axisLine: { lineStyle: { color: '#1C2B42' } },
          axisLabel: { color: '#5D6F85', fontSize: 9 },
          axisTick: { show: false }
        },
        yAxis: {
          type: 'value',
          axisLine: { show: false },
          axisLabel: { color: '#5D6F85', fontSize: 9 },
          splitLine: { lineStyle: { color: '#1C2B42', type: 'dashed' } }
        },
        series: [
          {
            name: '交易量', type: 'line',
            data: txData.map(p => p.value),
            smooth: true, symbol: 'none',
            lineStyle: { color: '#3B82F6', width: 2 },
            areaStyle: {
              color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                { offset: 0, color: 'rgba(59,130,246,0.25)' },
                { offset: 1, color: 'rgba(59,130,246,0.02)' }
              ])
            }
          },
          {
            name: '告警量', type: 'line',
            data: alData.map(p => p.value),
            smooth: true, symbol: 'none',
            lineStyle: { color: '#F97316', width: 2 },
            areaStyle: {
              color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                { offset: 0, color: 'rgba(249,115,22,0.2)' },
                { offset: 1, color: 'rgba(249,115,22,0.02)' }
              ])
            }
          }
        ]
      }, true)
    },

    /* ========== City Top10 Horizontal Bar ========== */
    renderCityBar() {
      const el = this.$refs.cityBar
      if (!el) return
      if (!this.charts.cityBar) this.charts.cityBar = echarts.init(el)
      const data = (this.dashboardData?.cityDistribution || []).slice(0, 10).reverse()

      this.charts.cityBar.setOption({
        tooltip: {
          trigger: 'axis',
          backgroundColor: '#0F1A2C',
          borderColor: '#1C2B42',
          textStyle: { color: '#D8DFE8', fontSize: 11 }
        },
        grid: { left: 48, right: 20, top: 4, bottom: 4 },
        xAxis: {
          type: 'value',
          axisLine: { show: false },
          axisLabel: { color: '#5D6F85', fontSize: 9 },
          splitLine: { lineStyle: { color: '#1C2B42', type: 'dashed' } }
        },
        yAxis: {
          type: 'category',
          data: data.map(d => d.name),
          axisLine: { show: false },
          axisTick: { show: false },
          axisLabel: { color: '#9AACBF', fontSize: 10 }
        },
        series: [{
          type: 'bar',
          data: data.map((d, i) => ({
            value: d.value,
            itemStyle: {
              color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
                { offset: 0, color: CHART_COLORS[i % CHART_COLORS.length] },
                { offset: 1, color: 'rgba(59,130,246,0.15)' }
              ]),
              borderRadius: [0, 2, 2, 0]
            }
          })),
          barWidth: '60%'
        }]
      }, true)
    },

    /* ========== Rule Top8 Horizontal Bar ========== */
    renderRuleBar() {
      const el = this.$refs.ruleBar
      if (!el) return
      if (!this.charts.ruleBar) this.charts.ruleBar = echarts.init(el)
      const data = (this.dashboardData?.ruleTypeDistribution || []).slice(0, 8).reverse()

      this.charts.ruleBar.setOption({
        tooltip: {
          trigger: 'axis',
          backgroundColor: '#0F1A2C',
          borderColor: '#1C2B42',
          textStyle: { color: '#D8DFE8', fontSize: 11 }
        },
        grid: { left: 55, right: 20, top: 4, bottom: 4 },
        xAxis: {
          type: 'value',
          axisLine: { show: false },
          axisLabel: { color: '#5D6F85', fontSize: 9 },
          splitLine: { lineStyle: { color: '#1C2B42', type: 'dashed' } }
        },
        yAxis: {
          type: 'category',
          data: data.map(d => d.name),
          axisLine: { show: false },
          axisTick: { show: false },
          axisLabel: { color: '#9AACBF', fontSize: 9 }
        },
        series: [{
          type: 'bar',
          data: data.map((d, i) => ({
            value: d.value,
            itemStyle: {
              color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
                { offset: 0, color: '#F59E0B' },
                { offset: 1, color: 'rgba(245,158,11,0.1)' }
              ]),
              borderRadius: [0, 2, 2, 0]
            }
          })),
          barWidth: '55%'
        }]
      }, true)
    },

    goToRiskMap() {
      this.$router.push('/risk-map')
    },

    /* ========== Helpers ========== */
    fmtNum(n) {
      if (n == null) return '0'
      if (n >= 10000) return (n / 10000).toFixed(1) + 'w'
      if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
      return n.toLocaleString()
    },
    fmtK(n) {
      if (n == null) return '0'
      if (n >= 10000) return (n / 10000).toFixed(1) + '万'
      return Number(n).toLocaleString()
    },
    formatTrend(v) {
      return Math.abs(v) + '%'
    },
    alertRiskClass(level) {
      if (level === '极度危险') return 'critical'
      if (level === '高危') return 'danger'
      if (level === '中危') return 'warning'
      return 'low'
    },
    shortTime(t) {
      if (!t) return '--:--:--'
      if (typeof t === 'number') {
        return new Date(t).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
      }
      const m = String(t).match(/(\d{2}:\d{2}:\d{2})/)
      return m ? m[1] : String(t).slice(-8)
    }
  }
}
</script>

<style scoped>
.dashboard { min-height: 100%; padding-bottom: var(--space-4); }

/* ========== Header ========== */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-3);
  padding-bottom: var(--space-3);
  border-bottom: 1px solid var(--color-border);
}

.header-left {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.page-title {
  color: var(--color-text-primary);
  font-size: var(--text-lg);
  font-weight: 600;
  margin: 0;
}

.header-right {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.meta-refresh {
  color: var(--color-text-muted);
  font-size: var(--text-xs);
  font-family: var(--font-mono);
}

.ws-status {
  font-size: 11px;
  color: var(--color-text-muted);
  font-family: var(--font-mono);
}

.ws-status.connected { color: var(--color-success); }

/* ========== Live Badge ========== */
.live-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: rgba(220,38,38,0.12);
  color: #DC2626;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 2px;
  padding: 2px 8px;
  border-radius: 2px;
  border: 1px solid rgba(220,38,38,0.25);
}

.live-dot {
  width: 6px; height: 6px;
  border-radius: 50%;
  background: #DC2626;
  display: inline-block;
  animation: live-pulse 1.5s ease-in-out infinite;
}

@keyframes live-pulse {
  0%, 100% { opacity: 1; box-shadow: 0 0 2px #DC2626; }
  50% { opacity: 0.4; box-shadow: 0 0 8px #DC2626; }
}

/* ========== Metric Cards ========== */
.metric-strip {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 10px;
  margin-bottom: 0;
}

.metric-card {
  background: var(--color-bg-elevated);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: var(--space-3) var(--space-4);
  display: flex;
  gap: var(--space-3);
  position: relative;
  overflow: hidden;
  transition: border-color 0.3s, box-shadow 0.3s;
}

.metric-card:hover {
  border-color: rgba(255,255,255,0.1);
  box-shadow: 0 0 20px rgba(0,0,0,0.3);
}

.card-accent {
  position: absolute;
  left: 0; top: 8px; bottom: 8px;
  width: 3px;
  border-radius: 0 2px 2px 0;
  background: #5D6F85;
}

.metric-card.success .card-accent { background: var(--color-success); }
.metric-card.warning .card-accent { background: var(--color-warning); }
.metric-card.danger  .card-accent { background: var(--color-danger); }
.metric-card.info    .card-accent { background: var(--color-info); }
.metric-card.neutral .card-accent { background: var(--color-text-muted); }

.card-body { flex: 1; min-width: 0; }

.card-label {
  color: var(--color-text-muted);
  font-size: var(--text-xs);
  margin-bottom: 2px;
  white-space: nowrap;
}

.card-value {
  font-family: var(--font-mono);
  font-size: 22px;
  font-weight: 700;
  color: var(--color-text-primary);
  line-height: 1.2;
  letter-spacing: -0.5px;
}

.card-trend {
  font-size: 11px;
  font-family: var(--font-mono);
  margin-top: 2px;
}

.card-trend.up   { color: var(--color-success); }
.card-trend.down { color: var(--color-danger); }

.trend-arrow { font-size: 13px; }

/* ========== Panel Shared ========== */
.row-gap { margin-top: var(--space-3); }

.panel {
  background: var(--color-bg-elevated);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: var(--space-3);
  height: 100%;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-2);
  padding-bottom: var(--space-2);
  border-bottom: 1px solid var(--color-border);
}

.panel-title {
  color: var(--color-text-secondary);
  font-size: var(--text-sm);
  font-weight: 500;
}

.chart-box { width: 100%; height: 260px; }
.chart-box-sm { height: 180px; }

/* ========== Map Panel ========== */
.map-panel {
  overflow: hidden;
  display: flex;
  flex-direction: column;
  cursor: pointer;
}

.map-panel .panel-header { flex-shrink: 0; }
.map-panel .map-chart-box { flex: 1; }
.map-panel .map-loading { flex: 1; }
.map-panel .map-legend { flex-shrink: 0; }

.map-subtitle {
  font-size: 11px;
  color: var(--color-text-muted);
  font-family: var(--font-mono);
}

.map-header-right {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.map-detail-link {
  font-size: 11px;
  color: var(--color-primary);
  text-decoration: none;
  font-weight: 500;
  transition: color 0.2s;
}

.map-detail-link:hover { color: #60A5FA; }

.map-loading {
  height: 100%;
  min-height: 435px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-muted);
  font-size: var(--text-sm);
}

.map-chart-box {
  width: 100%;
  height: 100%;
  min-height: 435px;
}

.map-legend {
  display: flex;
  justify-content: center;
  gap: var(--space-4);
  padding-top: 4px;
  border-top: 1px solid var(--color-border);
  margin-top: 2px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 10px;
  color: var(--color-text-muted);
}

.legend-dot {
  width: 8px; height: 8px;
  border-radius: 50%;
  display: inline-block;
}

.legend-dot.critical { background: #DC2626; box-shadow: 0 0 6px #DC2626; }
.legend-dot.danger   { background: #F97316; box-shadow: 0 0 6px #F97316; }
.legend-dot.warning  { background: #F59E0B; }
.legend-dot.success  { background: #22C55E; }

/* ========== Side Panel ========== */
.panel-side {
  display: flex;
  flex-direction: column;
}

/* ========== Alert Feed ========== */
.alert-feed {
  margin-top: var(--space-2);
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.alert-feed-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: var(--text-xs);
  color: var(--color-warning);
  font-weight: 500;
  padding-bottom: 6px;
  border-bottom: 1px solid var(--color-border);
  margin-bottom: 4px;
}

.alert-feed-list {
  flex: 1;
  overflow-y: auto;
}

.alert-feed-list::-webkit-scrollbar { width: 3px; }
.alert-feed-list::-webkit-scrollbar-thumb { background: #253652; border-radius: 2px; }

.alert-feed-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 6px;
  border-radius: 2px;
  font-size: 11px;
  font-family: var(--font-mono);
  border-left: 2px solid transparent;
  margin-bottom: 2px;
  transition: background 0.2s;
}

.alert-feed-item:hover { background: rgba(255,255,255,0.03); }
.alert-feed-item.risk-critical { border-left-color: #DC2626; }
.alert-feed-item.risk-danger   { border-left-color: #F97316; }
.alert-feed-item.risk-warning  { border-left-color: #F59E0B; }
.alert-feed-item.risk-low      { border-left-color: #22C55E; }

.feed-time { color: var(--color-text-muted); min-width: 52px; }
.feed-level { font-weight: 600; min-width: 28px; font-size: 10px; }
.feed-city { color: var(--color-text-secondary); min-width: 36px; }
.feed-amount { margin-left: auto; color: var(--color-text-primary); font-weight: 500; }

.risk-critical .feed-level { color: #DC2626; }
.risk-danger .feed-level   { color: #F97316; }
.risk-warning .feed-level  { color: #F59E0B; }
.risk-low .feed-level      { color: #22C55E; }

.alert-feed-empty {
  text-align: center;
  color: var(--color-text-muted);
  font-size: 11px;
  padding: var(--space-4) 0;
}

/* ========== Feed Item Transition ========== */
.feed-item-enter-active { animation: feedSlideIn 0.4s ease-out; }
.feed-item-leave-active { animation: feedSlideOut 0.3s ease-in; }

@keyframes feedSlideIn {
  from { opacity: 0; transform: translateX(20px); }
  to   { opacity: 1; transform: translateX(0); }
}

@keyframes feedSlideOut {
  from { opacity: 1; transform: translateX(0); }
  to   { opacity: 0; transform: translateX(-20px); }
}

/* ========== Trend Tags ========== */
.trend-tags { display: flex; gap: var(--space-3); }
.tag-tx { font-size: 10px; color: #3B82F6; }
.tag-al { font-size: 10px; color: #F97316; }

/* ========== Responsive ========== */
@media (max-width: 1400px) {
  .metric-strip { grid-template-columns: repeat(5, 1fr); }
  .map-chart-box, .map-loading { min-height: 280px; }
}
</style>
