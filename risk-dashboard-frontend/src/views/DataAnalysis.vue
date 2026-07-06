<template>
  <div class="full-page">
    <div class="page-header"><h2 class="page-title">数据统计分析</h2></div>

    <div class="charts-grid">
      <div class="panel flex-1">
        <div class="panel-hd"><span>风险等级分布 (24H)</span></div>
        <div ref="riskChart" class="chart-fill"></div>
      </div>
      <div class="panel flex-1">
        <div class="panel-hd"><span>触发规则类型分布</span></div>
        <div ref="ruleChart" class="chart-fill"></div>
      </div>
      <div class="panel panel-wide">
        <div class="panel-hd"><span>24H 风险趋势对比</span></div>
        <div ref="trendChart" class="chart-fill"></div>
      </div>
    </div>
  </div>
</template>

<script>
import * as echarts from 'echarts'
import { getRiskLevelStat, getRuleTypeStat } from '@/api/alert'
import { getMetricsTrend } from '@/api/metrics'
import { REFRESH_INTERVAL } from '@/utils/constants'

export default {
  name: 'DataAnalysis',
  data() { return { charts: {}, timer: null } },
  mounted() { this.loadData(); this.timer = setInterval(() => this.loadData(), REFRESH_INTERVAL * 2) },
  beforeDestroy() { clearInterval(this.timer); Object.values(this.charts).forEach(c => c.dispose()) },
  methods: {
    riskPieColor(n) { const m = { '极度危险': '#DC2626', '高危': '#F97316', '中危': '#F59E0B', '低危': '#22C55E' }; return m[n] || '#909399' },
    async loadData() {
      try {
        const [r1, r2, r3] = await Promise.all([getRiskLevelStat(), getRuleTypeStat(), getMetricsTrend(24)])
        this.$nextTick(() => {
          if (r1.code === 200) this.renderRiskPie(r1.data)
          if (r2.code === 200) this.renderRulePie(r2.data)
          if (r3.code === 200) this.renderTrend(r3.data)
        })
      } catch {}
    },
    renderRiskPie(data) {
      const el = this.$refs.riskChart; if (!el) return
      if (!this.charts.risk) this.charts.risk = echarts.init(el)
      this.charts.risk.setOption({
        tooltip: { trigger: 'item', formatter: '{b}: {c}', backgroundColor: '#121E33', borderColor: '#1C2B42', textStyle: { color: '#D8DFE8', fontSize: 10 } },
        series: [{ type: 'pie', radius: ['50%', '72%'], center: ['50%', '52%'], label: { color: '#5D6F85', fontSize: 10 },
          data: data.map(d => ({ name: d.name, value: d.value, itemStyle: { color: d.color || this.riskPieColor(d.name) } })) }]
      }, true)
    },
    renderRulePie(data) {
      const el = this.$refs.ruleChart; if (!el) return
      if (!this.charts.rule) this.charts.rule = echarts.init(el)
      const colors = ['#4A90D9', '#F59E0B', '#F97316', '#7B8CAA', '#4FA3B8', '#22C55E']
      this.charts.rule.setOption({
        tooltip: { trigger: 'item', formatter: '{b}: {c}', backgroundColor: '#121E33', borderColor: '#1C2B42', textStyle: { color: '#D8DFE8', fontSize: 10 } },
        series: [{ type: 'pie', radius: '68%', center: ['50%', '52%'], label: { color: '#5D6F85', fontSize: 10 },
          data: data.map((d, i) => ({ name: d.name, value: d.value, itemStyle: { color: d.color || colors[i] } })) }]
      }, true)
    },
    renderTrend(data) {
      const el = this.$refs.trendChart; if (!el) return
      if (!this.charts.trend) this.charts.trend = echarts.init(el)
      const times = data.map(d => { const dt = new Date(d.snapshotTime); return dt.getHours() + ':' + String(dt.getMinutes()).padStart(2, '0') })
      this.charts.trend.setOption({
        tooltip: { trigger: 'axis', backgroundColor: '#121E33', borderColor: '#1C2B42', textStyle: { color: '#D8DFE8', fontSize: 10 } },
        legend: { data: ['极度危险', '高危', '中危', '低危'], textStyle: { color: '#5D6F85', fontSize: 10 }, top: 0 },
        grid: { left: 44, right: 12, top: 30, bottom: 18 },
        xAxis: { type: 'category', data: times, axisLine: { lineStyle: { color: '#1C2B42' } }, axisLabel: { color: '#5D6F85', fontSize: 9 }, axisTick: { show: false } },
        yAxis: { type: 'value', axisLine: { show: false }, axisLabel: { color: '#5D6F85', fontSize: 9 }, splitLine: { lineStyle: { color: '#1C2B42' } } },
        series: [
          { name: '极度危险', type: 'line', data: data.map(d => d.criticalRiskCount || 0), smooth: false, symbol: 'none', lineStyle: { color: '#DC2626', width: 1 } },
          { name: '高危', type: 'line', data: data.map(d => d.highRiskCount), smooth: false, symbol: 'none', lineStyle: { color: '#F97316', width: 1 } },
          { name: '中危', type: 'line', data: data.map(d => d.mediumRiskCount), smooth: false, symbol: 'none', lineStyle: { color: '#F59E0B', width: 1 } },
          { name: '低危', type: 'line', data: data.map(d => d.lowRiskCount), smooth: false, symbol: 'none', lineStyle: { color: '#22C55E', width: 1 } }
        ]
      }, true)
    }
  }
}
</script>

<style scoped>
.full-page { height: 100%; display: flex; flex-direction: column; overflow: hidden; }
.page-header { padding-bottom: 6px; border-bottom: 1px solid var(--color-border); flex-shrink: 0; }
.page-title { color: var(--color-text-primary); font-size: 15px; font-weight: 600; margin: 0; }

.charts-grid { display: flex; gap: 8px; flex: 1; min-height: 0; padding-top: 6px; }
.panel-wide { flex: 2; }
.flex-1 { flex: 1; min-height: 0; }
.panel { background: var(--color-bg-elevated); border: 1px solid var(--color-border); border-radius: var(--radius-sm); padding: 6px 8px; display: flex; flex-direction: column; }
.panel-hd { padding-bottom: 4px; margin-bottom: 2px; border-bottom: 1px solid var(--color-border); font-size: 11px; color: var(--color-text-secondary); font-weight: 500; flex-shrink: 0; }
.chart-fill { flex: 1; min-height: 0; }
</style>
