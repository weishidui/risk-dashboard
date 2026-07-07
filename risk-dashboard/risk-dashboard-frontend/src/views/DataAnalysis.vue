<template>
  <div class="data-analysis">
    <div class="page-header">
      <h2 class="page-title">数据统计分析</h2>
      <el-button size="mini" icon="el-icon-back" @click="$router.push('/dashboard')">返回主页</el-button>
    </div>

    <!-- Gauge + 两个饼图 -->
    <el-row :gutter="10">
      <el-col :span="8">
        <div class="panel">
          <div class="panel-header">
            <span class="panel-title">实时风险指数</span>
          </div>
          <div ref="gaugeChart" class="chart-box"></div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="panel">
          <div class="panel-header">
            <span class="panel-title">风险等级分布 (24H)</span>
          </div>
          <div ref="riskLevelChart" class="chart-box"></div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="panel">
          <div class="panel-header">
            <span class="panel-title">触发规则类型分布</span>
          </div>
          <div ref="ruleTypeChart" class="chart-box"></div>
        </div>
      </el-col>
    </el-row>

    <!-- 24H 趋势 -->
    <el-row :gutter="10" style="margin-top:10px;">
      <el-col :span="24">
        <div class="panel">
          <div class="panel-header">
            <span class="panel-title">24H 风险趋势对比</span>
          </div>
          <div ref="trendChart" class="chart-box-lg"></div>
        </div>
      </el-col>
    </el-row>

    <!-- 城市活跃度 + 触发规则 -->
    <el-row :gutter="10" style="margin-top:10px;">
      <el-col :span="12">
        <div class="panel">
          <div class="panel-header">
            <span class="panel-title">城市活跃度 Top10</span>
          </div>
          <div ref="cityBar" class="chart-box"></div>
        </div>
      </el-col>
      <el-col :span="12">
        <div class="panel">
          <div class="panel-header">
            <span class="panel-title">触发规则 Top8</span>
          </div>
          <div ref="ruleBar" class="chart-box"></div>
        </div>
      </el-col>
    </el-row>

    <!-- 最新告警表格 -->
    <div class="panel" style="margin-top:10px;">
      <div class="panel-header">
        <span class="panel-title">最新告警</span>
        <el-button size="mini" type="primary" icon="el-icon-refresh" @click="fetchAlerts">刷新</el-button>
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
import { getRiskLevelStat, getRuleTypeStat, getRecentAlerts } from '@/api/alert'
import { getMetricsTrend, getDashboardData } from '@/api/metrics'
import { REFRESH_INTERVAL, ACTION_MAP, CHART_COLORS } from '@/utils/constants'

export default {
  name: 'DataAnalysis',
  data() {
    return {
      charts: {},
      timer: null,
      recentAlerts: []
    }
  },
  mounted() {
    this.loadData()
    this.fetchAlerts()
    this.timer = setInterval(() => { this.loadData(); this.fetchAlerts() }, REFRESH_INTERVAL * 2)
  },
  beforeDestroy() {
    clearInterval(this.timer)
    Object.values(this.charts).forEach(c => c.dispose())
  },
  methods: {
    riskPieColor(name) {
      const m = { '极度危险': '#DC2626', '高危': '#F97316', '中危': '#F59E0B', '低危': '#22C55E' }
      return m[name] || '#909399'
    },

    async loadData() {
      try {
        const [riskRes, ruleRes, trendRes, dashRes] = await Promise.all([
          getRiskLevelStat(), getRuleTypeStat(), getMetricsTrend(24), getDashboardData()
        ])
        this.$nextTick(() => {
          if (riskRes.code === 200) this.renderRiskPie(riskRes.data)
          if (ruleRes.code === 200) this.renderRulePie(ruleRes.data)
          if (trendRes.code === 200) this.renderTrend(trendRes.data)
          if (dashRes.code === 200) {
            this.renderGauge(dashRes.data)
            this.renderCityBar(dashRes.data)
            this.renderRuleBar(dashRes.data)
          }
        })
      } catch (e) { /* ignore */ }
    },

    async fetchAlerts() {
      try {
        const res = await getRecentAlerts(20)
        if (res.code === 200) this.recentAlerts = res.data || []
      } catch (e) { /* ignore */ }
    },

    /* ========== Gauge ========== */
    renderGauge(data) {
      const el = this.$refs.gaugeChart
      if (!el) return
      if (!this.charts.gauge) this.charts.gauge = echarts.init(el)
      const score = data.avgRiskScore || 0
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

    /* ========== Risk Pie ========== */
    renderRiskPie(data) {
      const el = this.$refs.riskLevelChart
      if (!el) return
      if (!this.charts.risk) this.charts.risk = echarts.init(el)
      this.charts.risk.setOption({
        tooltip: {
          trigger: 'item', formatter: '{b}: {c} ({d}%)',
          backgroundColor: '#121E33', borderColor: '#1C2B42',
          textStyle: { color: '#D8DFE8', fontSize: 11 }
        },
        series: [{
          type: 'pie', radius: ['50%', '72%'], center: ['50%', '52%'],
          label: { color: '#5D6F85', fontSize: 11 },
          data: data.map(d => ({
            name: d.name, value: d.value,
            itemStyle: { color: d.color || this.riskPieColor(d.name) }
          }))
        }]
      }, true)
    },

    /* ========== Rule Pie ========== */
    renderRulePie(data) {
      const el = this.$refs.ruleTypeChart
      if (!el) return
      if (!this.charts.rule) this.charts.rule = echarts.init(el)
      const colors = ['#4A90D9', '#D4952A', '#D94A4A', '#7B8CAA', '#4FA3B8', '#3C9D6E']
      this.charts.rule.setOption({
        tooltip: {
          trigger: 'item', formatter: '{b}: {c}',
          backgroundColor: '#121E33', borderColor: '#1C2B42',
          textStyle: { color: '#D8DFE8', fontSize: 11 }
        },
        series: [{
          type: 'pie', radius: '68%', center: ['50%', '52%'],
          label: { color: '#5D6F85', fontSize: 11 },
          data: data.map((d, i) => ({
            name: d.name, value: d.value,
            itemStyle: { color: d.color || colors[i] }
          }))
        }]
      }, true)
    },

    /* ========== Trend ========== */
    renderTrend(data) {
      const el = this.$refs.trendChart
      if (!el) return
      if (!this.charts.trend) this.charts.trend = echarts.init(el)

      const times = data.map(d => {
        const d2 = new Date(d.snapshotTime)
        return d2.getHours() + ':' + String(d2.getMinutes()).padStart(2, '0')
      })

      this.charts.trend.setOption({
        tooltip: {
          trigger: 'axis',
          backgroundColor: '#121E33', borderColor: '#1C2B42',
          textStyle: { color: '#D8DFE8', fontSize: 11 }
        },
        legend: {
          data: ['极度危险', '高危', '中危', '低危'],
          textStyle: { color: '#5D6F85', fontSize: 11 },
          top: 0
        },
        grid: { left: 44, right: 12, top: 32, bottom: 22 },
        xAxis: {
          type: 'category', data: times,
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
        series: [
          {
            name: '极度危险', type: 'line',
            data: data.map(d => d.criticalRiskCount || 0),
            smooth: false, symbol: 'none',
            lineStyle: { color: '#DC2626', width: 1.5 }
          },
          {
            name: '高危', type: 'line',
            data: data.map(d => d.highRiskCount),
            smooth: false, symbol: 'none',
            lineStyle: { color: '#F97316', width: 1.5 }
          },
          {
            name: '中危', type: 'line',
            data: data.map(d => d.mediumRiskCount),
            smooth: false, symbol: 'none',
            lineStyle: { color: '#F59E0B', width: 1.5 }
          },
          {
            name: '低危', type: 'line',
            data: data.map(d => d.lowRiskCount),
            smooth: false, symbol: 'none',
            lineStyle: { color: '#22C55E', width: 1.5 }
          }
        ]
      }, true)
    },

    /* ========== City Bar ========== */
    renderCityBar(data) {
      const el = this.$refs.cityBar
      if (!el) return
      if (!this.charts.cityBar) this.charts.cityBar = echarts.init(el)
      const d = (data.cityDistribution || []).slice(0, 10).reverse()

      this.charts.cityBar.setOption({
        tooltip: {
          trigger: 'axis',
          backgroundColor: '#0F1A2C', borderColor: '#1C2B42',
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
          data: d.map(v => v.name),
          axisLine: { show: false },
          axisTick: { show: false },
          axisLabel: { color: '#9AACBF', fontSize: 10 }
        },
        series: [{
          type: 'bar',
          data: d.map((v, i) => ({
            value: v.value,
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

    /* ========== Rule Bar ========== */
    renderRuleBar(data) {
      const el = this.$refs.ruleBar
      if (!el) return
      if (!this.charts.ruleBar) this.charts.ruleBar = echarts.init(el)
      const d = (data.ruleTypeDistribution || []).slice(0, 8).reverse()

      this.charts.ruleBar.setOption({
        tooltip: {
          trigger: 'axis',
          backgroundColor: '#0F1A2C', borderColor: '#1C2B42',
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
          data: d.map(v => v.name),
          axisLine: { show: false },
          axisTick: { show: false },
          axisLabel: { color: '#9AACBF', fontSize: 9 }
        },
        series: [{
          type: 'bar',
          data: d.map(v => ({
            value: v.value,
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

    /* ========== Helpers ========== */
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
.data-analysis { min-height: 100%; }

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

.chart-box { width: 100%; height: 280px; }
.chart-box-lg { width: 100%; height: 320px; }

/* Risk text */
.risk-text { font-weight: 600; font-size: var(--text-sm); }
.risk-text.critical { color: #DC2626; }
.risk-text.danger  { color: #F97316; }
.risk-text.warning { color: #F59E0B; }
.risk-text.success { color: #22C55E; }

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
