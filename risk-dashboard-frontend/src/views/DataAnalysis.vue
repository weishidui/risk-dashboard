<template>
  <div class="data-analysis">
    <div class="page-header">
      <h2 class="page-title">数据统计分析</h2>
    </div>

    <el-row :gutter="10">
      <el-col :span="12">
        <div class="panel">
          <div class="panel-header">
            <span class="panel-title">风险等级分布 (24H)</span>
          </div>
          <div ref="riskLevelChart" class="chart-box"></div>
        </div>
      </el-col>
      <el-col :span="12">
        <div class="panel">
          <div class="panel-header">
            <span class="panel-title">触发规则类型分布</span>
          </div>
          <div ref="ruleTypeChart" class="chart-box"></div>
        </div>
      </el-col>
    </el-row>

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
  </div>
</template>

<script>
import * as echarts from 'echarts'
import { getRiskLevelStat, getRuleTypeStat } from '@/api/alert'
import { getMetricsTrend } from '@/api/metrics'
import { REFRESH_INTERVAL } from '@/utils/constants'

export default {
  name: 'DataAnalysis',
  data() {
    return { charts: {}, timer: null }
  },
  mounted() {
    this.loadData()
    this.timer = setInterval(() => this.loadData(), REFRESH_INTERVAL * 2)
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
        const [riskRes, ruleRes, trendRes] = await Promise.all([
          getRiskLevelStat(), getRuleTypeStat(), getMetricsTrend(24)
        ])
        this.$nextTick(() => {
          if (riskRes.code === 200) this.renderRiskPie(riskRes.data)
          if (ruleRes.code === 200) this.renderRulePie(ruleRes.data)
          if (trendRes.code === 200) this.renderTrend(trendRes.data)
        })
      } catch (e) { /* ignore */ }
    },

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
    }
  }
}
</script>

<style scoped>
.data-analysis { min-height: 100%; }

.page-header {
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
</style>
