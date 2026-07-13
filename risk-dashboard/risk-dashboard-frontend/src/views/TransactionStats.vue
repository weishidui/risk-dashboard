<template>
  <div class="trans-stats-page">
    <div class="page-header">
      <div class="header-left">
        <h2 class="page-title">交易行为统计</h2>
      </div>
      <div class="header-right">
        <el-button size="mini" icon="el-icon-back" @click="$router.push('/offline-overview')">返回离线总览</el-button>
      </div>
    </div>

    <!-- 总览 -->
    <div class="stats-row">
      <div class="stat-item"><div class="stat-num muted">{{ summary.totalTransactions | num }}</div><div class="stat-label">总交易量</div></div>
      <div class="stat-item"><div class="stat-num muted">{{ summary.recent30dTransactions | num }}</div><div class="stat-label">近30天交易</div></div>
      <div class="stat-item"><div class="stat-num muted">{{ summary.distinctUsers | num }}</div><div class="stat-label">用户数</div></div>
      <div class="stat-item"><div class="stat-num muted">{{ summary.distinctDevices | num }}</div><div class="stat-label">设备数</div></div>
    </div>

    <!-- 图表区 -->
    <el-row :gutter="10" style="margin-top:10px;">
      <el-col :span="12">
        <div class="panel">
          <div class="panel-header"><span class="panel-title">每日交易量趋势（近30天）</span></div>
          <div ref="trendChart" class="chart-box"></div>
        </div>
      </el-col>
      <el-col :span="12">
        <div class="panel">
          <div class="panel-header"><span class="panel-title">金额区间分布</span></div>
          <div ref="amountChart" class="chart-box"></div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="10" style="margin-top:10px;">
      <el-col :span="8">
        <div class="panel">
          <div class="panel-header"><span class="panel-title">支付渠道占比</span></div>
          <div ref="payChannelChart" class="chart-box-sm"></div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="panel">
          <div class="panel-header"><span class="panel-title">交易类型分布</span></div>
          <div ref="transTypeChart" class="chart-box-sm"></div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="panel">
          <div class="panel-header"><span class="panel-title">城市 Top10</span></div>
          <div ref="cityChart" class="chart-box-sm"></div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import * as echarts from 'echarts'
import { getDailyTrend, getPayChannelDist, getTransTypeDist, getAmountRangeDist, getCityRank, getTransSummary } from '@/api/transaction-stats'
import { CHART_COLORS } from '@/utils/constants'

export default {
  name: 'TransactionStats',
  data() {
    return { summary: {}, charts: {} }
  },
  filters: { num(v) { return v ? Number(v).toLocaleString() : '0' } },
  async mounted() {
    await this.loadData()
  },
  beforeDestroy() {
    Object.values(this.charts).forEach(c => c.dispose())
  },
  methods: {
    async loadData() {
      try {
        const [trend, pay, type, amount, city, sum] = await Promise.all([
          getDailyTrend(30), getPayChannelDist(), getTransTypeDist(),
          getAmountRangeDist(), getCityRank(10), getTransSummary()
        ])
        if (sum.code === 200) this.summary = sum.data
        this.$nextTick(() => {
          if (trend.code === 200) this.renderTrend(trend.data)
          if (pay.code === 200) this.renderPie('payChannelChart', pay.data)
          if (type.code === 200) this.renderPie('transTypeChart', type.data)
          if (amount.code === 200) this.renderPie('amountChart', amount.data)
          if (city.code === 200) this.renderBar(city.data)
        })
      } catch (e) { /* ignore */ }
    },

    renderTrend(data) {
      const el = this.$refs.trendChart; if (!el) return
      if (!this.charts.trend) this.charts.trend = echarts.init(el)
      this.charts.trend.setOption({
        tooltip: { trigger: 'axis', backgroundColor: '#121E33', borderColor: '#1C2B42', textStyle: { color: '#D8DFE8', fontSize: 11 } },
        grid: { left: 50, right: 12, top: 10, bottom: 24 },
        xAxis: { type: 'category', data: data.map(d => d.date), axisLabel: { color: '#5D6F85', fontSize: 10, rotate: 30 }, axisLine: { lineStyle: { color: '#1C2B42' } } },
        yAxis: { type: 'value', axisLabel: { color: '#5D6F85', fontSize: 10 }, splitLine: { lineStyle: { color: '#1C2B42' } } },
        series: [{ type: 'line', data: data.map(d => d.count), smooth: true, symbol: 'none', lineStyle: { color: '#3B82F6', width: 1.5 }, areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(59,130,246,0.2)' }, { offset: 1, color: 'rgba(59,130,246,0)' }]) } }]
      }, true)
    },

    renderPie(refName, data) {
      const el = this.$refs[refName]; if (!el) return
      if (!this.charts[refName]) this.charts[refName] = echarts.init(el)
      this.charts[refName].setOption({
        tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)', backgroundColor: '#121E33', borderColor: '#1C2B42', textStyle: { color: '#D8DFE8', fontSize: 11 } },
        series: [{
          type: 'pie', radius: ['40%', '68%'], center: ['50%', '52%'],
          label: { color: '#5D6F85', fontSize: 10 },
          data: data.map((d, i) => ({ name: d.name, value: d.value, itemStyle: { color: CHART_COLORS[i % CHART_COLORS.length] } }))
        }]
      }, true)
    },

    renderBar(data) {
      const el = this.$refs.cityChart; if (!el) return
      if (!this.charts.cityBar) this.charts.cityBar = echarts.init(el)
      const d = data.slice(0, 10).reverse()
      this.charts.cityBar.setOption({
        tooltip: { trigger: 'axis', backgroundColor: '#121E33', borderColor: '#1C2B42', textStyle: { color: '#D8DFE8', fontSize: 11 } },
        grid: { left: 55, right: 20, top: 4, bottom: 4 },
        xAxis: { type: 'value', axisLine: { show: false }, axisLabel: { color: '#5D6F85', fontSize: 9 }, splitLine: { lineStyle: { color: '#1C2B42', type: 'dashed' } } },
        yAxis: { type: 'category', data: d.map(v => v.name), axisLabel: { color: '#9AACBF', fontSize: 9 }, axisTick: { show: false } },
        series: [{ type: 'bar', data: d.map((v, i) => ({ value: v.value, itemStyle: { color: CHART_COLORS[i % CHART_COLORS.length], borderRadius: [0, 2, 2, 0] } })), barWidth: '55%' }]
      }, true)
    }
  }
}
</script>

<style scoped>
.trans-stats-page { height: 100%; overflow-y: auto; }
.page-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; margin-bottom: var(--space-3); padding-bottom: var(--space-3); border-bottom: 1px solid var(--color-border); }
.page-title { color: var(--color-text-primary); font-size: var(--text-lg); font-weight: 600; margin: 0; }
.header-left { display: flex; align-items: center; gap: var(--space-3); }
.header-right { display: flex; align-items: center; gap: var(--space-2); }
.header-link { font-size: 11px; color: var(--color-text-muted); text-decoration: none; padding: 3px 8px; border-radius: var(--radius-sm); transition: all 0.2s; }
.header-link:hover { color: var(--color-primary); background: var(--color-bg-hover); }
.stats-row { display: flex; gap: var(--space-2); margin-bottom: var(--space-3); }
.stat-item { flex: 1; text-align: center; padding: var(--space-3); background: var(--color-bg-elevated); border: 1px solid var(--color-border); border-radius: var(--radius-sm); }
.stat-num { font-family: var(--font-mono); font-size: 22px; font-weight: 600; color: var(--color-text-primary); }
.stat-num.muted { color: var(--color-text-secondary); }
.stat-label { font-size: var(--text-xs); color: var(--color-text-muted); margin-top: 2px; }
.panel { background: var(--color-bg-elevated); border: 1px solid var(--color-border); border-radius: var(--radius-sm); padding: var(--space-3); }
.panel-header { margin-bottom: var(--space-2); padding-bottom: var(--space-2); border-bottom: 1px solid var(--color-border); }
.panel-title { color: var(--color-text-secondary); font-size: var(--text-sm); font-weight: 500; }
.chart-box { width: 100%; height: 280px; }
.chart-box-sm { width: 100%; height: 220px; }
</style>
