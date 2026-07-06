<template>
  <div class="full-page">
    <div class="page-header">
      <h2 class="page-title">风险地理分布</h2>
      <div class="header-right">
        <el-tag v-if="currentProvince" type="warning" size="small" closable @close="backToChina">{{ currentProvince }}</el-tag>
        <el-button v-if="currentProvince" size="mini" icon="el-icon-back" @click="backToChina">返回全国</el-button>
        <span class="subtitle">{{ currentProvince ? currentProvince + ' 各市' : '全国高危告警实时点位' }}</span>
        <el-button size="mini" icon="el-icon-refresh" :loading="refreshing" @click="manualRefresh">刷新</el-button>
      </div>
    </div>

    <div class="body-row">
      <div class="map-card">
        <div v-if="!mapReady" class="map-loading"><i class="el-icon-loading"></i> 地图加载中...</div>
        <div ref="mapChart" class="map-container" v-show="mapReady"></div>
        <div v-if="mapReady" class="map-legend">
          <div class="legend-title">{{ currentProvince ? '市级' : '省级' }}告警</div>
          <div class="legend-row" v-for="lv in legendLevels" :key="lv.label">
            <span class="legend-swatch" :style="{ background: lv.color }"></span>
            <span class="legend-label">{{ lv.label }}</span>
          </div>
          <div style="margin-top:4px;border-top:1px solid rgba(255,255,255,0.1);padding-top:4px;">
            <div class="legend-row"><span class="legend-swatch" style="background:#F97316;"></span><span class="legend-label">高危</span></div>
            <div class="legend-row"><span class="legend-swatch" style="background:#DC2626;"></span><span class="legend-label">极度危险</span></div>
          </div>
        </div>
      </div>

      <div class="right-panels">
        <div class="panel flex-1">
          <div class="panel-header"><span>{{ currentProvince ? currentProvince + ' — ' : '' }}告警排行</span></div>
          <el-table :data="cityRanks" size="mini" max-height="100%" class="flex-table">
            <el-table-column type="index" label="#" width="36" />
            <el-table-column prop="city" label="区域" />
            <el-table-column prop="count" label="告警数" width="60">
              <template slot-scope="{ row }"><el-tag :type="row.count > 5 ? 'danger' : 'warning'" size="mini" effect="dark">{{ row.count }}</el-tag></template>
            </el-table-column>
          </el-table>
        </div>
        <div class="panel flex-1">
          <div class="panel-header"><span>高危告警详情</span><span class="card-count">共 {{ highRiskTotal }} 条</span></div>
          <el-table :data="highRiskAlerts" size="mini" max-height="100%" class="flex-table">
            <el-table-column prop="alertLoc" label="城市" width="56" />
            <el-table-column prop="hitRules" label="触发规则" min-width="100" show-overflow-tooltip />
            <el-table-column prop="finalScore" label="评分" width="50">
              <template slot-scope="{ row }"><span :class="['mono-num', scoreClass(row.finalScore)]">{{ row.finalScore }}</span></template>
            </el-table-column>
          </el-table>
          <el-pagination small layout="prev, pager, next" :total="highRiskTotal" :page-size="20"
            :current-page.sync="highRiskPage" @current-change="loadHighRiskDetails"
            style="text-align:center;padding-top:4px;flex-shrink:0" />
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import * as echarts from 'echarts'
import { getCityRiskStat, getAlertList } from '@/api/alert'
import { REFRESH_INTERVAL, CITY_COORDS, PROVINCE_CENTERS } from '@/utils/constants'

function buildTooltip(p) {
  const d = p.data || {}
  let h = `${p.name || ''}`
  if (d.highCount > 0) h += `<br/><b style="color:#F97316;">高危</b>: ${d.highCount} 条`
  if (d.criticalCount > 0) h += `<br/><b style="color:#DC2626;">极度危险</b>: ${d.criticalCount} 条`
  return h
}

export default {
  name: 'RiskMap',
  data() {
    return {
      cityRanks: [], highRiskAlerts: [], highRiskTotal: 0, highRiskPage: 1,
      mapChart: null, mapReady: false, currentProvince: null, currentGbPrefix: null,
      provinceGeoJson: null, cityGeoJson: null, allAlertData: [],
      refreshing: false, mutex: 1, waitQ: [], dataMax: 0, timer: null
    }
  },
  computed: {
    legendLevels() {
      const colors = this.currentProvince ? ['#E8C0C0', '#D07070', '#8B0000'] : ['#3D1525', '#5A2035', '#7D2D4A', '#A53D5E', '#CD4D70']
      const n = colors.length; const mx = Math.max(this.dataMax, 1)
      const step = Math.max(1, Math.ceil(mx / n)); const levels = []
      for (let i = 0; i < n; i++) {
        const lo = i * step + 1; const hi = (i + 1) * step
        if (lo > mx) break
        levels.push({ label: hi >= mx ? `≥${lo}` : lo === hi ? `${lo}` : `${lo}-${hi}`, color: colors[i] })
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
    clearInterval(this.timer); if (this.mapChart) this.mapChart.dispose()
    document.removeEventListener('theme-changed', this._onTheme)
  },
  methods: {
    async initMaps() {
      try {
        const [p, c] = await Promise.all([fetch('/map/china.json'), fetch('/map/city.json')])
        this.provinceGeoJson = await p.json(); this.cityGeoJson = await c.json()
        echarts.registerMap('china', this.provinceGeoJson)
        this.mapReady = true
      } catch { this.$message.error('地图数据加载失败') }
      this.$nextTick(() => this.loadData())
    },
    async loadData() {
      if (!this.mapReady) return
      try {
        const res = await getCityRiskStat(200)
        if (res.code === 200 && res.data) {
          this.allAlertData = res.data
          this.cityRanks = this.buildCityRanks(res.data)
          await this.acquire(); this.renderMap(res.data); this.$nextTick(() => this.release())
        }
        this.loadHighRiskDetails()
      } catch { this.release() }
    },
    acquire() { if (this.mutex === 1) { this.mutex = 0; return Promise.resolve() } return new Promise(r => this.waitQ.push(r)) },
    release() { if (this.waitQ.length) this.waitQ.shift()(); else this.mutex = 1 },
    buildCityRanks(raw) {
      if (this.currentProvince && this.currentGbPrefix) {
        const prefix = this.currentGbPrefix; const cc = {}
        raw.forEach(d => { const gb = this.getCityGb(d.city); if (gb && String(gb).startsWith(prefix)) { const n = this.resolveGeoName(d.city) || d.city; cc[n] = (cc[n] || 0) + (d.count || 1) } })
        return Object.entries(cc).map(([city, count]) => ({ city, count })).sort((a, b) => b.count - a.count)
      }
      const pc = {}
      raw.forEach(d => { const p = this.guessProvince(d.city) || d.city || '未知'; pc[p] = (pc[p] || 0) + (d.count || 1) })
      return Object.entries(pc).map(([city, count]) => ({ city, count })).sort((a, b) => b.count - a.count)
    },
    async manualRefresh() {
      this.refreshing = true
      try { const r = await getCityRiskStat(200); if (r.code === 200 && r.data) { this.allAlertData = r.data; this._mapRendered = false; await this.acquire(); this.$nextTick(() => { this.renderMap(r.data); this.$nextTick(() => this.release()) }) }; this.loadHighRiskDetails() }
      catch { this.release() } finally { this.refreshing = false }
    },
    async loadHighRiskDetails() {
      try {
        const res = await getAlertList({ page: this.highRiskPage, pageSize: 20 })
        if (res.code === 200 && res.data) { this.highRiskAlerts = (res.data.list || []).filter(a => a.riskLevel === '高危' || a.riskLevel === '极度危险'); this.highRiskTotal = this.highRiskAlerts.length }
      } catch {}
    },
    scoreClass(s) { if (s > 120) return 'critical'; if (s >= 71) return 'danger'; if (s >= 41) return 'warning'; return 'success' },
    colorFor(cnt) {
      const levels = this.legendLevels; if (!levels.length) return '#333'
      const n = this.currentProvince ? 3 : 5; const step = Math.max(1, Math.ceil(Math.max(this.dataMax, 1) / n))
      return levels[Math.min(Math.floor((cnt - 1) / step), levels.length - 1)].color
    },
    renderMap(data) {
      const el = this.$refs.mapChart; if (!el || !this.mapReady) return
      if (!this.mapChart) this.mapChart = echarts.init(el)
      const isRefresh = this._mapRendered
      this._mapRendered = true
      if (!this.currentProvince) {
        this.renderProvinceView(data, isRefresh)
      } else {
        this.renderCityView(data, isRefresh)
      }
    },
    mapColors() {
      const s = getComputedStyle(document.documentElement), g = k => s.getPropertyValue(k).trim()
      return { geoFill: g('--map-geo-fill'), geoBorder: g('--map-geo-border'), hoverFill: g('--color-bg-hover'), tooltipText: g('--map-tooltip-text') }
    },
    renderProvinceView(data, isRefresh = false) {
      const m = this.mapColors(); const pT = {}, pH = {}, pC = {}
      data.forEach(d => { const p = this.guessProvince(d.city) || d.city || '未知'; pT[p] = (pT[p] || 0) + (d.count || 1); (d.riskLevel === '极度危险' ? pC : pH)[p] = ((d.riskLevel === '极度危险' ? pC : pH)[p] || 0) + (d.count || 1) })
      this.dataMax = Math.max(...Object.values(pT), 1)
      const regions = Object.entries(pT).map(([n, c]) => ({ name: n, itemStyle: { areaColor: this.colorFor(c), borderColor: '#5A2828' } }))
      const all = new Set([...Object.keys(pH), ...Object.keys(pC)])
      const sd = [...all].map(n => ({ name: n, value: [...this.getProvinceCenter(n), (pH[n] || 0) + (pC[n] || 0)], highCount: pH[n] || 0, criticalCount: pC[n] || 0, itemStyle: { color: (pC[n] || 0) > 0 ? '#DC2626' : '#F97316', opacity: 0.88 }, symbolSize: (pC[n] || 0) > 0 ? 14 : 10 }))
      if (isRefresh) {
        this.mapChart.setOption({ animation: false, series: [{ data: sd }] }, false)
        return
      }
      this.mapChart.setOption({
        geo: { map: 'china', roam: true, zoom: 1.2, center: [105, 36], itemStyle: { areaColor: m.geoFill, borderColor: m.geoBorder, borderWidth: 1 }, emphasis: { itemStyle: { areaColor: m.hoverFill }, label: { color: m.tooltipText, show: true } }, regions },
        series: [{ type: 'scatter', coordinateSystem: 'geo', data: sd, symbolSize: 10, tooltip: { formatter: p => buildTooltip(p) } }]
      })
      this.mapChart.setOption({ tooltip: { trigger: 'item' } }, false)
      this.mapChart.off('click'); this.mapChart.on('click', p => { if (p.name && p.name !== '境界线') this.drillDown(p.name) }); this.bindLock()
    },
    renderCityView(data, isRefresh = false) {
      const m = this.mapColors(); const prefix = this.currentGbPrefix
      const feats = this.cityGeoJson.features.filter(f => String(f.properties.gb || '').startsWith(prefix))
      if (!feats.length) { this.backToChina(); return }
      const mapName = 'province-cities'; echarts.registerMap(mapName, { type: 'FeatureCollection', features: feats })
      const b = this.computeBounds(feats); const center = [(b.minLng + b.maxLng) / 2, (b.minLat + b.maxLat) / 2]
      const cT = {}, cH = {}, cC = {}
      data.forEach(d => { const c = d.city || '未知'; const gb = this.getCityGb(c); if (gb && String(gb).startsWith(prefix)) { const gn = this.resolveGeoName(c); cT[gn] = (cT[gn] || 0) + (d.count || 1); (d.riskLevel === '极度危险' ? cC : cH)[gn] = ((d.riskLevel === '极度危险' ? cC : cH)[gn] || 0) + (d.count || 1) } })
      this.dataMax = Math.max(...Object.values(cT), 1)
      const regions = Object.entries(cT).map(([n, c]) => ({ name: n, itemStyle: { areaColor: this.colorFor(c), borderColor: '#5A2828' } }))
      const all = new Set([...Object.keys(cH), ...Object.keys(cC)])
      const sd = [...all].map(n => ({ name: n, value: [...(this.getCityCenter(n) || this.getProvinceCenter(n)), (cH[n] || 0) + (cC[n] || 0)], highCount: cH[n] || 0, criticalCount: cC[n] || 0, itemStyle: { color: (cC[n] || 0) > 0 ? '#DC2626' : '#F97316', opacity: 0.88 }, symbolSize: (cC[n] || 0) > 0 ? 14 : 10 }))
      if (isRefresh) {
        this.mapChart.setOption({ animation: false, series: [{ data: sd }] }, false)
        return
      }
      this.mapChart.setOption({
        geo: { map: mapName, roam: true, zoom: 1.0, center, itemStyle: { areaColor: m.geoFill, borderColor: m.geoBorder, borderWidth: 1 }, emphasis: { itemStyle: { areaColor: m.hoverFill }, label: { color: m.tooltipText, show: true } }, regions },
        series: [{ type: 'scatter', coordinateSystem: 'geo', data: sd, symbolSize: 10, tooltip: { formatter: p => buildTooltip(p) } }]
      })
      this.mapChart.setOption({ tooltip: { trigger: 'item' } }, false)
      this.bindLock()
    },
    bindLock() { this.mapChart.off('mousedown'); this.mapChart.on('mousedown', () => { this.mutex = 0 }); const el = this.mapChart.getDom(); el.addEventListener('wheel', () => { this.mutex = 0; clearTimeout(this._wt); this._wt = setTimeout(() => this.release(), 300) }); document.addEventListener('mouseup', () => this.release()) },
    drillDown(n) { this._mapRendered = false; this._switchView(); const p = this.provinceGeoJson.features.find(f => f.properties.name === n); if (!p || !p.properties.gb) return; this.currentGbPrefix = String(p.properties.gb).slice(0, 5); this.currentProvince = n; this.cityRanks = this.buildCityRanks(this.allAlertData); this.$nextTick(() => this.renderMap(this.allAlertData)) },
    backToChina() { this._mapRendered = false; this._switchView(); this.currentProvince = null; this.currentGbPrefix = null; this.cityRanks = this.buildCityRanks(this.allAlertData); this.$nextTick(() => this.renderMap(this.allAlertData)) },
    _switchView() { if (this.mapChart) { this.mapChart.dispose(); this.mapChart = null } },
    getProvinceCenter(n) { if (PROVINCE_CENTERS[n]) return PROVINCE_CENTERS[n]; if (!this.provinceGeoJson) return [116, 36]; const f = this.provinceGeoJson.features.find(x => x.properties.name === n); if (!f) return CITY_COORDS[n] || [116 + Math.random() * 5, 35 + Math.random() * 5]; return this.computePolygonCenter(f) },
    getCityCenter(n) { if (!this.cityGeoJson) return null; const f = this.findCityFeature(n); if (f) return this.computePolygonCenter(f); return CITY_COORDS[n] || null },
    resolveGeoName(n) { const f = this.findCityFeature(n); return f ? f.properties.name : n },
    findCityFeature(n) { if (!this.cityGeoJson || !n) return null; let f = this.cityGeoJson.features.find(x => x.properties.name === n); if (f) return f; f = this.cityGeoJson.features.find(x => x.properties.name === n + '市'); if (f) return f; f = this.cityGeoJson.features.find(x => x.properties.name === n + '特别行政区'); if (f) return f; if (n.endsWith('市')) { f = this.cityGeoJson.features.find(x => x.properties.name === n.slice(0, -1)); if (f) return f } return null },
    getCityGb(n) { const f = this.findCityFeature(n); return f ? f.properties.gb : null },
    guessProvince(n) { const gb = this.getCityGb(n); if (gb && this.provinceGeoJson) { const p = this.provinceGeoJson.features.find(f => String(f.properties.gb || '').startsWith(String(gb).slice(0, 5))); if (p) return p.properties.name } if (['北京', '上海', '天津', '重庆'].includes(n)) return n + '市'; if (n === '香港') return '香港特别行政区'; if (n === '澳门') return '澳门特别行政区'; return n },
    computePolygonCenter(f) { if (!f || !f.geometry) return [116, 36]; try { const geom = f.geometry; let rings = []; if (geom.type === 'Polygon') rings = [geom.coordinates[0]]; else if (geom.type === 'MultiPolygon') rings = geom.coordinates.map(p => p[0]); else return [116, 36]; let br = null, ba = -1; for (const ring of rings) { if (!ring || ring.length < 3) continue; let a = 0; for (let i = 0; i < ring.length - 1; i++) a += ring[i][0] * ring[i + 1][1] - ring[i + 1][0] * ring[i][1]; a = Math.abs(a); if (a > ba) { ba = a; br = ring } } if (!br) return [116, 36]; const lngs = br.map(c => c[0]), lats = br.map(c => c[1]); return [(Math.min(...lngs) + Math.max(...lngs)) / 2, (Math.min(...lats) + Math.max(...lats)) / 2] } catch { return [116, 36] } },
    computeBounds(fs) { let l = 180, r = -180, t = 90, b = -90; fs.forEach(f => { if (!f.geometry) return; const cs = f.geometry.type === 'MultiPolygon' ? f.geometry.coordinates[0][0] : f.geometry.coordinates[0]; if (!cs) return; cs.forEach(c => { if (c[0] < l) l = c[0]; if (c[0] > r) r = c[0]; if (c[1] < b) b = c[1]; if (c[1] > t) t = c[1] }) }); return { minLng: l, maxLng: r, minLat: b, maxLat: t } }
  }
}
</script>

<style scoped>
.full-page { height: 100%; display: flex; flex-direction: column; overflow: hidden; }

.page-header { display: flex; justify-content: space-between; align-items: center; padding-bottom: 6px; border-bottom: 1px solid var(--color-border); flex-shrink: 0; }
.page-title { color: var(--color-text-primary); font-size: 15px; font-weight: 600; margin: 0; }
.header-right { display: flex; align-items: center; gap: 8px; }
.subtitle { color: var(--color-text-muted); font-size: 15px; }

.body-row { display: flex; gap: 8px; flex: 1; min-height: 0; padding-top: 6px; }
.map-card { flex: 2; position: relative; background: var(--color-bg-elevated); border: 1px solid var(--color-border); border-radius: var(--radius-sm); overflow: hidden; display: flex; flex-direction: column; }
.map-container { flex: 1; }
.map-legend { position: absolute; bottom: 6px; left: 6px; background: rgba(0,0,0,0.55); border-radius: 2px; padding: 4px 6px; z-index: 5; pointer-events: none; }
.legend-title { font-size: 13px; color: #9AACBF; margin-bottom: 2px; }
.legend-row { display: flex; align-items: center; gap: 4px; margin: 1px 0; }
.legend-swatch { width: 14px; height: 8px; border-radius: 1px; flex-shrink: 0; }
.legend-label { font-size: 12px; color: #8899AA; }
.map-loading { flex: 1; display: flex; align-items: center; justify-content: center; color: var(--color-text-muted); font-size: 16px; gap: 8px; }

.right-panels { flex: 1; display: flex; flex-direction: column; gap: 8px; }
.flex-1 { flex: 1; min-height: 0; height: 0; }
.panel { background: var(--color-bg-elevated); border: 1px solid var(--color-border); border-radius: var(--radius-sm); padding: 6px 8px; display: flex; flex-direction: column; overflow: hidden; }
.panel-header { display: flex; justify-content: space-between; align-items: center; padding-bottom: 4px; border-bottom: 1px solid var(--color-border); flex-shrink: 0; font-size: 15px; color: var(--color-text-secondary); font-weight: 500; }
.card-count { font-size: 14px; color: var(--color-text-muted); }
.flex-table { flex: 1; min-height: 0; }

.mono-num { font-family: var(--font-mono); font-size: 16px; font-weight: 600; }
.mono-num.critical { color: var(--color-critical); }
.mono-num.danger   { color: var(--color-danger); }
.mono-num.warning  { color: var(--color-warning); }
.mono-num.success  { color: var(--color-success); }
</style>
