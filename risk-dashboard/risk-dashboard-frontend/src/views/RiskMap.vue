<template>
  <div class="risk-map-page">
    <div class="page-header">
      <div>
        <h2 class="page-title">风险地理分布</h2>
        <span class="subtitle">{{ currentProvince ? currentProvince + ' 城市高危告警实时点位' : '全国城市高危告警实时点位' }}</span>
      </div>
      <div class="header-right">
        <el-tag v-if="currentProvince" type="warning" size="small" closable @close="backToChina">{{ currentProvince }}</el-tag>
        <el-button v-if="currentProvince" size="mini" icon="el-icon-back" @click="backToChina">返回全国</el-button>
        <el-button size="mini" icon="el-icon-refresh" :loading="refreshing" @click="manualRefresh">刷新</el-button>
        <el-button size="mini" icon="el-icon-back" @click="$router.push('/dashboard')">返回主页</el-button>
      </div>
    </div>

    <div class="map-card">
      <div v-if="!mapReady" class="map-loading"><i class="el-icon-loading"></i> 地图加载中...</div>
      <div ref="mapChart" class="map-container" v-show="mapReady"></div>
      <div v-if="mapReady" :class="['map-legend', { 'is-light': themeMode === 'light' }]">
        <div class="legend-title">近5分钟{{ currentProvince ? '城市' : '省份' }}告警数</div>
        <div class="legend-row" v-for="item in legendLevels" :key="item.label">
          <span class="legend-swatch" :style="{ background: item.color }"></span>
          <span class="legend-label">{{ item.label }}</span>
        </div>
        <div class="legend-divider"></div>
        <div class="legend-row"><span class="legend-dot" :style="pointLegendStyle(mapTheme.highPoint)"></span><span class="legend-label">高危城市点</span></div>
        <div class="legend-row"><span class="legend-dot" :style="pointLegendStyle(mapTheme.criticalPoint)"></span><span class="legend-label">极度危险城市点</span></div>
      </div>
    </div>

    <el-row :gutter="10" class="detail-row">
      <el-col :span="12">
        <div class="panel">
          <div class="panel-header"><span class="panel-title">近5分钟{{ currentProvince ? '城市' : '省份' }}告警排行</span></div>
          <el-table :data="cityRanks" size="mini" max-height="340" empty-text="暂无高危告警">
            <el-table-column type="index" label="#" width="40" />
            <el-table-column prop="rankName" :label="currentProvince ? '城市' : '省份'" />
            <el-table-column v-if="currentProvince" prop="province" label="省份" width="120" show-overflow-tooltip />
            <el-table-column prop="count" label="告警数" sortable width="76">
              <template slot-scope="{ row }"><el-tag :type="row.criticalCount ? 'danger' : 'warning'" size="mini" effect="dark">{{ row.count }}</el-tag></template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>
      <el-col :span="12">
        <div class="panel">
          <div class="panel-header"><span class="panel-title">近5分钟高危告警详情</span><span class="card-count">共 {{ highRiskTotal }} 条</span></div>
          <el-table :data="pagedHighRiskAlerts" size="mini" max-height="320" empty-text="暂无高危告警">
            <el-table-column prop="displayCity" label="城市" width="84" show-overflow-tooltip />
            <el-table-column prop="hitRules" label="触发规则" min-width="132" show-overflow-tooltip />
            <el-table-column prop="riskLevel" label="等级" width="72"><template slot-scope="{ row }"><span :class="['risk-level', riskClass(row.riskLevel)]">{{ row.riskLevel }}</span></template></el-table-column>
            <el-table-column prop="finalScore" label="评分" width="58" sortable><template slot-scope="{ row }"><span :class="['mono-num', scoreClass(row.finalScore)]">{{ row.finalScore }}</span></template></el-table-column>
            <el-table-column prop="displayTime" label="时间" width="132" />
          </el-table>
          <el-pagination v-if="highRiskTotal > pageSize" small layout="prev, pager, next" :total="highRiskTotal" :page-size="pageSize" :current-page.sync="highRiskPage" style="text-align:center;margin-top:6px;" />
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import * as echarts from 'echarts'
import { getDashboardData } from '@/api/metrics'
import { REFRESH_INTERVAL } from '@/utils/constants'

const EVENT_TTL = 20 * 1000
const PULSE_TTL = 2 * 1000
const AGG_WINDOW = 5 * 60 * 1000
const MAINLAND_MAP_NAME = 'china-risk-map-detail'
const MAINLAND_MIN_LATITUDE = 18
const PROVINCE_LEVELS = [
  { label: '1-2', color: '#5A2035' },
  { label: '3-4', color: '#A53D5E' },
  { label: '>=5', color: '#CD4D70' }
]

function cityKeys(name) {
  const value = String(name || '').trim().replace(/\s+/g, '')
  if (!value) return []
  const keys = new Set([value])
  if (value.endsWith('市')) keys.add(value.slice(0, -1))
  else keys.add(value + '市')
  if (value.endsWith('特别行政区')) keys.add(value.slice(0, -5))
  return [...keys]
}

function parseTime(value) {
  if (!value) return null
  if (typeof value === 'number') return value < 1000000000000 ? value * 1000 : value
  const parsed = new Date(String(value).replace(/-/g, '/')).getTime()
  return Number.isNaN(parsed) ? null : parsed
}

export default {
  name: 'RiskMap',
  data() {
    return {
      cityRanks: [],
      highRiskAll: [],
      highRiskPage: 1,
      pageSize: 20,
      mapChart: null,
      mapReady: false,
      currentProvince: null,
      currentGbPrefix: null,
      provinceGeoJson: null,
      cityGeoJson: null,
      cityCoordinateIndex: {},
      latestOverview: null,
      refreshing: false,
      dataMax: 0,
      mapConfigured: false,
      lastRegionSignature: '',
      isRoaming: false,
      deferredMapRender: false,
      roamEndTimer: null,
      mapClock: Date.now(),
      refreshTimer: null,
      animationTimer: null,
      themeMode: localStorage.getItem('rd-mode') || 'dark'
    }
  },
  computed: {
    highRiskTotal() { return this.highRiskAll.length },
    pagedHighRiskAlerts() {
      const start = (this.highRiskPage - 1) * this.pageSize
      return this.highRiskAll.slice(start, start + this.pageSize)
    },
    legendLevels() {
      return this.mapTheme.levels
    },
    mapTheme() {
      if (this.themeMode === 'light') {
        return {
          levels: [
            { label: '1-2', color: '#F8D7E1' },
            { label: '3-4', color: '#EC8FA8' },
            { label: '>=5', color: '#CF3F65' }
          ],
          geoFill: '#EAF1FA', geoBorder: '#6E91BD', emphasisFill: '#D5E5F7', emphasisBorder: '#2563A8',
          geoShadow: 'rgba(38, 83, 137, 0.16)', tooltipBg: '#FFFFFF', tooltipBorder: '#9CB5D1', tooltipText: '#1F3147',
          regionBorder: '#B14A68', pointBorder: '#FFFFFF', highPoint: '#D97706', criticalPoint: '#DC2626'
        }
      }
      return {
        levels: PROVINCE_LEVELS,
        geoFill: '#09152B', geoBorder: '#32588C', emphasisFill: '#214685', emphasisBorder: '#5FA1FF',
        geoShadow: 'rgba(5,14,31,0.95)', tooltipBg: '#0F1A2C', tooltipBorder: '#32588C', tooltipText: '#D8DFE8',
        regionBorder: '#5A2828', pointBorder: '#FFF7D6', highPoint: '#FF9500', criticalPoint: '#FF3B30'
      }
    }
  },
  watch: {
    '$store.state.liveAlerts': {
      handler() { this.refreshRealtimeView() },
      deep: true
    }
  },
  mounted() {
    this.initMaps()
    this.refreshTimer = setInterval(() => this.loadData(), REFRESH_INTERVAL)
    this.animationTimer = setInterval(() => {
      this.mapClock = Date.now()
      this.refreshRealtimeView()
    }, 1000)
    document.addEventListener('theme-changed', this.handleThemeChange)
  },
  beforeDestroy() {
    clearInterval(this.refreshTimer)
    clearInterval(this.animationTimer)
    clearTimeout(this.roamEndTimer)
    document.removeEventListener('theme-changed', this.handleThemeChange)
    if (this.mapChart) this.mapChart.dispose()
  },
  methods: {
    async initMaps() {
      try {
        const [mapResponse, csvResponse, cityResponse] = await Promise.all([
          fetch('/map/china.json'),
          fetch('/map/China_cities.csv'),
          fetch('/map/city.json')
        ])
        const rawProvinceGeoJson = await mapResponse.json()
        this.provinceGeoJson = this.withoutSouthChinaSeaInset(rawProvinceGeoJson)
        this.cityCoordinateIndex = this.parseCityCsv(await csvResponse.arrayBuffer())
        this.cityGeoJson = await cityResponse.json()
        echarts.registerMap(MAINLAND_MAP_NAME, this.provinceGeoJson)
        this.mapReady = true
        await this.loadData()
      } catch (error) {
        this.$message.error('地图或城市坐标数据加载失败')
      }
    },

    // ── GeoJSON 过滤：去掉南海诸岛小窗、去掉非大陆要素 ──
    withoutSouthChinaSeaInset(geoJson) {
      if (!geoJson?.features) return geoJson
      return {
        ...geoJson,
        features: geoJson.features
          .filter(feature => !this.isSouthChinaSeaInset(feature) && this.isMainlandFeature(feature))
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
        geometry: { ...feature.geometry, coordinates: mainIsland ? [mainIsland] : feature.geometry.coordinates }
      }
    },

    isSouthChinaSeaInset(feature) {
      return feature?.properties?.name === '境界线'
    },

    isMainlandFeature(feature) {
      const bounds = this.coordinateBounds(feature?.geometry?.coordinates)
      return bounds && bounds.maxLat >= MAINLAND_MIN_LATITUDE
    },

    coordinateBounds(coordinates) {
      const bounds = { minLng: Infinity, maxLng: -Infinity, minLat: Infinity, maxLat: -Infinity }
      const walk = value => {
        if (!Array.isArray(value)) return
        if (typeof value[0] === 'number' && typeof value[1] === 'number') {
          bounds.minLng = Math.min(bounds.minLng, value[0]); bounds.maxLng = Math.max(bounds.maxLng, value[0])
          bounds.minLat = Math.min(bounds.minLat, value[1]); bounds.maxLat = Math.max(bounds.maxLat, value[1])
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

    parseCityCsv(buffer) {
      const decode = encoding => new TextDecoder(encoding).decode(buffer)
      let text = decode('utf-8')
      if (!text.includes('城市')) text = decode('gb18030')
      const index = {}
      text.split(/\r?\n/).slice(1).forEach(line => {
        const values = line.split(',').map(value => value.trim().replace(/^"|"$/g, ''))
        if (values.length < 4) return
        const latitude = Number(values[2])
        const longitude = Number(values[3])
        if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return
        const record = { province: values[0], city: values[1], coordinate: [longitude, latitude] }
        cityKeys(values[1]).forEach(key => { index[key] = record })
      })
      return index
    },

    async loadData() {
      if (!this.mapReady) return
      try {
        const response = await getDashboardData()
        if (response.code === 200 && response.data) {
          this.latestOverview = response.data
          this.refreshRealtimeView()
        }
      } catch (error) { /* keep the last successful map state */ }
    },

    async manualRefresh() {
      this.refreshing = true
      await this.loadData()
      this.refreshing = false
    },

    // ═══ 核心：实时视图刷新 ═══
    refreshRealtimeView() {
      if (!this.mapReady) return
      const alerts = this.currentProvince
        ? this.collectRealtimeAlerts().filter(alert => this.provinceForCity(this.alertCity(alert)) === this.currentProvince)
        : this.collectRealtimeAlerts()
      const aggregatePoints = this.buildCityPoints(alerts, AGG_WINDOW, false)
      if (this.currentProvince) {
        this.cityRanks = aggregatePoints
          .map(point => ({ rankName: point.city, province: point.province, count: point.count, criticalCount: point.criticalCount }))
          .sort((a, b) => b.count - a.count || b.criticalCount - a.criticalCount)
      } else {
        const provinceRanks = new Map()
        aggregatePoints.forEach(point => {
          const current = provinceRanks.get(point.province) || { rankName: point.province, count: 0, criticalCount: 0 }
          current.count += point.count
          current.criticalCount += point.criticalCount
          provinceRanks.set(point.province, current)
        })
        this.cityRanks = [...provinceRanks.values()]
          .sort((a, b) => b.count - a.count || b.criticalCount - a.criticalCount)
      }
      this.highRiskAll = alerts
        .filter(alert => this.alertAge(alert) <= AGG_WINDOW)
        .sort((a, b) => this.alertTimestamp(b) - this.alertTimestamp(a))
        .map(alert => ({ ...alert, displayCity: this.cityRecord(this.alertCity(alert))?.city || this.alertCity(alert), displayTime: this.formatTime(this.alertTimestamp(alert)) }))
      const maxPage = Math.max(1, Math.ceil(this.highRiskAll.length / this.pageSize))
      if (this.highRiskPage > maxPage) this.highRiskPage = maxPage
      // 用户正在拖拽/缩放地图 → 暂缓渲染，等操作结束后再补
      if (this.isRoaming) {
        this.deferredMapRender = true
        return
      }
      this.renderMap(alerts, aggregatePoints)
    },

    collectRealtimeAlerts() {
      const seen = new Set()
      const candidates = [
        ...(this.$store.state.liveAlerts || []),
        ...(this.latestOverview?.recentAlerts || [])
      ]
      return candidates.filter(alert => {
        if (!this.isSevereAlert(alert)) return false
        const key = alert.alertId || alert.transId || [this.alertCity(alert), this.alertTimestamp(alert), alert.finalScore].join('|')
        if (seen.has(key)) return false
        seen.add(key)
        return this.cityRecord(this.alertCity(alert)) && this.alertAge(alert) <= AGG_WINDOW
      })
    },

    isSevereAlert(alert) {
      const level = String(alert?.riskLevel || '').trim()
      return level === '高危' || level === '极度危险' || Number(alert?.finalScore || 0) >= 80
    },

    alertCity(alert) {
      return String(alert?.alertLoc || alert?.city || '').trim()
    },

    alertTimestamp(alert) {
      return parseTime(alert?.createTime || alert?.alertTime || alert?.timestamp || alert?.eventTime || alert?.time) || Date.now()
    },

    alertAge(alert) {
      return Math.max(0, this.mapClock - this.alertTimestamp(alert))
    },

    cityRecord(city) {
      for (const key of cityKeys(city)) {
        if (this.cityCoordinateIndex[key]) return this.cityCoordinateIndex[key]
      }
      return null
    },

    findProvinceFeature(name) {
      if (!this.provinceGeoJson?.features || !name) return null
      const value = String(name).trim()
      return this.provinceGeoJson.features.find(feature => feature.properties?.name === value) ||
        this.provinceGeoJson.features.find(feature => this.stripLocationSuffix(feature.properties?.name) === this.stripLocationSuffix(value)) || null
    },

    findCityFeature(name) {
      if (!this.cityGeoJson?.features || !name) return null
      for (const key of cityKeys(name)) {
        const feature = this.cityGeoJson.features.find(item => item.properties?.name === key)
        if (feature) return feature
      }
      return null
    },

    provinceForCity(city) {
      const cityFeature = this.findCityFeature(city)
      const cityGb = String(cityFeature?.properties?.gb || '')
      if (cityGb) {
        const prefix = cityGb.slice(0, 5)
        const province = this.provinceGeoJson?.features.find(feature => String(feature.properties?.gb || '').startsWith(prefix))
        if (province) return province.properties.name
      }
      const record = this.cityRecord(city)
      return this.findProvinceFeature(record?.province)?.properties?.name || record?.province || null
    },

    stripLocationSuffix(name) {
      return String(name || '').trim().replace(/(特别行政区|壮族自治区|回族自治区|维吾尔自治区|自治区|自治州|地区|新区|林区|盟|省|市)$/g, '')
    },

    currentCityFeatures() {
      if (!this.currentGbPrefix || !this.cityGeoJson?.features) return []
      return this.cityGeoJson.features.filter(feature => String(feature.properties?.gb || '').startsWith(this.currentGbPrefix))
    },

    drillDown(provinceName) {
      const province = this.findProvinceFeature(provinceName)
      const prefix = String(province?.properties?.gb || '').slice(0, 5)
      const cityFeatures = this.cityGeoJson?.features.filter(feature => String(feature.properties?.gb || '').startsWith(prefix)) || []
      if (!province || !prefix || cityFeatures.length === 0) return
      this.currentProvince = province.properties.name
      this.currentGbPrefix = prefix
      this.resetMapView()
      this.refreshRealtimeView()
    },

    backToChina() {
      if (!this.currentProvince) return
      this.currentProvince = null
      this.currentGbPrefix = null
      this.resetMapView()
      this.refreshRealtimeView()
    },

    resetMapView() {
      if (this.mapChart) this.mapChart.dispose()
      this.mapChart = null
      this.mapConfigured = false
      this.lastRegionSignature = ''
      this.isRoaming = false
      this.deferredMapRender = false
      clearTimeout(this.roamEndTimer)
    },

    buildCityPoints(alerts, windowMs, isLiveLayer) {
      const groups = new Map()
      alerts.forEach(alert => {
        const record = this.cityRecord(this.alertCity(alert))
        const age = this.alertAge(alert)
        if (!record || age > windowMs) return
        const key = record.city
        const level = String(alert.riskLevel || '').trim() === '极度危险' || Number(alert.finalScore || 0) > 120 ? '极度危险' : '高危'
        const current = groups.get(key) || {
          city: record.city, province: this.provinceForCity(record.city), coordinate: record.coordinate,
          count: 0, highCount: 0, criticalCount: 0, riskLevel: level, latestTime: 0, latestAlert: alert
        }
        current.count += 1
        if (level === '极度危险') current.criticalCount += 1
        else current.highCount += 1
        if (level === '极度危险') current.riskLevel = '极度危险'
        const timestamp = this.alertTimestamp(alert)
        if (timestamp >= current.latestTime) {
          current.latestTime = timestamp
          current.latestAlert = alert
        }
        groups.set(key, current)
      })

      return [...groups.values()].sort((a, b) => a.city.localeCompare(b.city, 'zh-CN')).map(group => {
        const age = Math.max(0, this.mapClock - group.latestTime)
        const fade = isLiveLayer ? Math.max(0.08, 1 - age / EVENT_TTL) : 0.42
        const color = group.riskLevel === '极度危险' ? this.mapTheme.criticalPoint : this.mapTheme.highPoint
        return {
          ...group,
          id: `city:${group.city}`,
          name: group.city,
          age,
          value: [...group.coordinate, group.count],
          symbolSize: isLiveLayer ? Math.min(26, 13 + group.count * 2) : Math.min(22, 9 + Math.sqrt(group.count) * 4),
          itemStyle: { color: this.hexToRgba(color, fade), opacity: fade },
          latestTimeText: this.formatTime(group.latestTime)
        }
      })
    },

    // ═══ 地图渲染 ═══
    renderMap(alerts, aggregatePoints) {
      const el = this.$refs.mapChart
      if (!el || !this.mapReady) return
      if (!this.mapChart) this.mapChart = echarts.init(el)
      const theme = this.mapTheme
      const livePoints = this.buildCityPoints(alerts, EVENT_TTL, true)
      const pulsePoints = livePoints.filter(point => point.age <= PULSE_TTL)
      const provinceCounts = {}
      const cityCounts = {}
      aggregatePoints.forEach(point => {
        provinceCounts[point.province] = (provinceCounts[point.province] || 0) + point.count
        cityCounts[point.city] = point.count
      })
      const viewCounts = this.currentProvince ? cityCounts : provinceCounts
      const values = Object.values(viewCounts)
      this.dataMax = values.length ? Math.max(...values) : 0
      const regions = Object.entries(viewCounts).map(([name, count]) => ({
        name,
        itemStyle: { areaColor: this.provinceColor(count), borderColor: theme.regionBorder }
      }))
      // 仅当区域颜色真正变化时才更新 geo.regions，避免不必要的地图重绘
      const regionSignature = Object.entries(viewCounts)
        .sort(([left], [right]) => left.localeCompare(right, 'zh-CN'))
        .map(([name, count]) => `${name}:${count}`)
        .join('|')
      const cityFeatures = this.currentCityFeatures()
      const mapName = this.currentProvince ? `risk-map-province-${this.currentGbPrefix}` : MAINLAND_MAP_NAME
      if (this.currentProvince && cityFeatures.length && !this.mapConfigured) {
        echarts.registerMap(mapName, { type: 'FeatureCollection', features: cityFeatures })
      }

      const mapOption = {
        tooltip: {
          trigger: 'item', backgroundColor: theme.tooltipBg, borderColor: theme.tooltipBorder, textStyle: { color: theme.tooltipText, fontSize: 11 },
          formatter: params => {
            const point = params.data
            if (!point?.city) return params.name
            const riskColor = point.riskLevel === '极度危险' ? theme.criticalPoint : theme.highPoint
            return `<b>${point.city}</b><br/>${point.province}<br/>风险等级: <span style="color:${riskColor}">${point.riskLevel}</span><br/>近5分钟告警: ${point.count} 条<br/>最新时间: ${point.latestTimeText}`
          }
        },
        geo: {
          map: mapName, roam: true, triggerEvent: true,
          ...(this.currentProvince ? {} : { layoutCenter: ['50%', '50%'], layoutSize: '116%', aspectScale: 0.82 }),
          itemStyle: { areaColor: theme.geoFill, borderColor: theme.geoBorder, borderWidth: 1.2, shadowColor: theme.geoShadow, shadowBlur: this.themeMode === 'light' ? 5 : 15, shadowOffsetX: this.themeMode === 'light' ? 0 : -4, shadowOffsetY: this.themeMode === 'light' ? 2 : 7 },
          emphasis: { label: { show: true, color: this.themeMode === 'light' ? '#1F3147' : '#FFFFFF', fontSize: 11 }, itemStyle: { areaColor: theme.emphasisFill, borderColor: theme.emphasisBorder, borderWidth: 1.5 } },
          regions
        },
        series: [
          {
            name: '近5分钟城市风险', type: 'scatter', coordinateSystem: 'geo', data: aggregatePoints,
            symbolSize: (value, params) => params.data.symbolSize,
            animation: false, animationDurationUpdate: 0,
            itemStyle: { color: params => params.data.itemStyle.color, opacity: params => params.data.itemStyle.opacity, shadowBlur: 10, shadowColor: params => params.data.itemStyle.color }, zlevel: 1
          },
          {
            name: '20秒实时城市风险', type: 'scatter', coordinateSystem: 'geo', data: livePoints,
            symbolSize: (value, params) => params.data.symbolSize,
            animation: false, animationDurationUpdate: 0,
            itemStyle: { color: params => params.data.itemStyle.color, opacity: params => params.data.itemStyle.opacity, borderColor: theme.pointBorder, borderWidth: 2, shadowBlur: 38, shadowColor: params => params.data.itemStyle.color }, zlevel: 2
          },
          {
            name: '新到达风险提示', type: 'effectScatter', coordinateSystem: 'geo', data: pulsePoints,
            symbolSize: (value, params) => params.data.symbolSize, showEffectOn: 'render', rippleEffect: { brushType: 'stroke', scale: 6.2, period: 1.5 },
            itemStyle: { color: params => params.data.itemStyle.color, opacity: params => params.data.itemStyle.opacity, borderColor: theme.pointBorder, borderWidth: 2, shadowBlur: 38, shadowColor: params => params.data.itemStyle.color }, zlevel: 3
          }
        ]
      }

      const isRefresh = this.mapConfigured

      if (isRefresh) {
        // ═══ 刷新路径：只合并 series，不触碰 geo → 保留 zoom/center ═══
        // 三要素：notMerge=false + animation=false + 只传 series
        const update = { animation: false }
        if (regionSignature !== this.lastRegionSignature) {
          // region 颜色变了才传 geo.regions，notMerge=false 不会重置 zoom/center
          update.geo = { regions }
          this.lastRegionSignature = regionSignature
        }
        update.series = [
          { data: aggregatePoints },
          { data: livePoints },
          { data: pulsePoints }
        ]
        this.mapChart.setOption(update, false)
        return
      }

      // ═══ 首次渲染路径：完整初始化 geo 组件 ═══
      this.mapChart.setOption(mapOption, true)
      this.mapConfigured = true
      this.lastRegionSignature = regionSignature

      this.bindMapInteractions()
    },

    // ═══ 地图交互绑定：click 下钻 + georoam 防抖 ═══
    bindMapInteractions() {
      this.mapChart.off('click')
      this.mapChart.on('click', params => {
        if (this.currentProvince) return
        const province = params.componentType === 'geo' ? params.name : params.data?.province
        if (province && province !== '境界线') this.drillDown(province)
      })
      this.mapChart.off('georoam')
      this.mapChart.on('georoam', () => {
        this.isRoaming = true
        clearTimeout(this.roamEndTimer)
        this.roamEndTimer = setTimeout(() => {
          this.isRoaming = false
          if (this.deferredMapRender) {
            this.deferredMapRender = false
            this.refreshRealtimeView()
          }
        }, 180)
      })
    },

    provinceColor(count) {
      if (!count || !this.dataMax) return this.mapTheme.geoFill
      if (count >= 5) return this.mapTheme.levels[2].color
      if (count >= 3) return this.mapTheme.levels[1].color
      return this.mapTheme.levels[0].color
    },

    // ═══ 主题切换 ═══
    handleThemeChange() {
      this.themeMode = localStorage.getItem('rd-mode') || 'dark'
      this.mapConfigured = false
      this.lastRegionSignature = ''
      this.$nextTick(() => this.refreshRealtimeView())
    },

    pointLegendStyle(color) { return { background: color, boxShadow: `0 0 7px ${color}` } },

    riskClass(level) { return level === '极度危险' ? 'critical' : 'danger' },
    scoreClass(score) { return score > 120 ? 'critical' : score >= 71 ? 'danger' : 'warning' },
    formatTime(value) {
      if (!value) return '--'
      const date = new Date(value)
      return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}:${String(date.getSeconds()).padStart(2, '0')}`
    },
    hexToRgba(hex, alpha) {
      const value = hex.replace('#', '')
      const red = parseInt(value.slice(0, 2), 16)
      const green = parseInt(value.slice(2, 4), 16)
      const blue = parseInt(value.slice(4, 6), 16)
      return `rgba(${red},${green},${blue},${alpha})`
    }
  }
}
</script>

<style scoped>
.risk-map-page { height: 100%; overflow-y: auto; }
.page-header { min-height: 42px; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid var(--color-border); padding: 0 0 8px; }
.page-title { margin: 0; color: var(--color-text-primary); font-size: 16px; font-weight: 600; }
.subtitle { display: inline-block; color: var(--color-text-muted); font-size: 11px; margin-top: 3px; }
.header-right { display: flex; gap: 8px; align-items: center; }
.map-card { height: 520px; min-height: 420px; position: relative; margin-top: 10px; border: 1px solid var(--color-border); background: var(--color-panel-bg); overflow: hidden; }
.map-container { width: 100%; height: 100%; }
.map-loading { height: 100%; display: flex; align-items: center; justify-content: center; color: var(--color-text-muted); font-size: 13px; }
.map-legend { position: absolute; left: 10px; bottom: 10px; width: 138px; padding: 8px; background: rgba(8, 20, 39, 0.92); border: 1px solid #263B5D; }
.map-legend.is-light { background: rgba(255, 255, 255, 0.96); border-color: #B9CCE1; box-shadow: 0 3px 12px rgba(38, 83, 137, 0.12); }
.legend-title { color: #D8DFE8; font-size: 11px; margin-bottom: 5px; }
.map-legend.is-light .legend-title { color: #1F3147; }
.legend-row { height: 18px; display: flex; align-items: center; gap: 6px; }
.legend-swatch { width: 14px; height: 11px; display: inline-block; }
.legend-dot { width: 9px; height: 9px; border-radius: 50%; display: inline-block; }
.legend-label { color: #A8B7CC; font-size: 10px; }
.map-legend.is-light .legend-label { color: #526981; }
.legend-divider { border-top: 1px solid rgba(255,255,255,0.12); margin: 5px 0; }
.map-legend.is-light .legend-divider { border-top-color: #D9E4F0; }
.detail-row { margin-top: 10px; }
.panel { border: 1px solid var(--color-border); background: var(--color-panel-bg); min-height: 250px; }
.panel-header { height: 38px; padding: 0 10px; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid var(--color-border); }
.panel-title { color: var(--color-text-primary); font-size: 13px; font-weight: 600; }
.card-count { color: var(--color-text-muted); font-size: 11px; }
.risk-level { font-weight: 600; font-size: 11px; }
.risk-level.critical, .mono-num.critical { color: #FF3B30; }
.risk-level.danger, .mono-num.danger { color: #FF9500; }
.mono-num { font-family: monospace; font-weight: 700; }
@media (max-width: 900px) { .map-card { height: 430px; } .header-right { gap: 4px; } }
</style>

