<template>
  <div class="offline-dashboard">
    <header class="page-header">
      <div>
        <h2>离线风险分析</h2>
        <div class="window-line">批次 {{ overview.dt || '-' }} <span>{{ overview.windowStart || '-' }} 至 {{ overview.windowEnd || '-' }}</span></div>
      </div>
      <div class="header-actions">
        <span class="task-state" :class="analysis.status">{{ analysis.message }}</span>
        <el-button size="mini" type="primary" :loading="analysis.running" @click="triggerAnalysis">分析近30天</el-button>
        <el-button size="mini" icon="el-icon-refresh" @click="refresh">刷新</el-button>
      </div>
    </header>

    <section class="kpi-grid">
      <div v-for="item in kpis" :key="item.label" class="kpi">
        <div class="kpi-value">{{ formatNumber(item.value) }}</div>
        <div class="kpi-label">{{ item.label }}</div>
        <div class="kpi-note">{{ item.note }}</div>
      </div>
    </section>

    <section class="dashboard-grid primary-grid">
      <article class="panel trend-panel">
        <div class="panel-head"><h3>风险时间趋势</h3><span>按天汇总</span></div>
        <div ref="trendChart" class="chart chart-lg"></div>
      </article>
      <article class="panel score-panel">
        <div class="panel-head"><h3>风险评分分布</h3><span>交易笔数</span></div>
        <div ref="scoreChart" class="chart chart-lg"></div>
      </article>
    </section>

    <section class="dashboard-grid ranking-grid">
      <article class="panel">
        <div class="panel-head"><h3>省份风险排行</h3><span>Top 12</span></div>
        <div ref="provinceChart" class="chart"></div>
      </article>
      <article class="panel">
        <div class="panel-head"><h3>城市风险排行</h3><span>Top 10</span></div>
        <div ref="cityChart" class="chart"></div>
      </article>
      <article class="panel">
        <div class="panel-head"><h3>规则命中排行</h3><span>Top 12</span></div>
        <div ref="ruleChart" class="chart"></div>
      </article>
    </section>

    <section class="dashboard-grid analysis-grid">
      <article class="panel">
        <div class="panel-head"><h3>风险行为分析</h3><span>{{ behaviorViewLabel }}</span></div>
        <div class="behavior-toolbar">
          <el-popover v-model="behaviorMenuVisible" placement="bottom-start" trigger="click" popper-class="behavior-menu-popper" @show="behaviorMenuGroup = ''">
            <div class="behavior-menu">
              <div class="behavior-menu-groups">
                <button v-for="group in behaviorMenuGroups" :key="group.value" type="button" :class="['behavior-menu-item', { active: behaviorMenuGroup === group.value }]" @click.stop="behaviorMenuGroup = group.value">
                  <span>{{ group.label }}</span><i class="el-icon-arrow-right" />
                </button>
              </div>
              <div v-if="activeBehaviorMenuGroup" class="behavior-menu-options">
                <button v-for="item in activeBehaviorMenuGroup.items" :key="item.value" type="button" :class="['behavior-menu-item', { selected: behaviorFeature === item.value }]" @click.stop="chooseBehaviorFeature(item.value)">{{ item.label }}</button>
              </div>
            </div>
            <button slot="reference" type="button" class="behavior-menu-trigger"><span>{{ behaviorSelectionLabel }}</span><i class="el-icon-arrow-down" /></button>
          </el-popover>
          <span class="behavior-summary">风险率 {{ formatRate(behaviorRiskRate) }}</span>
        </div>
        <div ref="behaviorChart" class="chart"></div>
      </article>
      <article class="panel">
        <div class="panel-head"><h3>风险实体排行</h3>
          <el-radio-group v-model="entityType" size="mini" @change="renderEntityChart">
            <el-radio-button label="user">用户</el-radio-button>
            <el-radio-button label="device">设备</el-radio-button>
            <el-radio-button label="counterparty">收款方</el-radio-button>
          </el-radio-group>
        </div>
        <div ref="entityChart" class="chart"></div>
      </article>
      <article class="panel flow-panel">
        <div class="panel-head"><h3>跨省风险流向</h3><span>收款方近窗口城市推断</span></div>
        <div class="flow-list">
          <div v-for="flow in data.crossRegionFlows" :key="flowKey(flow)" class="flow-row">
            <span>{{ flow.from_province }} {{ flow.from_city }}</span><b>to</b><span>{{ flow.to_province }} {{ flow.to_city }}</span><strong>{{ formatNumber(flow.risk_count) }}</strong>
          </div>
          <div v-if="!data.crossRegionFlows.length" class="empty">本批次没有跨省风险流</div>
        </div>
      </article>
    </section>

    <section class="dashboard-grid detail-grid">
      <article class="panel transaction-panel">
        <div class="panel-head"><h3>高风险交易明细</h3><span>评分大于70</span></div>
        <el-table :data="data.highRiskTransactions" height="300" size="mini" class="risk-table">
          <el-table-column prop="event_time" label="时间" width="146"><template slot-scope="scope">{{ formatTime(scope.row.event_time) }}</template></el-table-column>
          <el-table-column prop="trans_id" label="交易号" min-width="205" show-overflow-tooltip />
          <el-table-column prop="user_id" label="用户" min-width="120" show-overflow-tooltip />
          <el-table-column label="地区" width="115"><template slot-scope="scope">{{ scope.row.province }} {{ scope.row.city }}</template></el-table-column>
          <el-table-column prop="amount" label="金额" width="105" align="right"><template slot-scope="scope">{{ formatAmount(scope.row.amount) }}</template></el-table-column>
          <el-table-column prop="risk_score" label="评分" width="72" align="center" />
          <el-table-column prop="risk_level" label="等级" width="86" align="center"><template slot-scope="scope"><span :class="['risk-level', scope.row.risk_level]">{{ levelLabel(scope.row.risk_level) }}</span></template></el-table-column>
          <el-table-column prop="hit_rules" label="命中规则" min-width="145" show-overflow-tooltip />
        </el-table>
      </article>
      <article class="panel status-panel">
        <div class="panel-head"><h3>离线任务状态</h3><span>{{ tasks.length }} 项</span></div>
        <div class="task-list">
          <div v-for="task in tasks" :key="task.key" class="task-row">
            <i :class="taskStateClass(task)"></i><span>{{ task.name }}</span><b>{{ task.status }}</b>
          </div>
        </div>
      </article>
    </section>
  </div>
</template>

<script>
import * as echarts from 'echarts'
import { getOfflineDashboardData, getTaskStatus, startAnalysis, getAnalysisStatus } from '@/api/offline'

const COLORS = ['#36cfc9', '#4f8cff', '#f6bd16', '#ff7a45', '#f2637b', '#9b7bff']
const RANK_COLORS = ['#4f8cff', '#36cfc9', '#f6bd16', '#ff7a45', '#f2637b', '#9b7bff']
const BEHAVIOR_FEATURES = {
  CHANNEL: { label: '支付渠道', group: '交易行为', mode: 'pie' },
  TRANS_TYPE: { label: '交易类型', group: '交易行为', mode: 'pie' },
  INPUT_METHOD: { label: '输入模式', group: '交易行为', mode: 'pie' },
  AMOUNT_RANGE: { label: '交易金额区间', group: '交易行为', mode: 'bar' },
  TIME_OF_DAY: { label: '交易时段', group: '交易行为', mode: 'bar' },
  NETWORK_TYPE: { label: '网络环境', group: '设备与网络', mode: 'pie' },
  ROOT_STATUS: { label: '设备 Root/越狱状态', group: '设备与网络', mode: 'pie' },
  DEVICE_SCORE: { label: '设备评分区间', group: '设备与网络', mode: 'bar' },
  LOGIN_FAILURE: { label: '登录失败次数', group: '账户操作', mode: 'bar' },
  CANCEL_RETRY: { label: '取消重试次数', group: '账户操作', mode: 'bar' }
}
const FEATURE_VALUE_LABELS = {
  bank_card: '银行卡', wechat: '微信支付', alipay: '支付宝',
  autofill: '自动填充', manual: '手动输入', paste: '粘贴输入',
  WiFi: '无线网络', VPN: 'VPN/代理', '4G': '4G 网络', '5G': '5G 网络',
  ROOTED: '已 Root/越狱', NORMAL: '正常设备',
  '0-999': '1千元以下', '1K-9.9K': '1千元-1万元', '10K-49.9K': '1万-5万元', '50K+': '5万元以上',
  '00-05': '00:00-05:59', '06-11': '06:00-11:59', '12-17': '12:00-17:59', '18-23': '18:00-23:59'
}
const FEATURE_VALUE_ORDER = {
  AMOUNT_RANGE: ['0-999', '1K-9.9K', '10K-49.9K', '50K+'],
  TIME_OF_DAY: ['00-05', '06-11', '12-17', '18-23'],
  DEVICE_SCORE: ['0-39', '40-59', '60-79', '80-100'],
  LOGIN_FAILURE: ['0', '1', '2', '3+'],
  CANCEL_RETRY: ['0', '1', '2+']
}

export default {
  name: 'OfflineOverview',
  data() {
    return {
      data: { overview: {}, scoreDistribution: [], provinceRanking: [], cityRanking: [], ruleRanking: [], timeTrend: [], behaviorDistribution: [], featureDistributions: [], highRiskUsers: [], deviceRanking: [], counterpartyRanking: [], crossRegionFlows: [], highRiskTransactions: [] },
      tasks: [], taskStatusTimer: null, entityType: 'user', behaviorFeature: 'CHANNEL', behaviorMenuVisible: false, behaviorMenuGroup: '', charts: {}, analysis: { running: false, status: '', message: '已加载最近完成批次' }, timer: null
    }
  },
  computed: {
    overview() { return this.data.overview || {} },
    kpis() {
      const o = this.overview
      return [
        { label: '清洗交易', value: o.dwdTransCount, note: '离线窗口总量' },
        { label: '风险交易', value: o.riskUserCount ? this.riskTransactions : 0, note: '评分大于0' },
        { label: '高风险用户', value: o.highRiskUserCount, note: '评分大于70' },
        { label: '窗口用户', value: o.userProfileCount, note: '去重用户数' },
        { label: '窗口设备', value: o.deviceCount, note: '窗口去重设备' },
        { label: '窗口收款方', value: o.counterpartyCount, note: '窗口去重收款方' }
      ]
    },
    riskTransactions() { return (this.data.scoreDistribution || []).filter(item => item.name !== '0').reduce((sum, item) => sum + Number(item.value || 0), 0) }
    ,behaviorMenuGroups() {
      const groups = {}
      Object.keys(BEHAVIOR_FEATURES).forEach(key => {
        const feature = BEHAVIOR_FEATURES[key]
        if (!groups[feature.group]) groups[feature.group] = []
        groups[feature.group].push({ value: key, label: feature.label })
      })
      return Object.keys(groups).map(label => ({ value: label, label, items: groups[label] }))
    }
    ,activeBehaviorMenuGroup() { return this.behaviorMenuGroups.find(group => group.value === this.behaviorMenuGroup) || null }
    ,behaviorSelectionLabel() { return `${this.behaviorMeta.group} / ${this.behaviorMeta.label}` }
    ,behaviorRows() {
      const rows = (this.data.featureDistributions || []).filter(row => row.feature_key === this.behaviorFeature).slice()
      const order = FEATURE_VALUE_ORDER[this.behaviorFeature]
      if (!order) return rows
      return rows.sort((left, right) => order.indexOf(left.feature_value) - order.indexOf(right.feature_value))
    }
    ,behaviorMeta() { return BEHAVIOR_FEATURES[this.behaviorFeature] || BEHAVIOR_FEATURES.CHANNEL }
    ,behaviorViewLabel() { return `${this.behaviorMeta.label} ${this.behaviorMeta.mode === 'pie' ? '风险构成' : '风险率'}` }
    ,behaviorRiskRate() {
      const rows = this.behaviorRows
      const total = rows.reduce((sum, row) => sum + Number(row.total_count || 0), 0)
      const risk = rows.reduce((sum, row) => sum + Number(row.risk_count || 0), 0)
      return total ? risk / total : 0
    }
  },
  mounted() { this.refresh(); window.addEventListener('resize', this.resizeCharts) },
  beforeDestroy() { if (this.timer) clearInterval(this.timer); if (this.taskStatusTimer) clearTimeout(this.taskStatusTimer); window.removeEventListener('resize', this.resizeCharts); Object.keys(this.charts).forEach(key => this.charts[key].dispose()) },
  methods: {
    async refresh() {
      try {
        const dashboard = await getOfflineDashboardData()
        if (dashboard.code === 200) this.data = Object.assign(this.data, dashboard.data || {})
        this.$nextTick(() => {
          const redraw = () => {
            this.renderAll()
            this.resizeCharts()
            window.dispatchEvent(new Event('resize'))
          }
          redraw()
          window.requestAnimationFrame(redraw)
          setTimeout(redraw, 300)
          setTimeout(redraw, 1000)
        })
        this.loadTaskStatus(this.overview.dt)
      } catch (e) { this.analysis.message = '离线结果读取失败' }
    },
    async loadTaskStatus(dt) {
      try {
        const status = await getTaskStatus(dt)
        if (status.code !== 200) return
        this.tasks = status.data || []
        const checking = this.tasks.some(task => task.status === 'CHECKING')
        if (checking) {
          if (this.taskStatusTimer) clearTimeout(this.taskStatusTimer)
          this.taskStatusTimer = setTimeout(() => this.loadTaskStatus(dt), 1500)
        }
      } catch (e) { this.tasks = [] }
    },
    async triggerAnalysis() {
      if (this.analysis.running) return
      this.analysis.running = true; this.analysis.status = 'running'; this.analysis.message = '正在启动近30天分析'
      try {
        const today = this.localDt(); const response = await startAnalysis(today)
        if (response.code !== 200) throw new Error(response.message)
        if (this.timer) clearInterval(this.timer)
        this.timer = setInterval(async () => {
          const status = await getAnalysisStatus(today)
          const row = status.data || {}
          this.analysis.message = row.message || '离线任务运行中'
          if (row.status === 'SUCCESS') { clearInterval(this.timer); this.timer = null; this.analysis.running = false; this.analysis.status = 'ok'; this.analysis.message = '分析完成'; this.refresh() }
          if (row.status === 'FAILED') { clearInterval(this.timer); this.timer = null; this.analysis.running = false; this.analysis.status = 'fail'; this.analysis.message = row.message || '分析失败' }
        }, 3000)
      } catch (e) { this.analysis.running = false; this.analysis.status = 'fail'; this.analysis.message = e.message || '分析启动失败' }
    },
    renderAll() { this.renderTrend(); this.renderScore(); this.renderRank('provinceChart', this.data.provinceRanking, '省份'); this.renderRank('cityChart', this.data.cityRanking, '城市'); this.renderRule(); this.renderBehavior(); this.renderEntityChart() },
    chart(name) { if (!this.charts[name]) this.charts[name] = echarts.init(this.$refs[name]); return this.charts[name] },
    optionBase() { return { animationDuration: 360, textStyle: { color: '#b9c6d6' }, tooltip: { trigger: 'axis', backgroundColor: '#101c2c', borderColor: '#27425f', textStyle: { color: '#e7f0fa' } }, grid: { left: 48, right: 18, top: 24, bottom: 38, containLabel: true }, xAxis: { axisLine: { lineStyle: { color: '#35516f' } }, axisTick: { lineStyle: { color: '#35516f' } }, axisLabel: { color: '#8ea4ba', margin: 10, fontFamily: 'var(--font-mono)' } }, yAxis: { axisLine: { lineStyle: { color: '#35516f' } }, axisTick: { show: false }, axisLabel: { color: '#b9c6d6', margin: 8 }, splitLine: { lineStyle: { color: 'rgba(53,81,111,.56)' } } } } },
    renderTrend() { const days = {}; (this.data.timeTrend || []).forEach(row => { const day = String(row.stat_hour || '').slice(0, 8); if (!days[day]) days[day] = { risk: 0, high: 0 }; days[day].risk += Number(row.risk_count || 0); days[day].high += Number(row.high_risk_count || 0) }); const rows = Object.keys(days).sort().map(day => ({ day, risk: days[day].risk, high: days[day].high })); const option = this.optionBase(); option.legend = { data: ['风险交易', '高风险'], right: 12, textStyle: { color: '#9db1c4' } }; option.xAxis = Object.assign(option.xAxis, { type: 'category', data: rows.map(row => `${row.day.slice(4, 6)}-${row.day.slice(6, 8)}`), axisLabel: { color: '#8ea4ba' } }); option.yAxis = Object.assign(option.yAxis, { type: 'value' }); option.series = [{ name: '风险交易', type: 'line', smooth: true, symbolSize: 5, data: rows.map(row => row.risk), lineStyle: { color: COLORS[0], width: 2 }, areaStyle: { color: 'rgba(54,207,201,.13)' } }, { name: '高风险', type: 'line', smooth: true, symbolSize: 5, data: rows.map(row => row.high), lineStyle: { color: COLORS[4], width: 2 } }]; this.chart('trendChart').setOption(option, true) },
    renderScore() { const rows = this.data.scoreDistribution || []; const option = this.optionBase(); option.grid = { left: 34, right: 16, top: 24, bottom: 38, containLabel: true }; option.xAxis = Object.assign(option.xAxis, { type: 'category', data: rows.map(row => row.name) }); option.yAxis = Object.assign(option.yAxis, { type: 'value' }); option.series = [{ type: 'bar', barWidth: '56%', data: rows.map((row, index) => ({ value: row.value, itemStyle: { color: index === 0 ? '#53718d' : COLORS[index % COLORS.length] } })) }]; this.chart('scoreChart').setOption(option, true) },
    renderRank(ref, rows, suffix) { const data = (rows || []).slice().reverse(); const option = this.optionBase(); option.grid = this.horizontalGrid(); option.xAxis = Object.assign(option.xAxis, { type: 'value' }); option.yAxis = Object.assign(option.yAxis, { type: 'category', data: data.map(row => row.name), axisLabel: { color: '#b9c6d6', fontSize: 11, width: 88, overflow: 'truncate' } }); option.series = [this.horizontalSeries(data.map(row => row.risk_count), 0)]; option.tooltip.formatter = params => `${params[0].name}${suffix}<br/>风险交易 ${this.formatNumber(params[0].value)}`; this.chart(ref).setOption(option, true) },
    renderRule() { const rows = (this.data.ruleRanking || []).slice().reverse(); const option = this.optionBase(); option.grid = this.horizontalGrid(); option.xAxis = Object.assign(option.xAxis, { type: 'value' }); option.yAxis = Object.assign(option.yAxis, { type: 'category', data: rows.map(row => `${row.rule_code} ${row.rule_name}`), axisLabel: { color: '#b9c6d6', fontSize: 10, width: 110, overflow: 'truncate' } }); option.series = [this.horizontalSeries(rows.map(row => row.risk_count), 2)]; this.chart('ruleChart').setOption(option, true) },
    renderBehavior() { const rows = (this.data.behaviorDistribution || []).slice().reverse(); const option = this.optionBase(); option.grid = this.horizontalGrid(); option.xAxis = Object.assign(option.xAxis, { type: 'value' }); option.yAxis = Object.assign(option.yAxis, { type: 'category', data: rows.map(row => `${row.behavior_type === 'CHANNEL' ? '渠道' : '类型'} ${row.behavior_name}`), axisLabel: { color: '#b9c6d6', fontSize: 10, width: 110, overflow: 'truncate' } }); option.series = [this.horizontalSeries(rows.map(row => row.risk_count), COLORS[5])]; this.chart('behaviorChart').setOption(option, true) },
    renderBehavior() {
      const rows = this.behaviorRows.slice()
      const meta = this.behaviorMeta
      const chart = this.chart('behaviorChart')
      if (!rows.length) { chart.clear(); return }
      const names = rows.map(row => this.featureValueLabel(row.feature_value))
      if (meta.mode === 'pie') {
        chart.setOption({ animationDuration: 360, color: COLORS, tooltip: { trigger: 'item', backgroundColor: '#101c2c', borderColor: '#27425f', textStyle: { color: '#e7f0fa' }, formatter: params => this.behaviorTooltip(rows[params.dataIndex]) }, legend: { type: 'scroll', bottom: 0, textStyle: { color: '#9db1c4', fontSize: 10 } }, series: [{ type: 'pie', radius: ['42%', '70%'], center: ['50%', '45%'], minAngle: 4, label: { color: '#b9c6d6', fontSize: 10, formatter: '{b}\n{d}%' }, labelLine: { length: 8, length2: 6 }, data: rows.map((row, index) => ({ name: names[index], value: Number(row.risk_count || 0) })) }] }, true)
        return
      }
      const option = this.optionBase()
      option.grid = { left: 32, right: 18, top: 28, bottom: 44, containLabel: true }
      option.tooltip = { trigger: 'axis', backgroundColor: '#101c2c', borderColor: '#27425f', textStyle: { color: '#e7f0fa' }, formatter: params => this.behaviorTooltip(rows[params[0].dataIndex]) }
      option.xAxis = Object.assign(option.xAxis, { type: 'category', data: names, axisLabel: { color: '#8ea4ba', margin: 10, interval: 0, fontSize: 10 } })
      option.yAxis = Object.assign(option.yAxis, { type: 'value', axisLabel: { color: '#8ea4ba', formatter: value => `${Number(value * 100).toFixed(0)}%` }, splitLine: { lineStyle: { color: 'rgba(53,81,111,.56)' } } })
      option.series = [{ type: 'bar', barMaxWidth: 34, data: rows.map((row, index) => ({ value: Number(row.risk_rate || 0), itemStyle: { color: COLORS[index % COLORS.length] } })), label: { show: true, position: 'top', color: '#b9c6d6', fontSize: 10, formatter: value => this.formatRate(value.value) } }]
      chart.setOption(option, true)
    },
    renderEntityChart() { const maps = { user: this.data.highRiskUsers, device: this.data.deviceRanking, counterparty: this.data.counterpartyRanking }; const rows = (maps[this.entityType] || []).slice(0, 10).reverse(); const option = this.optionBase(); option.grid = this.horizontalGrid(); option.xAxis = Object.assign(option.xAxis, { type: 'value' }); option.yAxis = Object.assign(option.yAxis, { type: 'category', data: rows.map(row => row.name), axisLabel: { color: '#b9c6d6', fontSize: 10, width: 110, overflow: 'truncate' } }); option.series = [this.horizontalSeries(rows.map(row => row.risk_count), 4)]; this.chart('entityChart').setOption(option, true) },
    horizontalGrid() { return { left: 14, right: 52, top: 14, bottom: 34, containLabel: true } },
    horizontalSeries(data, colorOffset) { return { type: 'bar', data: data.map((value, index) => ({ value, itemStyle: { color: RANK_COLORS[(index + colorOffset) % RANK_COLORS.length] } })), barWidth: '54%', barMinHeight: 7, showBackground: true, backgroundStyle: { color: 'rgba(91,118,148,.12)' }, itemStyle: { borderRadius: [0, 3, 3, 0] }, label: { show: true, position: 'right', color: '#b9c6d6', fontSize: 11, fontWeight: 600, formatter: value => this.formatNumber(value.value) } } },
    resizeCharts() { Object.keys(this.charts).forEach(key => this.charts[key].resize()) },
    featureValueLabel(value) { return FEATURE_VALUE_LABELS[value] || value || '未知' },
    chooseBehaviorFeature(value) { this.behaviorFeature = value; this.behaviorMenuVisible = false; this.renderBehavior() },
    taskStateClass(task) { return task.status === 'CHECKING' ? 'checking' : task.ok ? 'ok' : 'fail' },
    formatRate(value) { return `${(Number(value || 0) * 100).toFixed(2)}%` },
    behaviorTooltip(row) { return `${this.featureValueLabel(row.feature_value)}<br/>总交易 ${this.formatNumber(row.total_count)}<br/>风险交易 ${this.formatNumber(row.risk_count)}<br/>风险率 ${this.formatRate(row.risk_rate)}<br/>高风险率 ${this.formatRate(row.high_risk_rate)}<br/>平均评分 ${Number(row.avg_risk_score || 0).toFixed(2)}` },
    formatNumber(value) { return Number(value || 0).toLocaleString() }, formatAmount(value) { return Number(value || 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 }) },
    formatTime(value) { if (!value) return '-'; const date = new Date(Number(value)); return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}` },
    hourLabel(value) { const raw = String(value || ''); return raw.length === 10 ? `${raw.slice(4, 6)}-${raw.slice(6, 8)} ${raw.slice(8, 10)}:00` : raw },
    levelLabel(level) { return { HIGH: '高危', EXTREME: '极度危险', MEDIUM: '中危', LOW: '低危' }[level] || level },
    flowKey(flow) { return [flow.from_province, flow.from_city, flow.to_province, flow.to_city].join('-') },
    localDt() { const d = new Date(); return `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, '0')}${String(d.getDate()).padStart(2, '0')}` }
  }
}
</script>

<style scoped>
.offline-dashboard { height: 100%; overflow-y: auto; padding: 2px 2px 18px; color: var(--color-text-primary); }
.page-header { display: flex; justify-content: space-between; align-items: center; gap: 14px; padding: 0 2px 13px; border-bottom: 1px solid var(--color-border); }
.page-header h2 { margin: 0; font-size: 18px; font-weight: 600; color: var(--color-text-primary); }
.window-line { margin-top: 6px; color: var(--color-text-muted); font-size: 12px; }.window-line span { margin-left: 10px; color: var(--color-text-secondary); }
.header-actions { display: flex; align-items: center; gap: 8px; }.task-state { font-size: 12px; color: var(--color-text-muted); }.task-state.ok { color: var(--color-success); }.task-state.fail { color: var(--color-danger); }.task-state.running { color: var(--color-warning); }
.kpi-grid { display: grid; grid-template-columns: repeat(6, minmax(120px, 1fr)); gap: 8px; margin: 10px 0; }.kpi { min-height: 76px; padding: 10px 12px; background: var(--color-bg-elevated); border: 1px solid var(--color-border); border-radius: var(--radius-sm); }.kpi-value { color: var(--color-primary); font: 700 22px var(--font-mono); line-height: 28px; }.kpi-label { margin-top: 5px; font-size: 12px; color: var(--color-text-secondary); }.kpi-note { margin-top: 2px; font-size: 10px; color: var(--color-text-muted); }
.dashboard-grid { display: grid; gap: 10px; margin-top: 10px; }.primary-grid { grid-template-columns: 2fr 1fr; }.ranking-grid, .analysis-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }.detail-grid { grid-template-columns: 3fr 1fr; }.panel { min-width: 0; padding: 10px; background: var(--color-bg-elevated); border: 1px solid var(--color-border); border-radius: var(--radius-sm); }.panel-head { min-height: 25px; display: flex; align-items: center; justify-content: space-between; gap: 8px; padding-bottom: 8px; border-bottom: 1px solid var(--color-border); }.panel-head h3 { margin: 0; font-size: 13px; font-weight: 600; color: var(--color-text-primary); }.panel-head span { color: var(--color-text-muted); font-size: 10px; white-space: nowrap; }.chart { height: 250px; width: 100%; }.chart-lg { height: 270px; }
.behavior-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 8px 0 0; }.behavior-menu-trigger { width: 178px; height: 28px; display: inline-flex; align-items: center; justify-content: space-between; padding: 0 8px; color: var(--color-text-primary); background: var(--color-bg-deep); border: 1px solid var(--color-border); border-radius: var(--radius-sm); font: 12px var(--font-body); cursor: pointer; }.behavior-menu-trigger:hover, .behavior-menu-trigger:focus { border-color: var(--color-primary); outline: none; }.behavior-menu-trigger i { color: var(--color-text-muted); font-size: 11px; }.behavior-summary { color: var(--color-primary); font: 11px var(--font-mono); white-space: nowrap; }
.flow-list { height: 250px; overflow-y: auto; padding-top: 4px; }.flow-row { display: grid; grid-template-columns: minmax(0, 1fr) 24px minmax(0, 1fr) 46px; gap: 4px; align-items: center; min-height: 29px; border-bottom: 1px solid rgba(53,81,111,.42); color: var(--color-text-secondary); font-size: 11px; }.flow-row span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.flow-row b { color: var(--color-primary); font-size: 10px; }.flow-row strong { color: var(--color-warning); text-align: right; font-family: var(--font-mono); }.empty { padding: 36px 0; color: var(--color-text-muted); text-align: center; font-size: 12px; }
.risk-table { margin-top: 8px; width: 100%; background: transparent; }.risk-level { padding: 2px 5px; border-radius: 2px; font-size: 11px; }.risk-level.HIGH { color: #ff8b72; background: rgba(255,122,69,.14); }.risk-level.EXTREME { color: #ff5b70; background: rgba(242,99,123,.14); }.risk-level.MEDIUM { color: #f6bd16; }.risk-level.LOW { color: #36cfc9; }
.task-list { height: 300px; overflow-y: auto; padding-top: 4px; }.task-row { display: grid; grid-template-columns: 9px minmax(0,1fr) auto; gap: 8px; align-items: center; min-height: 28px; border-bottom: 1px solid rgba(53,81,111,.4); color: var(--color-text-secondary); font-size: 11px; }.task-row i { width: 7px; height: 7px; border-radius: 50%; }.task-row i.ok { background: var(--color-success); }.task-row i.checking { background: var(--color-warning); animation: task-pulse 1.1s ease-in-out infinite; }.task-row i.fail { background: var(--color-danger); }.task-row span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.task-row b { color: var(--color-text-muted); font: 10px var(--font-mono); }@keyframes task-pulse { 50% { opacity: .35; } }
@media (max-width: 1280px) { .kpi-grid { grid-template-columns: repeat(3, 1fr); }.ranking-grid, .analysis-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }.detail-grid { grid-template-columns: 1fr; } }
</style>

<style>
.behavior-menu-popper { padding: 0 !important; background: var(--color-bg-elevated) !important; border: 1px solid var(--color-border) !important; border-radius: var(--radius-sm) !important; box-shadow: 0 8px 22px rgba(0, 0, 0, .18) !important; }
.behavior-menu-popper .popper__arrow { border-bottom-color: var(--color-border) !important; }.behavior-menu-popper .popper__arrow::after { border-bottom-color: var(--color-bg-elevated) !important; }
.behavior-menu { display: flex; min-width: 292px; min-height: 164px; font: 12px var(--font-body); }.behavior-menu-groups, .behavior-menu-options { width: 146px; padding: 5px; }.behavior-menu-options { border-left: 1px solid var(--color-border); }.behavior-menu-item { width: 100%; height: 31px; display: flex; align-items: center; justify-content: space-between; padding: 0 8px; color: var(--color-text-secondary); background: transparent; border: 0; border-radius: var(--radius-sm); font: inherit; text-align: left; cursor: pointer; }.behavior-menu-item:hover, .behavior-menu-item.active { color: var(--color-primary); background: var(--color-bg-hover); }.behavior-menu-item.selected { color: var(--color-primary); font-weight: 600; }.behavior-menu-item i { color: var(--color-text-muted); font-size: 11px; }
</style>
