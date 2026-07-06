<template>
  <div class="dashboard">
    <div class="page-header">
      <h2 class="page-title">实时风险监控仪表盘</h2>
      <div class="header-meta">
        <span class="meta-refresh">{{ lastRefresh }}</span>
        <span class="dot online"></span>
      </div>
    </div>

    <el-row :gutter="10" class="metric-row">
      <el-col :span="4" v-for="card in metricCards" :key="card.key">
        <div class="metric-card" :class="card.variant">
          <div class="card-label">{{ card.label }}</div>
          <div class="card-value">{{ card.value }}</div>
          <div class="card-line" :class="card.variant"></div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="10" class="row-gap">
      <el-col :span="16">
        <div class="panel">
          <div class="panel-header">
            <span class="panel-title">交易与告警趋势 (24H)</span>
            <el-radio-group v-model="trendType" size="mini">
              <el-radio-button label="transaction">交易量</el-radio-button>
              <el-radio-button label="alert">告警量</el-radio-button>
              <el-radio-button label="blockRate">拦截率</el-radio-button>
            </el-radio-group>
          </div>
          <div ref="trendChart" class="chart-box"></div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="panel">
          <div class="panel-header">
            <span class="panel-title">风险等级分布</span>
          </div>
          <div ref="riskLevelPie" class="chart-box"></div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="10" class="row-gap">
      <el-col :span="8">
        <div class="panel">
          <div class="panel-header">
            <span class="panel-title">触发规则分布</span>
          </div>
          <div ref="ruleTypePie" class="chart-box"></div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="panel">
          <div class="panel-header">
            <span class="panel-title">城市活跃度 Top10</span>
          </div>
          <div ref="cityBar" class="chart-box"></div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="panel">
          <div class="panel-header">
            <span class="panel-title">实时风险指数</span>
          </div>
          <div ref="gaugeChart" class="chart-box"></div>
        </div>
      </el-col>
    </el-row>

    <div class="panel row-gap">
      <div class="panel-header">
        <span class="panel-title">最新告警</span>
        <el-button size="mini" type="primary" icon="el-icon-refresh" @click="refreshData">刷新</el-button>
      </div>
      <el-table :data="recentAlerts" stripe size="mini" max-height="320">
        <el-table-column prop="alertId" label="告警编号" width="190" />
        <el-table-column prop="transId" label="交易流水" width="190" />
        <el-table-column prop="userId" label="用户" width="100" />
        <el-table-column prop="hitRules" label="触发规则" min-width="140" show-overflow-tooltip />
        <el-table-column prop="amount" label="金额" width="100">
          <template slot-scope="{ row }">
            <span class="mono-num">¥{{ row.amount?.toFixed(2) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="finalScore" label="评分" width="70" sortable>
          <template slot-scope="{ row }">
            <span v-if="row.finalScore > 120" class="score-critical-tag">{{ row.finalScore }}</span>
            <el-tag v-else :type="scoreType(row.finalScore)" size="mini" effect="dark">{{ row.finalScore }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="riskLevel" label="等级" width="60">
          <template slot-scope="{ row }">
            <span :class="['risk-text', riskClass(row.riskLevel)]">{{ row.riskLevel }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="alertLoc" label="城市" width="70" />
        <el-table-column prop="action" label="动作" width="60">
          <template slot-scope="{ row }">
            <el-tag :type="actionTagType(row.action)" size="mini">{{ actionLabel(row.action) }}</el-tag>
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
import { REFRESH_INTERVAL, ACTION_MAP } from '@/utils/constants'

export default {
  name: 'Dashboard',
  data() {
    return {
      trendType: 'transaction',
      dashboardData: null,
      lastRefresh: '--',
      refreshTimer: null,
      charts: {}
    }
  },
  computed: {
    metricCards() {
      const d = this.dashboardData || {}
      return [
        { key: 'total', label: '交易量', value: this.fmtNum(d.totalTransactions || 0), variant: 'neutral' },
        { key: 'pass', label: '放行', value: this.fmtNum(d.passCount || 0), variant: 'success' },
        { key: 'verify', label: '待核验', value: this.fmtNum(d.verifyCount || 0), variant: 'warning' },
        { key: 'block', label: '拦截', value: this.fmtNum(d.blockCount || 0), variant: 'danger' },
        { key: 'users', label: '活跃用户', value: this.fmtNum(d.activeUsers || 0), variant: 'neutral' },
        { key: 'latency', label: '延迟(ms)', value: d.avgLatency ? d.avgLatency.toFixed(0) : '0', variant: 'neutral' }
      ]
    },
    recentAlerts() {
      return this.dashboardData?.recentAlerts || []
    }
  },
  mounted() {
    this.fetchData()
    this.refreshTimer = setInterval(() => this.fetchData(), REFRESH_INTERVAL)
  },
  beforeDestroy() {
    clearInterval(this.refreshTimer)
    Object.values(this.charts).forEach(c => c.dispose())
  },
  watch: {
    dashboardData() { this.$nextTick(() => this.renderCharts()) },
    trendType() { this.renderTrendChart() }
  },
  methods: {
    async fetchData() {
      try {
        const res = await getDashboardData()
        if (res.code === 200) {
          this.dashboardData = res.data
          this.lastRefresh = new Date().toLocaleTimeString('zh-CN')
        }
      } catch (e) { /* ignore */ }
    },
    refreshData() { this.fetchData() },

    renderCharts() {
      this.renderTrendChart()
      this.renderRiskLevelPie()
      this.renderRuleTypePie()
      this.renderCityBar()
      this.renderGauge()
    },

    renderTrendChart() {
      const el = this.$refs.trendChart
      if (!el) return
      if (!this.charts.trend) this.charts.trend = echarts.init(el)
      const d = this.dashboardData
      let data = [], name = ''
      if (this.trendType === 'transaction') { data = d?.transactionTrend || []; name = '交易量' }
      else if (this.trendType === 'alert') { data = d?.alertTrend || []; name = '告警量' }
      else { data = d?.blockRateTrend || []; name = '拦截率(%)' }

      this.charts.trend.setOption({
        tooltip: {
          trigger: 'axis',
          backgroundColor: '#121E33',
          borderColor: '#1C2B42',
          textStyle: { color: '#D8DFE8', fontSize: 11 }
        },
        grid: { left: 40, right: 10, top: 8, bottom: 22 },
        xAxis: {
          type: 'category', data: data.map(p => p.time),
          axisLine: { lineStyle: { color: '#1C2B42' } },
          axisLabel: { color: '#5D6F85', fontSize: 10 },
          axisTick: { show: false }
        },
        yAxis: {
          type: 'value',
          axisLine: { show: false },
          axisLabel: { color: '#5D6F85', fontSize: 10 },
          splitLine: { lineStyle: { color: '#1C2B42' } }
        },
        series: [{
          name, type: 'line',
          data: data.map(p => p.value),
          smooth: false, symbol: 'none',
          lineStyle: { color: '#4A90D9', width: 1.5 }
        }]
      }, true)
    },

    renderRiskLevelPie() {
      const el = this.$refs.riskLevelPie
      if (!el) return
      if (!this.charts.riskLevelPie) this.charts.riskLevelPie = echarts.init(el)
      const data = (this.dashboardData?.riskLevelDistribution || []).map(d => ({
        name: d.name, value: d.value, itemStyle: { color: d.color }
      }))
      this.charts.riskLevelPie.setOption({
        tooltip: {
          trigger: 'item', formatter: '{b}: {c}',
          backgroundColor: '#121E33', borderColor: '#1C2B42',
          textStyle: { color: '#D8DFE8', fontSize: 11 }
        },
        series: [{
          type: 'pie', radius: ['50%', '70%'], center: ['50%', '52%'],
          label: { color: '#5D6F85', fontSize: 10 },
          data
        }]
      }, true)
    },

    renderRuleTypePie() {
      const el = this.$refs.ruleTypePie
      if (!el) return
      if (!this.charts.ruleTypePie) this.charts.ruleTypePie = echarts.init(el)
      const colors = ['#4A90D9', '#D4952A', '#D94A4A', '#7B8CAA', '#4FA3B8']
      const data = (this.dashboardData?.ruleTypeDistribution || []).map((d, i) => ({
        name: d.name, value: d.value, itemStyle: { color: d.color || colors[i] }
      }))
      this.charts.ruleTypePie.setOption({
        tooltip: {
          trigger: 'item', formatter: '{b}: {c}',
          backgroundColor: '#121E33', borderColor: '#1C2B42',
          textStyle: { color: '#D8DFE8', fontSize: 11 }
        },
        series: [{
          type: 'pie', radius: '65%', center: ['50%', '52%'],
          label: { color: '#5D6F85', fontSize: 10 },
          data
        }]
      }, true)
    },

    renderCityBar() {
      const el = this.$refs.cityBar
      if (!el) return
      if (!this.charts.cityBar) this.charts.cityBar = echarts.init(el)
      const data = this.dashboardData?.cityDistribution || []
      this.charts.cityBar.setOption({
        tooltip: {
          trigger: 'axis',
          backgroundColor: '#121E33', borderColor: '#1C2B42',
          textStyle: { color: '#D8DFE8', fontSize: 11 }
        },
        grid: { left: 40, right: 8, top: 4, bottom: 22 },
        xAxis: {
          type: 'category', data: data.map(d => d.name),
          axisLine: { lineStyle: { color: '#1C2B42' } },
          axisLabel: { color: '#5D6F85', fontSize: 9, rotate: 30 },
          axisTick: { show: false }
        },
        yAxis: {
          type: 'value',
          axisLine: { show: false },
          axisLabel: { color: '#5D6F85', fontSize: 10 },
          splitLine: { lineStyle: { color: '#1C2B42' } }
        },
        series: [{
          type: 'bar',
          data: data.map(d => ({ value: d.value, itemStyle: { color: '#4A90D9' } })),
          barWidth: '55%'
        }]
      }, true)
    },

    renderGauge() {
      const el = this.$refs.gaugeChart
      if (!el) return
      if (!this.charts.gauge) this.charts.gauge = echarts.init(el)
      const score = this.dashboardData?.avgRiskScore || 0
      this.charts.gauge.setOption({
        series: [{
          type: 'gauge',
          startAngle: 210, endAngle: -30,
          min: 0, max: 100, splitNumber: 10,
          axisLine: {
            lineStyle: {
              color: [[0.2, '#22C55E'], [0.4, '#F59E0B'], [0.6, '#F97316'], [1, '#DC2626']],
              width: 6
            }
          },
          pointer: { length: '55%', width: 4, itemStyle: { color: '#9AACBF' } },
          axisTick: { distance: -6, length: 4, lineStyle: { color: '#5D6F85', width: 1 } },
          splitLine: { distance: -10, length: 12, lineStyle: { color: '#5D6F85', width: 1.5 } },
          axisLabel: { color: '#5D6F85', distance: 16, fontSize: 9 },
          detail: {
            formatter: '{value}',
            color: '#D8DFE8',
            fontSize: 24,
            fontFamily: 'monospace',
            offsetCenter: [0, '70%']
          },
          data: [{ value: score, name: '' }]
        }]
      }, true)
    },

    fmtNum(n) { return n?.toLocaleString?.() || n },
    scoreType(s) { return s > 120 ? '' : s >= 71 ? 'danger' : s >= 41 ? 'warning' : 'success' },
    riskClass(l) {
      if (l === '极度危险') return 'critical'
      if (l === '高危') return 'danger'
      if (l === '中危') return 'warning'
      return 'success'
    },
    actionTagType(a) { return ACTION_MAP[a]?.type || 'info' },
    actionLabel(a) { return ACTION_MAP[a]?.label || a }
  }
}
</script>

<style scoped>
.dashboard { min-height: 100%; }

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

.header-meta {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.meta-refresh {
  color: var(--color-text-muted);
  font-size: var(--text-xs);
  font-family: var(--font-mono);
}

.dot {
  width: 6px; height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

.dot.online { background: var(--color-success); }

/* Metric Cards */
.metric-row { margin-bottom: 0; }

.metric-card {
  background: var(--color-bg-elevated);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: var(--space-3) var(--space-4);
  position: relative;
  overflow: hidden;
}

.card-label {
  color: var(--color-text-muted);
  font-size: var(--text-xs);
  margin-bottom: 2px;
}

.card-value {
  font-family: var(--font-mono);
  font-size: 20px;
  font-weight: 600;
  color: var(--color-text-primary);
  line-height: 1.2;
}

.card-line {
  position: absolute;
  bottom: 0; left: 0; right: 0;
  height: 2px;
}

.card-line.neutral  { background: var(--color-text-muted); }
.card-line.success  { background: var(--color-success); }
.card-line.warning  { background: var(--color-warning); }
.card-line.danger   { background: var(--color-danger); }

/* Panel */
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

/* Risk text */
.risk-text { font-weight: 600; font-size: var(--text-sm); }
.risk-text.critical { color: var(--color-critical); }
.risk-text.danger  { color: var(--color-danger); }
.risk-text.warning { color: var(--color-warning); }
.risk-text.success { color: var(--color-success); }

.text-success { color: var(--color-success); font-size: var(--text-xs); }
.text-warning { color: var(--color-warning); font-size: var(--text-xs); }

.mono-num { font-family: var(--font-mono); font-size: var(--text-sm); }

.score-critical-tag {
  display: inline-block;
  padding: 0 6px;
  height: 20px;
  line-height: 20px;
  font-size: 11px;
  font-weight: 700;
  font-family: var(--font-mono);
  color: #fff;
  background: #991B1B;
  border-radius: 2px;
}
</style>
