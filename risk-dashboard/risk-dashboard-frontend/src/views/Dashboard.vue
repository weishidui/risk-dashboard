<template>
  <div class="dashboard">
    <!-- Metric Cards Strip -->
    <div class="metric-strip">
      <div class="metric-card" v-for="card in metricCards" :key="card.key" :class="card.variant">
        <router-link v-if="card.key === 'total'" to="/transaction" class="card-chevron">&gt;</router-link>
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

    <!-- Main Section: Stacked Left Column + Map + AlertFeed (6+13+5=24) -->
    <el-row :gutter="10" type="flex" class="row-gap flex-row main-dashboard-row">
      <!-- 1. 左侧堆叠栏：实时风险指数 & 风险等级分布 (Span 5) -->
      <el-col :span="5" class="col-stacked">
        <!-- 上：实时风险指数 -->
        <div class="panel panel-gauge panel-stacked-item">
          <div class="panel-header"><span class="panel-title">实时风险指数</span></div>
          <div ref="gaugeChart" class="chart-gauge-stacked"></div>
        </div>
        <!-- 下：风险等级分布 -->
        <div class="panel panel-gauge panel-stacked-item">
          <div class="panel-header"><span class="panel-title">风险等级分布</span></div>
          <div ref="riskLevelPie" class="chart-gauge-stacked"></div>
        </div>
      </el-col>

      <!-- 2. 全国风险热力分布 (Span 12) -->
      <el-col :span="12">
        <div class="panel map-panel" @click="goToRiskMap">
          <div class="panel-header">
            <span class="panel-title">🇨🇳 全国风险热力分布</span>
            <div class="map-header-right">
              <span class="map-subtitle">{{ geoAlertCount }} 个实时风险省份</span>
              <router-link to="/risk-map" class="map-detail-link">详情 →</router-link>
            </div>
          </div>
          <div v-if="!mapReady" class="map-loading"><i class="el-icon-loading"></i> 地图加载中...</div>
          <div ref="mapChart" class="map-chart-box" v-show="mapReady"></div>
          <div class="map-legend">
            <span class="legend-item"><span class="legend-dot critical"></span>极度危险</span>
            <span class="legend-item"><span class="legend-dot danger"></span>高危</span>
          </div>
        </div>
      </el-col>

      <!-- 3. 实时告警推送 (Span 7) -->
      <el-col :span="7">
        <div class="panel panel-side">
          <div class="alert-feed" style="margin-top:0;height:100%;">
            <div class="alert-feed-header">
              <span>🚨 实时告警推送</span>
              <router-link to="/alerts" class="map-detail-link">详情 →</router-link>
            </div>
            <div class="alert-feed-list">
              <div class="alert-feed-item" v-for="a in liveAlertFeed" :key="a.alertId || a.transId || alertTimeValue(a) || a.userId" :class="'risk-' + alertRiskClass(a.riskLevel)">
                <span class="feed-time">{{ dateTime(alertTimeValue(a)) }}</span>
                <span class="feed-level">{{ a.riskLevel || '--' }}</span>
                <span class="feed-city">{{ a.alertLoc || a.city || '--' }}</span>
                <span class="feed-amount">¥{{ fmtK(a.amount) }}</span>
              </div>
              <div v-if="!liveAlertFeed.length" class="alert-feed-empty"><i class="el-icon-loading"></i> 等待实时数据推送...</div>
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
            <div class="trend-tags"><span class="tag-tx">— 交易量</span><span class="tag-al">— 告警量</span></div>
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
            <span class="panel-title">规则命中分析</span>
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
import { getDashboardData, getRealtimeMetrics } from '@/api/metrics'
import { REFRESH_INTERVAL, CHART_COLORS, PROVINCE_CENTERS, CITY_COORDS } from '@/utils/constants'

const RISK_COLOR_MAP = {
  '极度危险': '#DC2626',
  '高危': '#F97316',
  '中危': '#F59E0B',
  '低危': '#22C55E'
}
const MAP_PULSE_TTL = 5 * 1000

const MAP_EVENT_TTL = 20 * 1000
const MAP_AGG_WINDOW = 5 * 60 * 1000
const RISK_LEVEL_RANK = { '高危': 1, '极度危险': 2 }
const EMPTY_REALTIME_METRICS = {
  available: false,
  totalTransactions: 0,
  passCount: 0,
  verifyCount: 0,
  blockCount: 0,
  activeUsers: 0,
  avgRiskScore: 0,
  riskIndex: 0,
  lowRiskCount: 0,
  mediumRiskCount: 0,
  highRiskCount: 0,
  criticalRiskCount: 0,
  riskLevelDistribution: [
    { name: '低危', value: 0, color: '#22C55E' },
    { name: '中危', value: 0, color: '#F59E0B' },
    { name: '高危', value: 0, color: '#F97316' },
    { name: '极度危险', value: 0, color: '#DC2626' }
  ],
  cityDistribution: [],
  ruleTypeDistribution: []
}

export default {
  name: 'Dashboard',
  data() {
    return {
      dashboardData: null,
      realtimeMetrics: null,
      prevMetrics: null,
      lastRefresh: '--',
      refreshTimer: null,
      realtimeMetricsTimer: null,
      mapReady: false,
      chinaGeoJson: null,
      charts: {},
      ruleTab: 'topn',
      mapClock: Date.now(),
      mapAnimationTimer: null,
      mapOptionReady: false,
      mapHoveringPoint: false,
      mapHoverReleaseTimer: null,
      mapAlertSeenAt: {}
    }
  },
  computed: {
    wsConnected() {
      return this.$store.state.liveTransactions?.length > 0 ||
             this.$store.state.liveAlerts?.length > 0
    },
    metricData() {
      return this.realtimeMetrics || EMPTY_REALTIME_METRICS
    },
    metricCards() {
      const d = this.metricData
      const total = d.totalTransactions || 0
      const passRateNum = total > 0 ? ((d.passCount || 0) / total * 100) : 0
      const blockRateNum = total > 0 ? ((d.blockCount || 0) / total * 100) : 0
      const prev = this.prevMetrics

      const mkCard = (key, label, valueStr, rawValue, variant, trendKey) => {
        let trend = null
        if (prev && trendKey) {
          const oldVal = typeof prev[trendKey] === 'number' ? prev[trendKey] : 0
          if (oldVal !== 0) trend = ((rawValue - oldVal) / Math.abs(oldVal) * 100).toFixed(0)
        }
        return { key, label, value: valueStr, variant, trend: trend !== null ? parseInt(trend) : null }
      }

      return [
        mkCard('total', '近60秒交易量', this.fmtNum(total), total, 'neutral', 'totalTransactions'),
        mkCard('pass', '放行率', passRateNum.toFixed(1) + '%', passRateNum, 'success', 'passRate'),
        mkCard('block', '拦截率', blockRateNum.toFixed(1) + '%', blockRateNum, 'danger', 'blockRate'),
        mkCard('users', '活跃用户', this.fmtNum(d.activeUsers || 0), d.activeUsers || 0, 'info', 'activeUsers'),
        mkCard('score', '平均风险评分', d.avgRiskScore ? d.avgRiskScore.toFixed(1) : '0', d.avgRiskScore || 0, 'warning', 'avgRiskScore')
      ]
    },
    geoAlertCount() {
      const layers = this.buildRealtimeMapLayers(this.mapClock)
      return layers.livePoints.length || layers.aggregatePoints.length
    },
    liveAlertFeed() {
      const wsAlerts = this.$store.state.liveAlerts || []
      const apiAlerts = this.dashboardData?.recentAlerts || []
      const seen = new Set()
      const merged = []
      for (const a of [...wsAlerts, ...apiAlerts].filter(this.isSevereAlert)) {
        const id = a.alertId || a.transId
        if (!id || seen.has(id)) continue
        seen.add(id)
        merged.push(a)
      }
      return merged
    }
  },
  mounted() {
    this.initMap()
    this.fetchData()
    this.refreshTimer = setInterval(() => this.fetchData(), REFRESH_INTERVAL)
    this.fetchRealtimeMetrics()
    this.realtimeMetricsTimer = setInterval(() => this.fetchRealtimeMetrics(), 1000)
    this.mapAnimationTimer = setInterval(() => {
      this.mapClock = Date.now()
      this.renderMapChart()
    }, 1000)
    window.addEventListener('resize', this.handleResize)
    document.addEventListener('theme-changed', this.handleThemeChange)
  },
  beforeDestroy() {
    clearInterval(this.refreshTimer)
    clearInterval(this.realtimeMetricsTimer)
    clearInterval(this.mapAnimationTimer)
    clearTimeout(this.mapHoverReleaseTimer)
    window.removeEventListener('resize', this.handleResize)
    document.removeEventListener('theme-changed', this.handleThemeChange)
    Object.values(this.charts).forEach(c => c.dispose())
  },
  watch: {
    dashboardData() {
      this.$nextTick(() => this.renderAllCharts())
    },
    realtimeMetrics() {
      this.$nextTick(() => {
        this.renderGauge()
        this.renderRiskLevelPie()
        this.renderCityBar()
        this.renderRuleBar()
      })
    },
    '$store.state.liveAlerts': {
      handler() {
        this.mapClock = Date.now()
        this.$nextTick(() => this.renderMapChart())
      },
      deep: true
    }
  },
  methods: {
    async initMap() {
      try {
        const resp = await fetch('/map/china.json')
        const rawGeoJson = await resp.json()
        this.chinaGeoJson = this.withoutSouthChinaSeaInset(rawGeoJson)
        echarts.registerMap('china', this.chinaGeoJson)
        this.mapReady = true
        this.$nextTick(() => {
          if (this.dashboardData) this.renderMapChart()
        })
      } catch (e) {
        console.error('Map load failed:', e)
      }
    },

    withoutSouthChinaSeaInset(geoJson) {
      if (!geoJson?.features) return geoJson
      return {
        ...geoJson,
        features: geoJson.features
          .filter(feature => !this.isSouthChinaSeaInset(feature))
          .map(feature => this.withoutSouthChinaSeaPolygons(feature))
      }
    },

    withoutSouthChinaSeaPolygons(feature) {
      const keepMainIslandOnly = ['海南省', '台湾省']
      if (!keepMainIslandOnly.includes(feature?.properties?.name) || feature?.geometry?.type !== 'MultiPolygon') {
        return feature
      }
      const mainIsland = feature.geometry.coordinates
        .map(polygon => ({ polygon, bounds: this.coordinateBounds(polygon) }))
        .filter(item => item.bounds)
        .sort((a, b) => this.boundsArea(b.bounds) - this.boundsArea(a.bounds))[0]?.polygon
      return {
        ...feature,
        geometry: {
          ...feature.geometry,
          coordinates: mainIsland ? [mainIsland] : feature.geometry.coordinates
        }
      }
    },

    isSouthChinaSeaInset(feature) {
      return feature?.properties?.name === '境界线'
    },

    geometryBounds(geometry) {
      if (!geometry?.coordinates) return null
      return this.coordinateBounds(geometry.coordinates)
    },

    coordinateBounds(coordinates) {
      const bounds = { minLng: Infinity, maxLng: -Infinity, minLat: Infinity, maxLat: -Infinity }
      const walk = value => {
        if (!Array.isArray(value)) return
        if (typeof value[0] === 'number' && typeof value[1] === 'number') {
          bounds.minLng = Math.min(bounds.minLng, value[0])
          bounds.maxLng = Math.max(bounds.maxLng, value[0])
          bounds.minLat = Math.min(bounds.minLat, value[1])
          bounds.maxLat = Math.max(bounds.maxLat, value[1])
          return
        }
        value.forEach(walk)
      }
      walk(coordinates)
      return Number.isFinite(bounds.minLng) ? bounds : null
    },

    boundsArea(bounds) {
      return (bounds.maxLng - bounds.minLng) * (bounds.maxLat - bounds.minLat)
    },

    async fetchData() {
      try {
        const res = await getDashboardData()
        if (res.code === 200 && res.data) {
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

    async fetchRealtimeMetrics() {
      try {
        const res = await getRealtimeMetrics()
        if (res.code !== 200) return
        const next = res.data
        if (!next?.available) {
          this.realtimeMetrics = null
          this.prevMetrics = null
          return
        }
        if (this.realtimeMetrics) {
          const total = this.realtimeMetrics.totalTransactions || 0
          this.prevMetrics = {
            totalTransactions: total,
            passRate: total > 0 ? (this.realtimeMetrics.passCount || 0) / total * 100 : 0,
            blockRate: total > 0 ? (this.realtimeMetrics.blockCount || 0) / total * 100 : 0,
            activeUsers: this.realtimeMetrics.activeUsers || 0,
            avgRiskScore: this.realtimeMetrics.avgRiskScore || 0
          }
        }
        this.realtimeMetrics = next
        this.lastRefresh = new Date().toLocaleTimeString('zh-CN')
      } catch (e) { /* keep the last successful realtime value */ }
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
        this.mapOptionReady = false
        this.mapHoveringPoint = false
        this.renderAllCharts()
      })
    },

    renderAllCharts() {
      this.renderGauge()
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
      if (this.mapHoveringPoint && this.mapOptionReady) return

      const { aggregatePoints, livePoints, pulsePoints } = this.buildRealtimeMapLayers(this.mapClock)
      const mc = this.mapColors()
      const isLight = (localStorage.getItem('rd-mode') || 'dark') === 'light'

      const areaColor = isLight ? '#F2F2F2' : {
        type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
        colorStops: [{ offset: 0, color: '#09152b' }, { offset: 1, color: '#162e54' }]
      }
      const borderColor = isLight ? '#D9D9D9' : '#32588c'
      const emphasisArea = isLight ? '#E5E5E5' : {
        type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
        colorStops: [{ offset: 0, color: '#13284f' }, { offset: 1, color: '#214685' }]
      }

      const mapOption = {
        tooltip: {
          trigger: 'item',
          backgroundColor: mc.tooltipBg, borderColor: mc.geoBorder,
          textStyle: { color: mc.tooltipText, fontSize: 11 },
          formatter: function(p) {
            if (p.seriesType === 'effectScatter' || p.seriesType === 'scatter') {
              const d = p.data
              return `<b>${d.province || d.name}</b><br/>
                风险等级: <span style="color:${RISK_COLOR_MAP[d.riskLevel] || '#999'}">${d.riskLevel || '--'}</span><br/>
                近窗告警: ${d.count || 0} 条<br/>
                最新位置: ${d.latestCity || '--'}<br/>
                最新时间: ${d.latestTimeText || '--'}`
            }
            return p.name
          }
        },
        geo: {
          map: 'china', roam: false,
          layoutCenter: ['50%', '50%'],
          layoutSize: '116%',
          aspectScale: 0.82,
          label: { show: false },
          emphasis: {
            label: { show: true, color: '#FFFFFF', fontSize: 11 },
            itemStyle: { areaColor: emphasisArea, borderColor: '#5fa1ff', borderWidth: 1.5 }
          },
          itemStyle: {
            areaColor: areaColor, borderColor: borderColor, borderWidth: 1.2,
            shadowColor: isLight ? 'rgba(0,0,0,0.06)' : 'rgba(5,14,31,0.95)',
            shadowBlur: isLight ? 3 : 15,
            shadowOffsetX: isLight ? 0 : -4,
            shadowOffsetY: isLight ? 0 : 7
          }
        },
        series: [
          {
            name: '近5分钟省级风险', type: 'scatter', coordinateSystem: 'geo',
            data: aggregatePoints,
            symbolSize: (value, params) => params.data.symbolSize,
            animation: false, animationDurationUpdate: 0,
            itemStyle: {
              color: (params) => params.data.itemStyle.color,
              opacity: (params) => params.data.itemStyle.opacity,
              shadowBlur: 10,
              shadowColor: (params) => params.data.itemStyle.color
            },
            zlevel: 1
          },
          {
            name: '20秒实时风险', type: 'scatter', coordinateSystem: 'geo',
            data: livePoints,
            symbolSize: (value, params) => params.data.symbolSize,
            animation: false, animationDurationUpdate: 0,
            itemStyle: {
              color: (params) => params.data.itemStyle.color,
              opacity: (params) => params.data.itemStyle.opacity,
              borderColor: '#FFF7D6',
              borderWidth: 2,
              shadowBlur: 38,
              shadowColor: (params) => params.data.itemStyle.color
            },
            zlevel: 2
          },
          {
            name: '新到达风险', type: 'effectScatter', coordinateSystem: 'geo',
            data: pulsePoints,
            symbolSize: (value, params) => params.data.symbolSize,
            showEffectOn: 'render',
            rippleEffect: { brushType: 'stroke', scale: 3.8, period: 2.2 },
            itemStyle: {
              color: (params) => params.data.itemStyle.color,
              opacity: (params) => params.data.itemStyle.opacity,
              borderColor: '#FFF7D6', borderWidth: 2,
              shadowBlur: 38,
              shadowColor: (params) => params.data.itemStyle.color
            },
            zlevel: 3
          }
        ]
      }

      if (!this.mapOptionReady) {
        this.charts.map.setOption(mapOption, true)
        this.mapOptionReady = true
        this.bindMapTooltipEvents()
        return
      }

      this.charts.map.setOption({
        series: [{ data: aggregatePoints }, { data: livePoints }, { data: pulsePoints }]
      }, false, true)
    },

    buildRealtimeMapLayers(now = Date.now()) {
      const alerts = this.mapSourceAlerts(now)
      const livePoints = this.buildProvincePoints(alerts, now, MAP_EVENT_TTL, true)
      return {
        aggregatePoints: this.buildProvincePoints(alerts, now, MAP_AGG_WINDOW, false),
        livePoints,
        // The permanent point follows the 20-second lifetime, while its ripple
        // fades from full strength to transparent throughout its first 5 seconds.
        pulsePoints: livePoints
          .filter(point => point.age <= MAP_PULSE_TTL)
          .map(point => {
            const pulseOpacity = Math.max(0, 1 - point.age / MAP_PULSE_TTL)
            return {
              ...point,
              itemStyle: {
                ...point.itemStyle,
                color: this.hexToRgba(
                  point.riskLevel === '极度危险' ? '#FF3B30' : '#FF9500',
                  pulseOpacity
                ),
                opacity: pulseOpacity
              }
            }
          })
      }
    },

    mapSourceAlerts(now) {
      const candidates = [
        ...(this.$store.state.liveAlerts || []),
        ...(this.dashboardData?.recentAlerts || [])
      ]
      const seen = new Set()
      return candidates.filter(alert => {
        if (!this.isSevereAlert(alert)) return false
        const key = this.alertKey(alert)
        if (seen.has(key)) return false
        seen.add(key)
        this.trackedAlertTime(alert, now)
        return true
      })
    },

    buildProvincePoints(alerts, now, windowMs, isLiveLayer) {
      const groups = new Map()
      alerts.forEach(alert => {
        const ts = this.trackedAlertTime(alert, now)
        const age = now - ts
        if (age < 0 || age > windowMs) return
        const province = this.provinceNameForAlert(alert)
        if (!province) return
        const center = this.provinceCenter(province)
        if (!center) return
        const level = this.normalizedRiskLevel(alert)
        const current = groups.get(province) || {
          province,
          count: 0,
          riskLevel: level,
          latestTime: ts,
          latestCity: alert.alertLoc || alert.city || province
        }
        current.count += 1
        if (RISK_LEVEL_RANK[level] > RISK_LEVEL_RANK[current.riskLevel]) current.riskLevel = level
        if (ts >= current.latestTime) {
          current.latestTime = ts
          current.latestCity = alert.alertLoc || alert.city || province
        }
        groups.set(province, current)
      })

      return Array.from(groups.values()).map(group => {
        const age = Math.max(0, now - group.latestTime)
        const fade = isLiveLayer ? Math.max(0.08, 1 - age / MAP_EVENT_TTL) : 0.42
        const baseColor = group.riskLevel === '极度危险' ? '#FF3B30' : '#FF9500'
        const [lng, lat] = this.provinceCenter(group.province)
        const symbolSize = isLiveLayer
          ? Math.min(26, 13 + group.count * 2)
          : Math.min(22, 9 + Math.sqrt(group.count) * 4)
        return {
          id: `province:${group.province}`,
          name: group.province,
          province: group.province,
          value: [lng, lat, group.count],
          count: group.count,
          riskLevel: group.riskLevel,
          latestCity: group.latestCity,
          latestTime: group.latestTime,
          latestTimeText: this.dateTime(group.latestTime),
          age,
          symbolSize,
          itemStyle: {
            color: this.hexToRgba(baseColor, fade),
            opacity: fade
          }
        }
      })
    },

    bindMapTooltipEvents() {
      const chart = this.charts.map
      chart.off('mouseover')
      chart.off('mouseout')
      chart.off('globalout')
      chart.on('mouseover', params => {
        if (params.seriesType !== 'scatter' && params.seriesType !== 'effectScatter') return
        clearTimeout(this.mapHoverReleaseTimer)
        this.mapHoveringPoint = true
      })
      chart.on('mouseout', params => {
        if (params.seriesType !== 'scatter' && params.seriesType !== 'effectScatter') return
        clearTimeout(this.mapHoverReleaseTimer)
        this.mapHoverReleaseTimer = setTimeout(() => this.releaseMapTooltip(), 80)
      })
      chart.on('globalout', () => this.releaseMapTooltip())
    },

    releaseMapTooltip() {
      clearTimeout(this.mapHoverReleaseTimer)
      if (!this.mapHoveringPoint) return
      this.mapHoveringPoint = false
      if (this.charts.map) this.charts.map.dispatchAction({ type: 'hideTip' })
      this.renderMapChart()
    },

    alertKey(alert) {
      return alert.alertId || alert.transId ||
        [alert.alertLoc, alert.city, alert.riskLevel, this.alertTimeValue(alert)].filter(Boolean).join('|')
    },

    alertTimeMs(alert) {
      const raw = this.alertTimeValue(alert)
      if (!raw) return null
      if (typeof raw === 'number') return raw < 1000000000000 ? raw * 1000 : raw
      const parsed = new Date(String(raw).replace(/-/g, '/')).getTime()
      return Number.isNaN(parsed) ? null : parsed
    },

    trackedAlertTime(alert, now = Date.now()) {
      const explicit = this.alertTimeMs(alert)
      if (explicit) return explicit > now + 60000 ? now : explicit
      const key = this.alertKey(alert)
      if (!this.mapAlertSeenAt[key]) this.mapAlertSeenAt[key] = now
      return this.mapAlertSeenAt[key]
    },

    normalizedRiskLevel(alert) {
      const level = (alert?.riskLevel || '').trim()
      if (level === '极度危险' || level === '高危') return level
      return Number(alert?.finalScore || 0) >= 90 ? '极度危险' : '高危'
    },

    provinceNameForAlert(alert) {
      const explicitProvince = alert.province || alert.provinceName || alert.alertProvince
      if (this.provinceCenter(explicitProvince)) return this.normalizeProvinceName(explicitProvince)

      const names = [alert.alertLoc, alert.city, alert.location, alert.name].filter(Boolean)
      for (const name of names) {
        if (this.provinceCenter(name)) return this.normalizeProvinceName(name)
        const stripped = this.stripLocationSuffix(name)
        if (this.provinceCenter(stripped)) return this.normalizeProvinceName(stripped)
        const cityCoord = this.cityCenter(name) || this.cityCenter(stripped)
        if (cityCoord) return this.provinceByCoordinate(cityCoord) || this.nearestProvinceName(cityCoord)
      }

      const lng = Number(alert.longitude)
      const lat = Number(alert.latitude)
      if (Number.isFinite(lng) && Number.isFinite(lat)) {
        const coord = [lng, lat]
        return this.provinceByCoordinate(coord) || this.nearestProvinceName(coord)
      }
      return null
    },

    provinceByCoordinate(coord) {
      if (!this.chinaGeoJson?.features || !coord) return null
      const feature = this.chinaGeoJson.features.find(feature => this.pointInGeometry(coord, feature.geometry))
      return feature?.properties?.name || null
    },

    pointInGeometry(point, geometry) {
      if (!geometry?.coordinates) return false
      if (geometry.type === 'Polygon') return this.pointInPolygon(point, geometry.coordinates)
      if (geometry.type === 'MultiPolygon') {
        return geometry.coordinates.some(polygon => this.pointInPolygon(point, polygon))
      }
      return false
    },

    pointInPolygon(point, rings) {
      if (!rings?.length || !this.pointInRing(point, rings[0])) return false
      return !rings.slice(1).some(ring => this.pointInRing(point, ring))
    },

    pointInRing(point, ring) {
      const [x, y] = point
      let inside = false
      for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
        const xi = ring[i][0]
        const yi = ring[i][1]
        const xj = ring[j][0]
        const yj = ring[j][1]
        const intersects = ((yi > y) !== (yj > y)) &&
          (x < (xj - xi) * (y - yi) / ((yj - yi) || 1e-9) + xi)
        if (intersects) inside = !inside
      }
      return inside
    },

    provinceCenter(name) {
      if (!name) return null
      const normalized = this.normalizeProvinceName(name)
      return PROVINCE_CENTERS[name] || PROVINCE_CENTERS[normalized] || null
    },

    cityCenter(name) {
      if (!name) return null
      const stripped = this.stripLocationSuffix(name)
      return CITY_COORDS[name] || CITY_COORDS[stripped] || null
    },

    normalizeProvinceName(name) {
      if (!name) return ''
      const text = String(name).trim()
      if (PROVINCE_CENTERS[text]) return text
      return this.stripLocationSuffix(text)
    },

    stripLocationSuffix(name) {
      if (!name) return ''
      return String(name).trim().replace(/(特别行政区|壮族自治区|回族自治区|维吾尔自治区|自治区|自治州|地区|新区|林区|盟|省|市)$/g, '')
    },

    nearestProvinceName(coord) {
      let best = null
      let bestDist = Infinity
      Object.entries(PROVINCE_CENTERS).forEach(([name, center]) => {
        const dx = coord[0] - center[0]
        const dy = coord[1] - center[1]
        const dist = dx * dx + dy * dy
        if (dist < bestDist) {
          best = name
          bestDist = dist
        }
      })
      return best
    },

    hexToRgba(hex, alpha) {
      const raw = hex.replace('#', '')
      const r = parseInt(raw.slice(0, 2), 16)
      const g = parseInt(raw.slice(2, 4), 16)
      const b = parseInt(raw.slice(4, 6), 16)
      return `rgba(${r}, ${g}, ${b}, ${alpha})`
    },

    /* ========== Risk Level Donut ========== */
    renderRiskLevelPie() {
      const el = this.$refs.riskLevelPie
      if (!el) return
      if (!this.charts.riskLevelPie) this.charts.riskLevelPie = echarts.init(el)
      const raw = this.metricData?.riskLevelDistribution || []
      const data = raw.map(d => ({
        name: d.name,
        value: d.value,
        itemStyle: { color: d.color || RISK_COLOR_MAP[d.name] || '#5D6F85' }
      }))

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
          radius: ['40%', '65%'],
          center: ['50%', '42%'],
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
      const data = (this.metricData?.cityDistribution || []).slice(0, 10).reverse()

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
      const data = (this.metricData?.ruleTypeDistribution || []).slice(0, 8).reverse()

      this.charts.ruleBar.setOption({
        tooltip: {
          trigger: 'axis',
          backgroundColor: '#0F1A2C',
          borderColor: '#1C2B42',
          textStyle: { color: '#D8DFE8', fontSize: 11 }
        },
        grid: { left: 36, right: 20, top: 4, bottom: 4 },
        xAxis: {
          type: 'value',
          axisLine: { show: false },
          axisLabel: { color: '#5D6F85', fontSize: 9 },
          splitLine: { lineStyle: { color: '#1C2B42', type: 'dashed' } }
        },
        yAxis: {
          type: 'category',
          data: data.map(d => this.ruleCode(d.name)),
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

    ruleCode(name) {
      const match = String(name || '').match(/^([A-I]\d+)/)
      return match ? match[1] : name
    },

    /* ========== Gauge ========== */
    renderGauge() {
      const el = this.$refs.gaugeChart
      if (!el) return
      if (!this.charts.gauge) this.charts.gauge = echarts.init(el)
      const metrics = this.metricData
      const score = metrics.riskIndex != null ? metrics.riskIndex : (metrics.avgRiskScore || 0)
      this.charts.gauge.setOption({
        series: [{
          type: 'gauge', startAngle: 210, endAngle: -30, min: 0, max: 100, splitNumber: 10,
          center: ['50%', '62%'],
          radius: '85%',
          axisLine: { lineStyle: { color: [[0.2,'#22C55E'],[0.4,'#F59E0B'],[0.6,'#F97316'],[1,'#DC2626']], width: 6 } },
          pointer: { length: '50%', width: 4, itemStyle: { color: '#9AACBF' } },
          axisTick: { distance: -6, length: 3, lineStyle: { color: '#5D6F85', width: 1 } },
          splitLine: { distance: -10, length: 10, lineStyle: { color: '#5D6F85', width: 1.5 } },
          axisLabel: { color: '#5D6F85', distance: 14, fontSize: 8 },
          detail: { formatter: '{value}', color: '#D8DFE8', fontSize: 18, fontFamily: 'monospace', offsetCenter: [0, '70%'] },
          data: [{ value: score, name: '' }]
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
      const [int, dec] = Number(n).toFixed(2).split('.')
      return int.replace(/\B(?=(\d{3})+(?!\d))/g, ',') + '.' + dec
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
    isSevereAlert(alert) {
      if (!alert) return false
      const level = (alert.riskLevel || '').trim()
      return level === '高危' || level === '极度危险' || Number(alert.finalScore || 0) >= 80
    },
    alertTimeValue(alert) {
      return alert?.createTime || alert?.alertTime || alert?.timestamp || alert?.eventTime || alert?.time
    },
    dateTime(t) {
      if (!t) return '--'
      const d = typeof t === 'number' ? new Date(t) : new Date(String(t).replace(/-/g, '/'))
      if (Number.isNaN(d.getTime())) return String(t)
      const y = d.getFullYear()
      const m = String(d.getMonth() + 1).padStart(2, '0')
      const day = String(d.getDate()).padStart(2, '0')
      const hh = String(d.getHours()).padStart(2, '0')
      const mm = String(d.getMinutes()).padStart(2, '0')
      const ss = String(d.getSeconds()).padStart(2, '0')
      return `${y}-${m}-${day} ${hh}:${mm}:${ss}`
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
.dashboard { height: 100%; overflow-y: auto; padding-bottom: var(--space-4); }

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
.card-chevron {
  position: absolute;
  right: 8px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 18px;
  font-weight: 700;
  color: var(--color-text-muted);
  text-decoration: none;
  opacity: 0.35;
  transition: opacity 0.2s, color 0.2s;
  line-height: 1;
}

.card-chevron:hover { opacity: 1; color: var(--color-primary); }
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
  min-height: 0;
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

/* === 主监控网格：加载前后都保持图一的固定结构 === */
.flex-row { align-items: stretch; }
.main-dashboard-row {
  display: grid !important;
  grid-template-columns: 5fr 12fr 7fr;
  height: 405px;
  overflow: hidden;
}
.main-dashboard-row > .el-col {
  width: auto !important;
  max-width: none !important;
  flex: none !important;
  float: none !important;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
}
.main-dashboard-row > .el-col > .panel {
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  height: auto;
  overflow: hidden;
}

.map-loading { height: 100%; min-height: 0; display: flex; align-items: center; justify-content: center; color: var(--color-text-muted); font-size: var(--text-sm); }
.map-chart-box { width: 100%; flex: 1 1 0; height: 0; min-height: 0; overflow: hidden; }
.panel-gauge { display: grid; grid-template-rows: auto minmax(0, 1fr); min-height: 0; overflow: hidden; }
.panel-gauge .panel-header { width: 100%; }
.chart-gauge { width: 100%; flex: 1; min-height: 240px; }
.map-chart-box > div,
.map-chart-box canvas,
.chart-gauge-stacked > div,
.chart-gauge-stacked canvas {
  max-width: 100% !important;
  max-height: 100% !important;
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
.panel-side { display: flex; flex-direction: column; height: 100%; min-height: 0; overflow: hidden; }
.panel-side .alert-feed { height: 100%; display: flex; flex-direction: column; min-height: 0; }

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
  min-height: 0;
}

.alert-feed-list::-webkit-scrollbar { width: 3px; }
.alert-feed-list::-webkit-scrollbar-thumb { background: #253652; border-radius: 2px; }

.alert-feed-item {
  display: grid;
  grid-template-columns: 132px 64px 56px minmax(92px, 1fr);
  align-items: center;
  gap: 6px;
  padding: 5px 6px;
  border-radius: 2px;
  font-size: 11px;
  font-family: var(--font-mono);
  border-left: 2px solid transparent;
  margin-bottom: 2px;
  transition: background 0.2s;
  min-width: 0;
}

.alert-feed-item:hover { background: rgba(255,255,255,0.03); }
.alert-feed-item.risk-critical { border-left-color: #DC2626; }
.alert-feed-item.risk-danger   { border-left-color: #F97316; }
.alert-feed-item.risk-warning  { border-left-color: #F59E0B; }
.alert-feed-item.risk-low      { border-left-color: #22C55E; }

.feed-time { color: var(--color-text-muted); white-space: nowrap; }
.feed-level { font-weight: 600; font-size: 10px; white-space: nowrap; }
.feed-city { color: var(--color-text-secondary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.feed-amount { color: var(--color-text-primary); font-weight: 500; text-align: right; white-space: nowrap; }

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

/* ========== Trend Tags ========== */
.trend-tags { display: flex; gap: var(--space-3); }
.tag-tx { font-size: 10px; color: #3B82F6; }
.tag-al { font-size: 10px; color: #F97316; }

/* ========== Responsive ========== */
@media (max-width: 1400px) {
  .metric-strip { grid-template-columns: repeat(5, 1fr); }
  .main-dashboard-row { height: 390px; }
}
/* ===== Link Health ===== */
.health-list { display: flex; flex-direction: column; gap: 8px; }
.health-row { display: flex; align-items: center; gap: 10px; padding: 8px 12px; background: var(--color-bg-surface); border-radius: var(--radius-sm); }
.health-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.health-dot.ok { background: var(--color-success); box-shadow: 0 0 6px var(--color-success); }
.health-dot.fail { background: var(--color-danger); }
.health-label { flex: 1; font-size: var(--text-sm); color: var(--color-text-secondary); }
.health-val { font-size: var(--text-xs); font-weight: 600; }
.health-val.ok { color: var(--color-success); }
.health-val.fail { color: var(--color-danger); }
/* 压缩 radio-button 高度 */
.rule-tabs .el-radio-button__inner { padding: 2px 8px; font-size: 10px; height: 22px; line-height: 18px; }
.health-mini { margin-top: 6px; }
.health-mini-header { font-size: 9px; color: var(--color-text-muted); margin-bottom: 4px; }
.health-mini-row { display: flex; align-items: center; gap: 6px; padding: 1px 0; font-size: 10px; }
.health-mini-row .health-label { flex: 1; color: var(--color-text-secondary); font-size: 10px; }
.health-mini-row .health-val { font-size: 9px; font-weight: 600; }

/* === 左侧上下堆叠布局 === */
.col-stacked {
  display: grid !important;
  grid-template-rows: minmax(0, 1fr) minmax(0, 1fr);
  gap: 10px;
  min-height: 0;
}
.panel-stacked-item {
  display: grid !important;
  grid-template-rows: auto minmax(0, 1fr);
  height: auto;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}
.chart-gauge-stacked {
  width: 100%;
  height: auto;
  min-height: 0;
  overflow: hidden;
}
</style>
