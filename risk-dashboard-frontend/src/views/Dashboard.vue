<template>
  <div class="home-screen">
    <div class="top-bar">
      <h1 class="title">金融交易风险实时监控平台</h1>
      <div class="top-right">
        <span :class="['dot', online ? 'online' : 'offline']"></span>
        <span class="refresh-text">{{ lastRefresh }}</span>
      </div>
    </div>

    <div class="cards-row">
      <div class="metric-card" v-for="c in cards" :key="c.key" :class="c.variant">
        <div class="card-label">{{ c.label }}</div>
        <div class="card-value">{{ c.value }}</div>
      </div>
    </div>

    <div class="charts-grid">
      <div class="panel panel-left">
        <div class="panel-hd">
          <span>交易与告警趋势 (24H)</span>
          <el-radio-group v-model="trendType" size="mini">
            <el-radio-button label="transaction">交易量</el-radio-button>
            <el-radio-button label="alert">告警量</el-radio-button>
            <el-radio-button label="blockRate">拦截率</el-radio-button>
          </el-radio-group>
        </div>
        <div ref="trend" class="chart-fill"></div>
      </div>

      <div class="panel-col">
        <div class="panel panel-sm">
          <div class="panel-hd"><span>风险等级分布</span></div>
          <div ref="riskPie" class="chart-fill"></div>
        </div>
        <div class="panel panel-sm">
          <div class="panel-hd"><span>风险指数</span></div>
          <div ref="gauge" class="chart-fill"></div>
        </div>
      </div>

      <div class="panel-col">
        <div class="panel panel-sm">
          <div class="panel-hd"><span>触发规则分布</span></div>
          <div ref="rulePie" class="chart-fill"></div>
        </div>
        <div class="panel panel-sm">
          <div class="panel-hd"><span>城市活跃度 Top6</span></div>
          <div ref="cityBar" class="chart-fill"></div>
        </div>
      </div>
    </div>

    <div class="panel panel-bottom">
      <div class="panel-hd"><span>最新告警</span></div>
      <el-table :data="alerts" stripe size="mini" max-height="100%" class="compact-table">
        <el-table-column prop="alertId" label="告警编号" width="190" />
        <el-table-column prop="transId" label="交易流水" width="190" />
        <el-table-column prop="hitRules" label="触发规则" min-width="140" show-overflow-tooltip />
        <el-table-column prop="amount" label="金额" width="90">
          <template slot-scope="{ row }"><span class="mono">¥{{ row.amount?.toFixed(0) }}</span></template>
        </el-table-column>
        <el-table-column prop="finalScore" label="评分" width="65">
          <template slot-scope="{ row }">
            <span v-if="row.finalScore > 120" class="score-tag critical">{{ row.finalScore }}</span>
            <el-tag v-else :type="sType(row.finalScore)" size="mini" effect="dark">{{ row.finalScore }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="riskLevel" label="等级" width="70">
          <template slot-scope="{ row }">
            <span :class="['r-text', rClass(row.riskLevel)]">{{ row.riskLevel }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="alertLoc" label="城市" width="60" />
        <el-table-column prop="status" label="状态" width="65">
          <template slot-scope="{ row }">
            <el-tag :type="stType(row.status)" size="mini">{{ stLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="时间" width="155" />
      </el-table>
    </div>
  </div>
</template>

<script>
import * as echarts from 'echarts'
import { getDashboardData } from '@/api/metrics'
import { REFRESH_INTERVAL } from '@/utils/constants'

const ST = { pending: 'info', processing: 'warning', verified: 'success', blocked: 'danger', closed: 'info' }
const SL = { pending: '待处理', processing: '处理中', verified: '已核验', blocked: '已拦截', closed: '已关闭' }

export default {
  name: 'Dashboard',
  data() {
    return {
      data: null, trendType: 'transaction', lastRefresh: '--', online: false,
      timer: null, charts: {}
    }
  },
  computed: {
    cards() {
      const d = this.data || {}
      return [
        { key: 'txn', label: '交易总量', value: this.fmt(d.totalTransactions), variant: '' },
        { key: 'pass', label: '放行', value: this.fmt(d.passCount), variant: 'success' },
        { key: 'verify', label: '待核验', value: this.fmt(d.verifyCount), variant: 'warning' },
        { key: 'block', label: '拦截', value: this.fmt(d.blockCount), variant: 'danger' },
        { key: 'user', label: '活跃用户', value: this.fmt(d.activeUsers), variant: '' },
        { key: 'score', label: '平均风险分', value: d.avgRiskScore?.toFixed(1) || '0', variant: '' },
        { key: 'latency', label: '延迟(ms)', value: d.avgLatency?.toFixed(0) || '0', variant: '' }
      ]
    },
    alerts() { return (this.data?.recentAlerts || []).slice(0, 8) }
  },
  mounted() {
    this.fetchData()
    this.timer = setInterval(() => this.fetchData(), REFRESH_INTERVAL)
  },
  beforeDestroy() {
    clearInterval(this.timer)
    Object.values(this.charts).forEach(c => c.dispose())
  },
  watch: { data() { this.$nextTick(() => this.renderAll()) }, trendType() { this.renderTrend() } },
  methods: {
    fmt(n) { return n?.toLocaleString?.() || n },
    sType(s) { return s >= 71 ? 'danger' : s >= 41 ? 'warning' : 'success' },
    rClass(l) {
      if (l === '极度危险') return 'critical'; if (l === '高危') return 'danger'
      if (l === '中危') return 'warning'; return 'success'
    },
    stType(s) { return ST[s] || 'info' },
    stLabel(s) { return SL[s] || s },

    async fetchData() {
      try {
        const res = await getDashboardData()
        if (res.code === 200) {
          this.data = res.data
          this.lastRefresh = new Date().toLocaleTimeString('zh-CN')
          this.online = true
        }
      } catch { this.online = false }
    },

    renderAll() {
      this.renderTrend(); this.renderRiskPie(); this.renderRulePie(); this.renderCityBar(); this.renderGauge()
    },

    renderTrend() {
      const el = this.$refs.trend; if (!el) return
      if (!this.charts.trend) this.charts.trend = echarts.init(el)
      const d = this.data; let data = [], name = ''
      if (this.trendType === 'transaction') { data = d?.transactionTrend || []; name = '交易量' }
      else if (this.trendType === 'alert') { data = d?.alertTrend || []; name = '告警量' }
      else { data = d?.blockRateTrend || []; name = '拦截率(%)' }
      this.charts.trend.setOption({
        tooltip: { trigger: 'axis', backgroundColor: '#121E33', borderColor: '#1C2B42', textStyle: { color: '#D8DFE8', fontSize: 10 } },
        grid: { left: 40, right: 8, top: 4, bottom: 18 },
        xAxis: { type: 'category', data: data.map(p => p.time), axisLine: { lineStyle: { color: '#1C2B42' } }, axisLabel: { color: '#5D6F85', fontSize: 9 }, axisTick: { show: false } },
        yAxis: { type: 'value', axisLine: { show: false }, axisLabel: { color: '#5D6F85', fontSize: 9 }, splitLine: { lineStyle: { color: '#1C2B42' } } },
        series: [{ name, type: 'line', data: data.map(p => p.value), smooth: false, symbol: 'none', lineStyle: { color: '#4A90D9', width: 1 } }]
      }, true)
    },

    renderRiskPie() {
      const el = this.$refs.riskPie; if (!el) return
      if (!this.charts.riskPie) this.charts.riskPie = echarts.init(el)
      const colors = { '极度危险': '#DC2626', '高危': '#F97316', '中危': '#F59E0B', '低危': '#22C55E' }
      const data = (this.data?.riskLevelDistribution || []).map(d => ({
        name: d.name, value: d.value, itemStyle: { color: colors[d.name] || d.color }
      }))
      this.charts.riskPie.setOption({
        tooltip: { trigger: 'item', formatter: '{b}: {c}', backgroundColor: '#121E33', borderColor: '#1C2B42', textStyle: { color: '#D8DFE8', fontSize: 10 } },
        series: [{ type: 'pie', radius: ['45%', '68%'], center: ['50%', '52%'], label: { color: '#5D6F85', fontSize: 9 }, data }]
      }, true)
    },

    renderRulePie() {
      const el = this.$refs.rulePie; if (!el) return
      if (!this.charts.rulePie) this.charts.rulePie = echarts.init(el)
      const colors = ['#4A90D9', '#F59E0B', '#F97316', '#7B8CAA']
      const data = (this.data?.ruleTypeDistribution || []).map((d, i) => ({
        name: d.name, value: d.value, itemStyle: { color: d.color || colors[i] }
      }))
      this.charts.rulePie.setOption({
        tooltip: { trigger: 'item', formatter: '{b}: {c}', backgroundColor: '#121E33', borderColor: '#1C2B42', textStyle: { color: '#D8DFE8', fontSize: 10 } },
        series: [{ type: 'pie', radius: ['45%', '68%'], center: ['50%', '52%'], label: { color: '#5D6F85', fontSize: 9 }, data }]
      }, true)
    },

    renderCityBar() {
      const el = this.$refs.cityBar; if (!el) return
      if (!this.charts.cityBar) this.charts.cityBar = echarts.init(el)
      const data = (this.data?.cityDistribution || []).slice(0, 6)
      this.charts.cityBar.setOption({
        tooltip: { trigger: 'axis', backgroundColor: '#121E33', borderColor: '#1C2B42', textStyle: { color: '#D8DFE8', fontSize: 10 } },
        grid: { left: 38, right: 4, top: 2, bottom: 18 },
        xAxis: { type: 'category', data: data.map(d => d.name), axisLine: { lineStyle: { color: '#1C2B42' } }, axisLabel: { color: '#5D6F85', fontSize: 8 }, axisTick: { show: false } },
        yAxis: { type: 'value', axisLine: { show: false }, axisLabel: { color: '#5D6F85', fontSize: 9 }, splitLine: { lineStyle: { color: '#1C2B42' } } },
        series: [{ type: 'bar', data: data.map(d => ({ value: d.value, itemStyle: { color: '#4A90D9' } })), barWidth: '50%' }]
      }, true)
    },

    renderGauge() {
      const el = this.$refs.gauge; if (!el) return
      if (!this.charts.gauge) this.charts.gauge = echarts.init(el)
      this.charts.gauge.setOption({
        series: [{
          type: 'gauge', startAngle: 210, endAngle: -30, min: 0, max: 100, splitNumber: 10,
          axisLine: { lineStyle: { color: [[0.2, '#22C55E'], [0.4, '#F59E0B'], [0.6, '#F97316'], [1, '#DC2626']], width: 5 } },
          pointer: { length: '50%', width: 3, itemStyle: { color: '#9AACBF' } },
          axisTick: { distance: -5, length: 3, lineStyle: { color: '#5D6F85', width: 1 } },
          splitLine: { distance: -8, length: 10, lineStyle: { color: '#5D6F85', width: 1 } },
          axisLabel: { color: '#5D6F85', distance: 12, fontSize: 8 },
          detail: { formatter: '{value}', color: '#D8DFE8', fontSize: 20, fontFamily: 'monospace', offsetCenter: [0, '65%'] },
          data: [{ value: this.data?.avgRiskScore || 0, name: '' }]
        }]
      }, true)
    }
  }
}
</script>

<style scoped>
.home-screen {
  height: 100%;
  display: flex; flex-direction: column; overflow: hidden;
}

.top-bar {
  display: flex; justify-content: space-between; align-items: center;
  padding: 0 0 6px; border-bottom: 1px solid var(--color-border);
}

.title { font-size: 15px; font-weight: 600; color: var(--color-text-primary); margin: 0; }
.top-right { display: flex; align-items: center; gap: 6px; }
.dot { width: 6px; height: 6px; border-radius: 50%; }
.dot.online { background: var(--color-success); }
.dot.offline { background: var(--color-danger); }
.refresh-text { color: var(--color-text-muted); font-size: 11px; font-family: var(--font-mono); }

.cards-row { display: flex; gap: 8px; padding: 6px 0; flex-shrink: 0; }

.metric-card {
  flex: 1; background: var(--color-bg-elevated);
  border: 1px solid var(--color-border); border-radius: var(--radius-sm);
  padding: 6px 10px; position: relative; overflow: hidden;
}

.metric-card.success { border-left: 2px solid var(--color-success); }
.metric-card.warning { border-left: 2px solid var(--color-warning); }
.metric-card.danger  { border-left: 2px solid var(--color-danger); }

.card-label { color: var(--color-text-muted); font-size: 10px; }
.card-value { font-family: var(--font-mono); font-size: 18px; font-weight: 600; color: var(--color-text-primary); margin-top: 1px; }

.charts-grid { display: flex; gap: 8px; flex: 1; min-height: 0; padding: 6px 0; }
.panel-left { flex: 2; }
.panel-col { flex: 1; display: flex; flex-direction: column; gap: 8px; }
.panel-sm { flex: 1; }
.panel-bottom { flex-shrink: 0; flex: 1; min-height: 0; }

.panel {
  background: var(--color-bg-elevated);
  border: 1px solid var(--color-border); border-radius: var(--radius-sm);
  padding: 6px 8px; display: flex; flex-direction: column; overflow: hidden;
}
.compact-table { flex: 1; min-height: 0; }

.panel-hd {
  display: flex; justify-content: space-between; align-items: center;
  padding-bottom: 4px; margin-bottom: 2px; border-bottom: 1px solid var(--color-border);
  font-size: 11px; color: var(--color-text-secondary); font-weight: 500; flex-shrink: 0;
}

.chart-fill { flex: 1; min-height: 0; }

.mono { font-family: var(--font-mono); font-size: 11px; }
.r-text { font-weight: 600; font-size: 11px; }
.r-text.critical { color: var(--color-critical); }
.r-text.danger   { color: var(--color-danger); }
.r-text.warning  { color: var(--color-warning); }
.r-text.success  { color: var(--color-success); }

.score-tag {
  display: inline-block; padding: 0 5px; height: 18px; line-height: 18px;
  font-size: 10px; font-weight: 700; font-family: var(--font-mono);
  color: #fff; background: #991B1B; border-radius: 2px;
}
</style>
