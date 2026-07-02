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
      </div>
    </div>

    <div class="map-card">
      <div v-if="!mapReady" class="map-loading">
        <i class="el-icon-loading"></i> 地图加载中...
      </div>
      <div ref="mapChart" class="map-container" :class="{ 'no-interact': !mapInteractive || mapLocked }" v-show="mapReady"></div>
      <div v-if="mapReady && !mapInteractive" class="map-loading-overlay">
        <i class="el-icon-loading"></i> 加载中...
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
import { getCityRiskStat } from '@/api/alert'
import { REFRESH_INTERVAL, CITY_COORDS, PROVINCE_CENTERS } from '@/utils/constants'

export default {
  name: 'RiskMap',
  data() {
    return {
      cityRanks: [],
      highRiskAlerts: [],
      highRiskTotal: 0,
      highRiskPage: 1,
      timer: null,
      mapChart: null,
      mapReady: false,
      mapInteractive: false,
      mapLocked: false,
      isRoaming: false,
      currentProvince: null,
      currentGbPrefix: null,
      provinceGeoJson: null,
      cityGeoJson: null,
      allAlertData: [],
      refreshing: false
    }
  },
  mounted() {
    this.initMaps()
    this.timer = setInterval(() => this.loadData(), REFRESH_INTERVAL * 2)
    this._onThemeChange = () => {
      this._mapRendered = false
      if (this.allAlertData.length) this.$nextTick(() => this.renderMap(this.allAlertData))
    }
    document.addEventListener('theme-changed', this._onThemeChange)
  },
  beforeDestroy() {
    clearInterval(this.timer)
    if (this.mapChart) this.mapChart.dispose()
    document.removeEventListener('theme-changed', this._onThemeChange)
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
          if (!this.isRoaming) {
            this.mapLocked = true
            await new Promise(r => setTimeout(r, 250))
            this.$nextTick(() => this.renderMap(raw))
            setTimeout(() => { this.mapLocked = false }, 250)
          }
        }
        this.loadHighRiskDetails()
      } catch (e) { /* ignore */ }
    },

    buildCityRanks(raw) {
      if (this.currentProvince && this.currentGbPrefix) {
        const prefix = this.currentGbPrefix
        return raw
          .filter(d => {
            const gb = this.getCityGb(d.city)
            return gb && String(gb).startsWith(prefix)
          })
          .map(d => ({ city: d.city, count: d.count }))
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
          this.$nextTick(() => this.renderMap(res.data))
        }
        this.loadHighRiskDetails()
        this.$message.success('已刷新')
      } catch (e) { /* ignore */ }
      finally { this.refreshing = false }
    },

    async loadHighRiskDetails() {
      try {
        const res = await fetch(
          `/api/alert/list?riskLevel=高危&page=${this.highRiskPage}&pageSize=20`
        ).then(r => r.json())
        if (res.data) {
          this.highRiskAlerts = res.data.list || []
          this.highRiskTotal = res.data.total || 0
        }
      } catch { /* ignore */ }
    },

    scoreClass(s) {
      if (s >= 80) return 'danger'; if (s >= 60) return 'warning'; return 'success'
    },

    mapColors() {
      const s = getComputedStyle(document.documentElement)
      const g = (k) => s.getPropertyValue(k).trim()
      return {
        geoFill: g('--map-geo-fill'), geoBorder: g('--map-geo-border'),
        alertFill: g('--map-alert-fill'), alertBorder: g('--map-alert-border'),
        tooltipBg: g('--map-tooltip-bg'), tooltipText: g('--map-tooltip-text'),
        overlay: g('--map-overlay'),
        label: g('--color-text-secondary'), hoverFill: g('--color-bg-hover'),
        scatter: 'rgba(217,74,74,0.85)', scatterBorder: '#fff',
        red: '#D94A4A'
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

    renderProvinceView(data, isRefresh = false) {
      const m = this.mapColors()
      const provCount = {}
      data.forEach(d => {
        const prov = this.guessProvince(d.city) || d.city || '未知'
        provCount[prov] = (provCount[prov] || 0) + (d.count || 1)
      })

      const scatterData = Object.entries(provCount).map(([name, cnt]) => {
        const center = this.getProvinceCenter(name)
        return { name, value: [...center, cnt], alertCount: cnt }
      })

      if (isRefresh) {
        this.mapChart.setOption({
          series: [
            { data: scatterData },
            { data: scatterData.filter(d => d.value[2] > 2) }
          ]
        }, false)
        return
      }

      this.mapChart.setOption({
        tooltip: {
          trigger: 'item',
          backgroundColor: m.tooltipBg, borderColor: m.geoBorder,
          textStyle: { color: m.tooltipText, fontSize: 11 },
          formatter: p => p.name ? `${p.name}<br/>告警: <b>${p.data.alertCount || p.value[2]}</b> 条` : ''
        },
        geo: {
          map: 'china', roam: true, zoom: 1.2, center: [105, 36],
          itemStyle: { areaColor: m.geoFill, borderColor: m.geoBorder, borderWidth: 1 },
          emphasis: { itemStyle: { areaColor: m.hoverFill }, label: { color: m.tooltipText, show: true } },
          regions: Object.keys(provCount).map(prov => ({
            name: prov,
            itemStyle: { areaColor: m.alertFill, borderColor: m.alertBorder }
          }))
        },
        series: [{
          type: 'scatter', coordinateSystem: 'geo',
          data: scatterData,
          symbolSize: val => Math.min(Math.max(val[2] * 3, 14), 40),
          itemStyle: {
            color: 'rgba(217,74,74,0.85)',
            borderColor: '#fff', borderWidth: 1,
            shadowBlur: 8, shadowColor: 'rgba(217,74,74,0.4)'
          },
          label: { show: true, position: 'right', color: m.label, fontSize: 10, formatter: p => p.name },
          emphasis: { itemStyle: { borderColor: '#fff', borderWidth: 2 } }
        }, {
          type: 'effectScatter', coordinateSystem: 'geo',
          data: scatterData.filter(d => d.value[2] > 2),
          symbolSize: 6, showEffectOn: 'render',
          rippleEffect: { brushType: 'stroke', scale: 3, period: 3 },
          itemStyle: { color: '#D94A4A' }, zlevel: 1
        }]
      })

      this.mapChart.off('click')
      this.mapChart.on('click', params => {
        if (params.name && params.name !== '境界线') this.drillDown(params.name)
      })
      this.bindRoamEvents()
      this.mapInteractive = true
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

      const cityCount = {}
      data.forEach(d => {
        const city = d.city || '未知'
        const gb = this.getCityGb(city)
        if (gb && String(gb).startsWith(prefix)) {
          const geoName = this.resolveGeoName(city)
          cityCount[geoName] = (cityCount[geoName] || 0) + (d.count || 1)
        }
      })

      const scatterData = Object.entries(cityCount).map(([name, cnt]) => {
        const coords = this.getCityCenter(name) || this.getProvinceCenter(name)
        return { name, value: [...coords, cnt], alertCount: cnt }
      })

      if (isRefresh) {
        this.mapChart.setOption({
          geo: {
            regions: Object.keys(cityCount).map(city => ({
              name: city,
              itemStyle: { areaColor: m.alertFill, borderColor: m.alertBorder }
            }))
          },
          series: [
            { data: scatterData },
            { data: scatterData.filter(d => d.value[2] > 1) }
          ]
        }, false)
        return
      }

      this.mapChart.setOption({
        tooltip: {
          trigger: 'item',
          backgroundColor: m.tooltipBg, borderColor: m.geoBorder,
          textStyle: { color: m.tooltipText, fontSize: 11 },
          formatter: p => p.name ? `${p.name}<br/>告警: <b>${p.data.alertCount || p.value[2]}</b> 条` : ''
        },
        geo: {
          map: mapName, roam: true, zoom: 1.0, center,
          itemStyle: { areaColor: m.geoFill, borderColor: m.geoBorder, borderWidth: 1 },
          emphasis: { itemStyle: { areaColor: m.hoverFill }, label: { color: m.tooltipText, show: true } },
          regions: Object.keys(cityCount).map(city => ({
            name: city,
            itemStyle: { areaColor: m.alertFill, borderColor: m.alertBorder }
          }))
        },
        series: [{
          type: 'scatter', coordinateSystem: 'geo',
          data: scatterData,
          symbolSize: val => Math.min(Math.max(val[2] * 5, 10), 42),
          itemStyle: {
            color: 'rgba(217,74,74,0.85)',
            borderColor: '#fff', borderWidth: 1,
            shadowBlur: 8, shadowColor: 'rgba(217,74,74,0.4)'
          },
          label: { show: true, position: 'right', color: m.label, fontSize: 9, formatter: p => p.name },
          emphasis: { itemStyle: { borderColor: '#fff', borderWidth: 2 } }
        }, {
          type: 'effectScatter', coordinateSystem: 'geo',
          data: scatterData.filter(d => d.value[2] > 1),
          symbolSize: 6, showEffectOn: 'render',
          rippleEffect: { brushType: 'stroke', scale: 3, period: 3 },
          itemStyle: { color: '#D94A4A' }, zlevel: 1
        }]
      })

      this.mapChart.off('click')
      this.bindRoamEvents()
      this.mapInteractive = true
    },

    bindRoamEvents() {
      const el = this.mapChart.getDom()
      if (!el) return
      const start = () => { this.isRoaming = true }
      el.addEventListener('mousedown', start)
      el.addEventListener('touchstart', start)
      el.addEventListener('wheel', start)
      this.mapChart.off('georoam')
      this.mapChart.on('georoam', () => {
        this.isRoaming = false
        clearTimeout(this._roamTimer)
        this._roamTimer = setTimeout(() => this.loadData(), 300)
      })
    },

    drillDown(provinceName) {
      this._mapRendered = false; this.mapInteractive = false; this._switchView()
      if (!this.provinceGeoJson) return
      const prov = this.provinceGeoJson.features.find(f => f.properties.name === provinceName)
      if (!prov || !prov.properties.gb) return
      this.currentGbPrefix = String(prov.properties.gb).slice(0, 5)
      this.currentProvince = provinceName
      this.$nextTick(() => this.renderMap(this.allAlertData))
    },

    backToChina() {
      this._mapRendered = false; this.mapInteractive = false; this._switchView()
      this.currentProvince = null; this.currentGbPrefix = null
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
.risk-map-page { min-height: 100%; }

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

.map-container.no-interact {
  pointer-events: none;
}

.map-loading,
.map-loading-overlay {
  color: var(--color-text-muted);
  font-size: var(--text-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
}

.map-loading { height: 460px; }

.map-loading-overlay {
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  background: var(--map-overlay);
  z-index: 10;
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
.mono-num.danger  { color: var(--color-danger); }
.mono-num.warning { color: var(--color-warning); }
.mono-num.success { color: var(--color-success); }
</style>
