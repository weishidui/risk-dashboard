<template>
  <div class="risk-map-page">
    <div class="page-header">
      <h2 class="page-title">风险地理分布</h2>
      <div class="header-right">
        <el-tag v-if="currentProvince" type="warning" size="small" closable @close="backToChina">
          {{ currentProvince }}
        </el-tag>
        <el-button v-if="currentProvince" size="mini" icon="el-icon-back" @click="backToChina">返回全国</el-button>
        <span class="subtitle">{{ currentProvince ? currentProvince + ' 各市' : '全国高危告警实时点位' }}</span>
        <el-button size="mini" icon="el-icon-refresh" :loading="refreshing" @click="manualRefresh">刷新</el-button>
        <el-button size="mini" icon="el-icon-back" @click="$router.push('/dashboard')">返回主页</el-button>
      </div>
    </div>

    <div class="map-card">
      <div v-if="!mapReady" class="map-loading">
        <i class="el-icon-loading"></i> 地图加载中...
      </div>
      <div ref="mapChart" class="map-container" v-show="mapReady"></div>
      <div v-if="mapReady" class="map-legend">
        <div class="legend-title">{{ currentProvince ? '市级' : '省级' }}告警</div>
        <div class="legend-row" v-for="lv in legendLevels" :key="lv.label">
          <span class="legend-swatch" :style="{ background: lv.color }"></span>
          <span class="legend-label">{{ lv.label }}</span>
        </div>
        <div style="margin-top:4px;border-top:1px solid rgba(255,255,255,0.1);padding-top:4px;">
          <div class="legend-row">
            <span class="legend-swatch" style="background:#F97316;"></span>
            <span class="legend-label">高危</span>
          </div>
          <div class="legend-row">
            <span class="legend-swatch" style="background:#DC2626;"></span>
            <span class="legend-label">极度危险</span>
          </div>
        </div>
      </div>
    </div>

    <el-row :gutter="10" style="margin-top:10px;">
      <el-col :span="12">
        <div class="panel">
          <div class="panel-header">
            <span class="panel-title">{{ currentProvince ? currentProvince + ' — ' : '' }}告警排行</span>
          </div>
          <el-table :data="cityRanks" size="mini" max-height="340">
            <el-table-column type="index" label="#" width="40" />
            <el-table-column prop="city" label="区域" />
            <el-table-column prop="count" label="告警数" sortable width="70">
              <template slot-scope="{ row }">
                <el-tag :type="row.count > 5 ? 'danger' : 'warning'" size="mini" effect="dark">{{ row.count }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>
      <el-col :span="12">
        <div class="panel">
          <div class="panel-header">
            <span class="panel-title">高危告警详情</span>
            <span class="card-count">共 {{ highRiskTotal }} 条</span>
          </div>
          <el-table :data="highRiskAlerts" size="mini" max-height="320">
            <el-table-column prop="alertLoc" label="城市" width="60" />
            <el-table-column prop="hitRules" label="触发规则" min-width="110" show-overflow-tooltip />
            <el-table-column prop="finalScore" label="评分" width="55" sortable>
              <template slot-scope="{ row }">
                <span :class="['mono-num', scoreClass(row.finalScore)]">{{ row.finalScore }}</span>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            small layout="prev, pager, next"
            :total="highRiskTotal" :page-size="20"
            :current-page.sync="highRiskPage"
            @current-change="loadHighRiskDetails"
            style="text-align:center;margin-top:6px;" />
          <div v-if="!highRiskAlerts.length" class="empty-tip">暂无高危告警</div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import * as echarts from 'echarts'
import { getCityRiskStat, getAlertList } from '@/api/alert'
import { REFRESH_INTERVAL, CITY_COORDS, PROVINCE_CENTERS } from '@/utils/constants'

function buildTooltip(p) {
  const d = p.data || {}
  let html = `${p.name || ''}`
  if (d.highCount > 0) html += `<br/><b style="color:#F97316;">高危</b>: ${d.highCount} 条`
  if (d.criticalCount > 0) html += `<br/><b style="color:#DC2626;">极度危险</b>: ${d.criticalCount} 条`
  return html
}

export default {
  name: 'RiskMap',
  data() {
    return {
      cityRanks: [],
      highRiskAlerts: [],
      highRiskTotal: 0,
      highRiskPage: 1,
      mapChart: null,
      mapReady: false,
      currentProvince: null,
      currentGbPrefix: null,
      provinceGeoJson: null,
      cityGeoJson: null,
      allAlertData: [],
      refreshing: false,
      mutex: 1,
      waitQ: [],
      dataMax: 0
    }
  },
  computed: {
    legendLevels() {
      const colors = this.currentProvince
        ? ['#E8C0C0', '#D07070', '#8B0000']
        : ['#3D1525', '#5A2035', '#7D2D4A', '#A53D5E', '#CD4D70']
      const n = colors.length
      const mx = Math.max(this.dataMax, 1)
      const step = Math.max(1, Math.ceil(mx / n))
      const levels = []
      for (let i = 0; i < n; i++) {
        const lo = i * step + 1
        const hi = (i + 1) * step
        if (lo > mx) break
        const label = hi >= mx ? `≥${lo}` : lo === hi ? `${lo}` : `${lo}-${hi}`
        levels.push({ label, color: colors[i] })
      }
      return levels
    }
  },
  mounted() {
    this.initMaps()
    this.timer = setInterval(() => this.loadData(), REFRESH_INTERVAL * 2)
    this._onTheme = () => { this._mapRendered = false; if (this.allAlertData.length) this.$nextTick(() => this.renderMap(this.allAlertData)) }
    document.addEventListener('theme-changed', this._onTheme)
  },
  beforeDestroy() {
    clearInterval(this.timer)
    if (this.mapChart) this.mapChart.dispose()
    document.removeEventListener('theme-changed', this._onTheme)
  },
  methods: {
    async initMaps() {
      try {
        const [provResp, cityResp] = await Promise.all([
          fetch('/map/china.json'),
          fetch('/map/city.json')
        ])
        this.provinceGeoJson = await provResp.json()
        this.cityGeoJson = await cityResp.json()
        echarts.registerMap('china', this.provinceGeoJson)
        this.mapReady = true
      } catch (e) {
        this.$message.error('地图数据加载失败')
      }
      this.$nextTick(() => this.loadData())
    },

    async loadData() {
      if (!this.mapReady) return
      try {
        const statRes = await getCityRiskStat(200)
        if (statRes.code === 200 && statRes.data) {
          const raw = statRes.data || []
          this.allAlertData = raw
          this.cityRanks = this.buildCityRanks(raw)
          await this.acquire()
          this.renderMap(raw)
          this.$nextTick(() => this.release())
        }
        this.loadHighRiskDetails()
      } catch (e) { this.release() }
    },

    acquire() {
      if (this.mutex === 1) { this.mutex = 0; return Promise.resolve() }
      return new Promise(r => this.waitQ.push(r))
    },

    release() {
      if (this.waitQ.length) { this.waitQ.shift()() }
      else { this.mutex = 1 }
    },

    buildCityRanks(raw) {
      if (this.currentProvince && this.currentGbPrefix) {
        const prefix = this.currentGbPrefix
        const cityCount = {}
        raw.forEach(d => {
          const gb = this.getCityGb(d.city)
          if (gb && String(gb).startsWith(prefix)) {
            const name = this.resolveGeoName(d.city) || d.city
            cityCount[name] = (cityCount[name] || 0) + (d.count || 1)
          }
        })
        return Object.entries(cityCount)
          .map(([city, count]) => ({ city, count }))
          .sort((a, b) => b.count - a.count)
      }
      const provCount = {}
      raw.forEach(d => {
        const prov = this.guessProvince(d.city) || d.city || '未知'
        provCount[prov] = (provCount[prov] || 0) + (d.count || 1)
      })
      return Object.entries(provCount)
        .map(([city, count]) => ({ city, count }))
        .sort((a, b) => b.count - a.count)
    },

    async manualRefresh() {
      this.refreshing = true
      try {
        const res = await getCityRiskStat(200)
        if (res.code === 200 && res.data) {
          this.allAlertData = res.data
          this._mapRendered = false
          await this.acquire()
          this.$nextTick(() => { this.renderMap(res.data); this.$nextTick(() => this.release()) })
        }
        this.loadHighRiskDetails()
        this.$message.success('已刷新')
      } catch (e) { this.release() }
      finally { this.refreshing = false }
    },

    async loadHighRiskDetails() {
      try {
        const res = await getAlertList({
          page: this.highRiskPage,
          pageSize: 20
        })
        if (res.code === 200 && res.data) {
          this.highRiskAlerts = (res.data.list || []).filter(
            a => a.riskLevel === '高危' || a.riskLevel === '极度危险'
          )
          this.highRiskTotal = this.highRiskAlerts.length
        }
      } catch { /* ignore */ }
    },

    scoreClass(s) {
      if (s > 120) return 'critical'; if (s >= 71) return 'danger'; if (s >= 41) return 'warning'; return 'success'
    },

    mapColors() {
      const s = getComputedStyle(document.documentElement)
      const g = (k) => s.getPropertyValue(k).trim()
      return {
        geoFill: g('--map-geo-fill'), geoBorder: g('--map-geo-border'),
        alertFill: g('--map-alert-fill'), alertBorder: g('--map-alert-border'),
        tooltipBg: g('--map-tooltip-bg'), tooltipText: g('--map-tooltip-text'),
        overlay: g('--map-overlay'),
        label: g('--color-text-secondary'), hoverFill: g('--color-bg-hover')
      }
    },

    renderMap(data) {
      const el = this.$refs.mapChart
      if (!el || !this.mapReady) return
      if (!this.mapChart) this.mapChart = echarts.init(el)
      const isRefresh = this._mapRendered
      this._mapRendered = true

      if (!this.currentProvince) {
        this.renderProvinceView(data, isRefresh)
      } else {
        this.renderCityView(data, isRefresh)
      }
    },

    riskPointColor(cr, hi, md, lo) {
      if (cr > 0) return '#DC2626'
      if (hi > 0) return '#F97316'
      if (md > 0) return '#F59E0B'
      return '#22C55E'
    },

    colorFor(cnt) {
      const levels = this.legendLevels
      if (!levels.length) return '#333'
      const n = this.currentProvince ? 3 : 5
      const step = Math.max(1, Math.ceil(Math.max(this.dataMax, 1) / n))
      const idx = Math.min(Math.floor((cnt - 1) / step), levels.length - 1)
      return levels[idx].color
    },

    renderProvinceView(data, isRefresh = false) {
      const m = this.mapColors()
      const provTotal = {}
      const provCritical = {}
      const provHigh = {}
      data.forEach(d => {
        const prov = this.guessProvince(d.city) || d.city || '未知'
        const lv = d.riskLevel
        if (lv === '极度危险' || lv === '高危') {
          provTotal[prov] = (provTotal[prov] || 0) + (d.count || 1)
          if (lv === '极度危险') provCritical[prov] = (provCritical[prov] || 0) + (d.count || 1)
          else provHigh[prov] = (provHigh[prov] || 0) + (d.count || 1)
        }
      })

      const provVals = Object.values(provTotal)
      this.dataMax = provVals.length ? Math.max(...provVals) : 0

      const regions = Object.entries(provTotal).map(([name, cnt]) => ({
        name,
        itemStyle: { areaColor: this.colorFor(cnt), borderColor: '#5A2828' },
        _cnt: cnt
      }))

      const allProvinces = new Set([...Object.keys(provCritical), ...Object.keys(provHigh)])
      const criticalPoints = []
      const highPoints = []
      ;[...allProvinces].forEach(name => {
        const cr = provCritical[name] || 0
        const hi = provHigh[name] || 0
        const pt = {
          name, value: [...this.getProvinceCenter(name), cr + hi],
          highCount: hi, criticalCount: cr
        }
        if (cr > 0) criticalPoints.push(pt)
        else highPoints.push(pt)
      })

      if (isRefresh) {
        this.mapChart.setOption({ animation: false, series: [
          { data: criticalPoints }, { data: highPoints }
        ] }, false)
        return
      }

      this.mapChart.setOption({
        geo: {
          map: 'china', roam: true, zoom: 1.2, center: [105, 36],
          itemStyle: { areaColor: m.geoFill, borderColor: m.geoBorder, borderWidth: 1 },
          emphasis: { itemStyle: { areaColor: m.hoverFill }, label: { color: m.tooltipText, show: true } },
          regions
        },
        series: [
          {
            name: '极度危险', type: 'effectScatter', coordinateSystem: 'geo',
            data: criticalPoints, symbolSize: 12,
            showEffectOn: 'render',
            rippleEffect: { brushType: 'stroke', scale: 4, period: 2.5 },
            itemStyle: { color: '#DC2626', shadowBlur: 12, shadowColor: '#DC2626' },
            tooltip: { formatter: p => buildTooltip(p) }, zlevel: 1
          },
          {
            name: '高危', type: 'effectScatter', coordinateSystem: 'geo',
            data: highPoints, symbolSize: 9,
            showEffectOn: 'render',
            rippleEffect: { brushType: 'stroke', scale: 3.5, period: 3 },
            itemStyle: { color: '#F97316', shadowBlur: 8, shadowColor: '#F97316' },
            tooltip: { formatter: p => buildTooltip(p) }, zlevel: 1
          }
        ]
      })
      this.mapChart.setOption({ tooltip: { trigger: 'item' } }, false)

      this.mapChart.off('click')
      this.mapChart.on('click', params => {
        if (params.name && params.name !== '境界线') this.drillDown(params.name)
      })
      this.bindLock()
    },

    renderCityView(data, isRefresh = false) {
      const m = this.mapColors()
      const prefix = this.currentGbPrefix
      const cityFeatures = this.cityGeoJson.features.filter(f => {
        return String(f.properties.gb || '').startsWith(prefix)
      })

      if (cityFeatures.length === 0) { this.backToChina(); return }

      const mapName = 'province-cities'
      echarts.registerMap(mapName, { type: 'FeatureCollection', features: cityFeatures })

      const bounds = this.computeBounds(cityFeatures)
      const center = [(bounds.minLng + bounds.maxLng) / 2, (bounds.minLat + bounds.maxLat) / 2]

      const cityTotal = {}
      const cityCritical = {}
      const cityHigh = {}
      data.forEach(d => {
        const city = d.city || '未知'
        const gb = this.getCityGb(city)
        if (gb && String(gb).startsWith(prefix)) {
          const geoName = this.resolveGeoName(city)
          const lv = d.riskLevel
          if (lv === '极度危险' || lv === '高危') {
            cityTotal[geoName] = (cityTotal[geoName] || 0) + (d.count || 1)
            if (lv === '极度危险') cityCritical[geoName] = (cityCritical[geoName] || 0) + (d.count || 1)
            else cityHigh[geoName] = (cityHigh[geoName] || 0) + (d.count || 1)
          }
        }
      })

      const cityVals = Object.values(cityTotal)
      this.dataMax = cityVals.length ? Math.max(...cityVals) : 0

      const regions = Object.entries(cityTotal).map(([name, cnt]) => ({
        name,
        itemStyle: { areaColor: this.colorFor(cnt), borderColor: '#5A2828' },
        _cnt: cnt
      }))

      const allCities = new Set([...Object.keys(cityCritical), ...Object.keys(cityHigh)])
      const criticalPoints = []
      const highPoints = []
      ;[...allCities].forEach(name => {
        const cr = cityCritical[name] || 0
        const hi = cityHigh[name] || 0
        const pt = {
          name, value: [...(this.getCityCenter(name) || this.getProvinceCenter(name)), cr + hi],
          highCount: hi, criticalCount: cr
        }
        if (cr > 0) criticalPoints.push(pt)
        else highPoints.push(pt)
      })

      if (isRefresh) {
        this.mapChart.setOption({ animation: false, series: [
          { data: criticalPoints }, { data: highPoints }
        ] }, false)
        return
      }

      this.mapChart.setOption({
        geo: {
          map: mapName, roam: true, zoom: 1.0, center,
          itemStyle: { areaColor: m.geoFill, borderColor: m.geoBorder, borderWidth: 1 },
          emphasis: { itemStyle: { areaColor: m.hoverFill }, label: { color: m.tooltipText, show: true } },
          regions
        },
        series: [
          {
            name: '极度危险', type: 'effectScatter', coordinateSystem: 'geo',
            data: criticalPoints, symbolSize: 12,
            showEffectOn: 'render',
            rippleEffect: { brushType: 'stroke', scale: 4, period: 2.5 },
            itemStyle: { color: '#DC2626', shadowBlur: 12, shadowColor: '#DC2626' },
            tooltip: { formatter: p => buildTooltip(p) }, zlevel: 1
          },
          {
            name: '高危', type: 'effectScatter', coordinateSystem: 'geo',
            data: highPoints, symbolSize: 9,
            showEffectOn: 'render',
            rippleEffect: { brushType: 'stroke', scale: 3.5, period: 3 },
            itemStyle: { color: '#F97316', shadowBlur: 8, shadowColor: '#F97316' },
            tooltip: { formatter: p => buildTooltip(p) }, zlevel: 1
          }
        ]
      })
      this.mapChart.setOption({ tooltip: { trigger: 'item' } }, false)

      this.mapChart.off('click')
      this.bindLock()
    },

    bindLock() {
      this.mapChart.off('mousedown')
      this.mapChart.on('mousedown', () => { this.mutex = 0 })
      this.mapChart.getDom().addEventListener('wheel', () => {
        this.mutex = 0
        clearTimeout(this._wheelTimer)
        this._wheelTimer = setTimeout(() => this.release(), 300)
      })
      const up = () => { this.release() }
      document.removeEventListener('mouseup', up)
      document.addEventListener('mouseup', up)
    },

    drillDown(provinceName) {
      this._mapRendered = false; this._switchView()
      if (!this.provinceGeoJson) return
      const prov = this.provinceGeoJson.features.find(f => f.properties.name === provinceName)
      if (!prov || !prov.properties.gb) return
      this.currentGbPrefix = String(prov.properties.gb).slice(0, 5)
      this.currentProvince = provinceName
      this.cityRanks = this.buildCityRanks(this.allAlertData)
      this.$nextTick(() => this.renderMap(this.allAlertData))
    },

    backToChina() {
      this._mapRendered = false; this._switchView()
      this.currentProvince = null; this.currentGbPrefix = null
      this.cityRanks = this.buildCityRanks(this.allAlertData)
      this.$nextTick(() => this.renderMap(this.allAlertData))
    },

    _switchView() {
      if (this.mapChart) { this.mapChart.dispose(); this.mapChart = null }
    },

    getProvinceCenter(name) {
      if (PROVINCE_CENTERS[name]) return PROVINCE_CENTERS[name]
      if (!this.provinceGeoJson) return [116, 36]
      const feat = this.provinceGeoJson.features.find(f => f.properties.name === name)
      if (!feat) return CITY_COORDS[name] || [116 + Math.random() * 5, 35 + Math.random() * 5]
      return this.computePolygonCenter(feat)
    },

    getCityCenter(name) {
      if (!this.cityGeoJson) return null
      const feat = this.findCityFeature(name)
      if (feat) return this.computePolygonCenter(feat)
      return CITY_COORDS[name] || null
    },

    resolveGeoName(name) {
      const feat = this.findCityFeature(name)
      return feat ? feat.properties.name : name
    },

    findCityFeature(name) {
      if (!this.cityGeoJson || !name) return null
      let f = this.cityGeoJson.features.find(x => x.properties.name === name)
      if (f) return f
      f = this.cityGeoJson.features.find(x => x.properties.name === name + '市')
      if (f) return f
      f = this.cityGeoJson.features.find(x => x.properties.name === name + '特别行政区')
      if (f) return f
      if (name.endsWith('市')) {
        f = this.cityGeoJson.features.find(x => x.properties.name === name.slice(0, -1))
        if (f) return f
      }
      return null
    },

    getCityGb(name) {
      const feat = this.findCityFeature(name)
      return feat ? feat.properties.gb : null
    },

    guessProvince(cityName) {
      const gb = this.getCityGb(cityName)
      if (gb && this.provinceGeoJson) {
        const prefix = String(gb).slice(0, 5)
        const prov = this.provinceGeoJson.features.find(f => String(f.properties.gb || '').startsWith(prefix))
        if (prov) return prov.properties.name
      }
      if (['北京', '上海', '天津', '重庆'].includes(cityName)) return cityName + '市'
      if (cityName === '香港') return '香港特别行政区'
      if (cityName === '澳门') return '澳门特别行政区'
      return cityName
    },

    computePolygonCenter(feat) {
      if (!feat || !feat.geometry) return [116, 36]
      try {
        const geom = feat.geometry
        let outerRings = []
        if (geom.type === 'Polygon') outerRings = [geom.coordinates[0]]
        else if (geom.type === 'MultiPolygon') outerRings = geom.coordinates.map(poly => poly[0])
        else return [116, 36]
        let bestRing = null, bestArea = -1
        for (const ring of outerRings) {
          if (!ring || ring.length < 3) continue
          let area = 0
          for (let i = 0; i < ring.length - 1; i++) {
            area += ring[i][0] * ring[i + 1][1] - ring[i + 1][0] * ring[i][1]
          }
          area = Math.abs(area)
          if (area > bestArea) { bestArea = area; bestRing = ring }
        }
        if (!bestRing) return [116, 36]
        const lngs = bestRing.map(c => c[0]), lats = bestRing.map(c => c[1])
        return [(Math.min(...lngs) + Math.max(...lngs)) / 2, (Math.min(...lats) + Math.max(...lats)) / 2]
      } catch { return [116, 36] }
    },

    computeBounds(features) {
      let minLng = 180, maxLng = -180, minLat = 90, maxLat = -90
      features.forEach(f => {
        if (!f.geometry) return
        const coords = f.geometry.type === 'MultiPolygon' ? f.geometry.coordinates[0][0] : f.geometry.coordinates[0]
        if (!coords) return
        coords.forEach(c => {
          if (c[0] < minLng) minLng = c[0]
          if (c[0] > maxLng) maxLng = c[0]
          if (c[1] < minLat) minLat = c[1]
          if (c[1] > maxLat) maxLat = c[1]
        })
      })
      return { minLng, maxLng, minLat, maxLat }
    }
  }
}
</script>

<style scoped>
.risk-map-page { height: 100%; overflow-y: auto; }

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-3);
  padding-bottom: var(--space-3);
  border-bottom: 1px solid var(--color-border);
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
  gap: var(--space-2);
}

.subtitle {
  color: var(--color-text-muted);
  font-size: var(--text-xs);
}

.map-card {
  background: var(--color-bg-elevated);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  position: relative;
  overflow: hidden;
}

.map-container {
  width: 100%;
  height: 460px;
}

.map-legend {
  position: absolute;
  bottom: 8px;
  left: 8px;
  background: rgba(0,0,0,0.55);
  border-radius: 2px;
  padding: 6px 8px;
  z-index: 5;
  pointer-events: none;
}

.legend-title {
  font-size: 10px;
  color: #9AACBF;
  margin-bottom: 3px;
  font-weight: 500;
}

.legend-row {
  display: flex;
  align-items: center;
  gap: 5px;
  margin: 1px 0;
}

.legend-swatch {
  width: 16px;
  height: 10px;
  border-radius: 1px;
  flex-shrink: 0;
}

.legend-label {
  font-size: 9px;
  color: #8899AA;
}

.map-loading {
  height: 460px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-muted);
  font-size: var(--text-sm);
  gap: var(--space-2);
}

.panel {
  background: var(--color-bg-elevated);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: var(--space-3);
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

.card-count {
  font-size: var(--text-xs);
  color: var(--color-text-muted);
}

.empty-tip {
  color: var(--color-text-muted);
  text-align: center;
  padding: 30px 0;
  font-size: var(--text-sm);
}

.text-success { color: var(--color-success); font-size: 11px; }
.mono-num { font-family: var(--font-mono); font-size: var(--text-sm); font-weight: 600; }
.mono-num.critical { color: var(--color-critical); }
.mono-num.danger   { color: var(--color-danger); }
.mono-num.warning  { color: var(--color-warning); }
.mono-num.success  { color: var(--color-success); }
</style>
