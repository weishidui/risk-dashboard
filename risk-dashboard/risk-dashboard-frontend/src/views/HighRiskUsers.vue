<template>
  <div class="high-risk-page">
    <div class="page-header">
      <div class="header-left">
        <h2 class="page-title">高风险用户排行</h2>
        <span class="header-sub">共 <b>{{ total }}</b> 人</span>
      </div>
      <div class="header-right">
        <router-link to="/profiles" class="header-link">全部用户画像</router-link>
        <el-button size="mini" icon="el-icon-back" @click="$router.push('/offline-overview')">返回离线总览</el-button>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-row">
      <div class="stat-item">
        <div class="stat-num critical">{{ stats.over80 }}</div>
        <div class="stat-label">≥ 80 分</div>
      </div>
      <div class="stat-item">
        <div class="stat-num danger">{{ stats.over40 }}</div>
        <div class="stat-label">40-79 分</div>
      </div>
      <div class="stat-item">
        <div class="stat-num warning">{{ stats.flagged }}</div>
        <div class="stat-label">已标记账户</div>
      </div>
      <div class="stat-item">
        <div class="stat-num muted">{{ stats.frozen }}</div>
        <div class="stat-label">冻结账户</div>
      </div>
    </div>

    <!-- 筛选 -->
    <div class="filter-bar">
      <el-select v-model="filters.accountStatus" clearable placeholder="账户状态" size="small" class="filter-select">
        <el-option label="已标记" value="flagged" />
        <el-option label="冻结" value="frozen" />
        <el-option label="休眠" value="dormant" />
      </el-select>
      <el-button type="primary" size="small" @click="search">查询</el-button>
      <el-button size="small" @click="reset">显示全部高风险</el-button>
    </div>

    <!-- 表格 -->
    <div class="panel" style="margin-top:10px;">
      <el-table :data="list" stripe size="mini" @row-click="showDetail">
        <el-table-column type="index" label="#" width="45" />
        <el-table-column prop="userId" label="用户ID" width="140" />
        <el-table-column prop="riskScore" label="风险评分" width="80" sortable>
          <template slot-scope="{ row }">
            <span :class="['risk-text', scoreClass(row.riskScore)]">{{ row.riskScore }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="accountStatus" label="账户状态" width="80">
          <template slot-scope="{ row }">
            <el-tag :type="statusType(row.accountStatus)" size="mini">{{ statusLabel(row.accountStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="riskTags" label="风险标签" min-width="140" show-overflow-tooltip>
          <template slot-scope="{ row }">
            <el-tag v-if="row.riskTags" size="mini" type="danger" effect="dark">{{ row.riskTags }}</el-tag>
            <span v-else class="text-muted">--</span>
          </template>
        </el-table-column>
        <el-table-column prop="avgAmt30d" label="30日均额" width="100" sortable>
          <template slot-scope="{ row }"><span class="mono-num">¥{{ row.avgAmt30d?.toFixed(0) }}</span></template>
        </el-table-column>
        <el-table-column prop="transCount24h" label="24H交易" width="80" />
        <el-table-column prop="transCount7d" label="7天交易" width="80" />
        <el-table-column prop="commonCities" label="常用城市" min-width="100" show-overflow-tooltip>
          <template slot-scope="{ row }"><span class="text-muted">{{ row.commonCities || '--' }}</span></template>
        </el-table-column>
        <el-table-column prop="lastCity" label="上次交易城市" width="100" />
        <el-table-column prop="cancelRetryCount" label="取消重试" width="80" />
      </el-table>

      <el-pagination style="margin-top:10px;text-align:right;" background layout="total, prev, pager, next"
        :total="total" :page-size="pageSize" :current-page.sync="page" @current-change="search" />
    </div>

    <!-- 详情弹窗 -->
    <el-dialog :title="'高风险用户 — ' + detail.userId" :visible.sync="dialogVisible" width="560px" append-to-body>
      <el-tabs v-if="detail.userId" size="small">
        <el-tab-pane label="行为基线">
          <el-form label-width="120px" size="small">
            <el-form-item label="近30天平均金额">¥{{ detail.avgAmt30d?.toFixed(2) }}</el-form-item>
            <el-form-item label="常用城市">{{ detail.commonCities || '--' }}</el-form-item>
            <el-form-item label="常用设备">{{ detail.commonDevs || '--' }}</el-form-item>
            <el-form-item label="常用支付渠道">{{ detail.commonPayChannels || '--' }}</el-form-item>
            <el-form-item label="常用收款方">{{ detail.commonCounterparties || '--' }}</el-form-item>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="风险指标">
          <el-form label-width="120px" size="small">
            <el-form-item label="风险评分">
              <span :class="['risk-text', scoreClass(detail.riskScore)]">{{ detail.riskScore }}</span>
            </el-form-item>
            <el-form-item label="风险标签">
              <el-tag v-if="detail.riskTags" size="mini" type="danger">{{ detail.riskTags }}</el-tag>
              <span v-else class="text-muted">无</span>
            </el-form-item>
            <el-form-item label="账户状态">
              <el-tag :type="statusType(detail.accountStatus)" size="mini">{{ statusLabel(detail.accountStatus) }}</el-tag>
            </el-form-item>
            <el-form-item label="24H转账笔数">{{ detail.transCount24h }}</el-form-item>
            <el-form-item label="7天转账笔数">{{ detail.transCount7d }}</el-form-item>
            <el-form-item label="取消重试次数">{{ detail.cancelRetryCount }}</el-form-item>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="最近活动">
          <el-form label-width="120px" size="small">
            <el-form-item label="上次交易时间">{{ fmtTime(detail.lastTransTs) }}</el-form-item>
            <el-form-item label="上次交易城市">{{ detail.lastCity || '--' }}</el-form-item>
            <el-form-item label="上次登录IP">{{ detail.lastIp || '--' }}</el-form-item>
            <el-form-item label="上次登录时间">{{ fmtTime(detail.lastLoginTime) }}</el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>
  </div>
</template>

<script>
import { getProfileList, getProfileDetail, getProfileStats } from '@/api/profile'

const STATUS_MAP = {
  'normal': { label: '正常', type: 'success' },
  'frozen': { label: '冻结', type: 'danger' },
  'flagged': { label: '已标记', type: 'warning' },
  'dormant': { label: '休眠', type: 'info' }
}

export default {
  name: 'HighRiskUsers',
  data() {
    return {
      filters: { accountStatus: '' },
      list: [], stats: { over80: 0, over40: 0, flagged: 0, frozen: 0 },
      page: 1, pageSize: 20, total: 0,
      dialogVisible: false, detail: {}
    }
  },
  async mounted() {
    await Promise.all([this.search(), this.loadStats()])
  },
  methods: {
    async loadStats() {
      try {
        const res = await getProfileStats()
        if (res.code === 200) {
          this.stats.frozen = res.data.frozen || 0
          this.stats.flagged = res.data.flagged || 0
        }
        // 高风险分段
        const r80 = await getProfileList({ riskLevel: 'high', pageSize: 1 })
        if (r80.code === 200) { this.stats.over80 = r80.data.total || 0 }
        const r40 = await getProfileList({ riskLevel: 'medium', pageSize: 1 })
        if (r40.code === 200) { this.stats.over40 = r40.data.total || 0 }
      } catch (e) { /* ignore */ }
    },
    async search() {
      try {
        const params = { page: this.page, pageSize: this.pageSize, riskLevel: 'high' }
        if (this.filters.accountStatus) params.accountStatus = this.filters.accountStatus
        const res = await getProfileList(params)
        if (res.code === 200) { this.list = res.data.list; this.total = res.data.total }
      } catch (e) { /* ignore */ }
    },
    reset() { this.filters.accountStatus = ''; this.page = 1; this.search() },
    async showDetail(row) {
      try {
        const res = await getProfileDetail(row.userId)
        if (res.code === 200) { this.detail = res.data; this.dialogVisible = true }
      } catch (e) { /* ignore */ }
    },
    scoreClass(s) { if (s >= 80) return 'critical'; if (s >= 40) return 'warning'; return 'success' },
    statusType(s) { return (STATUS_MAP[s] || {}).type || 'info' },
    statusLabel(s) { return (STATUS_MAP[s] || {}).label || s },
    fmtTime(ts) { return ts ? new Date(ts).toLocaleString('zh-CN') : '--' }
  }
}
</script>

<style scoped>
.high-risk-page { height: 100%; overflow-y: auto; }
.page-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; margin-bottom: var(--space-3); padding-bottom: var(--space-3); border-bottom: 1px solid var(--color-border); }
.page-title { color: var(--color-text-primary); font-size: var(--text-lg); font-weight: 600; margin: 0; }
.header-left { display: flex; align-items: center; gap: var(--space-3); }
.header-right { display: flex; align-items: center; gap: var(--space-2); flex-wrap: wrap; }
.header-sub { color: var(--color-text-muted); font-size: var(--text-sm); }
.header-sub b { color: var(--color-text-secondary); }
.header-link { font-size: 11px; color: var(--color-text-muted); text-decoration: none; padding: 3px 8px; border-radius: var(--radius-sm); transition: all 0.2s; }
.header-link:hover { color: var(--color-primary); background: var(--color-bg-hover); }
.stats-row { display: flex; gap: var(--space-2); margin-bottom: var(--space-3); }
.stat-item { flex: 1; text-align: center; padding: var(--space-3); background: var(--color-bg-elevated); border: 1px solid var(--color-border); border-radius: var(--radius-sm); }
.stat-num { font-family: var(--font-mono); font-size: 22px; font-weight: 600; color: var(--color-text-primary); }
.stat-num.critical { color: var(--color-critical); }
.stat-num.danger { color: var(--color-danger); }
.stat-num.warning { color: var(--color-warning); }
.stat-num.muted { color: var(--color-text-secondary); }
.stat-label { font-size: var(--text-xs); color: var(--color-text-muted); margin-top: 2px; }
.filter-bar { display: flex; align-items: center; gap: var(--space-2); padding: var(--space-2) var(--space-3); background: var(--color-bg-elevated); border: 1px solid var(--color-border); border-radius: var(--radius-sm); }
.filter-select { width: 120px; }
.panel { background: var(--color-bg-elevated); border: 1px solid var(--color-border); border-radius: var(--radius-sm); padding: var(--space-3); }
.risk-text { font-weight: 600; font-size: var(--text-sm); }
.risk-text.critical { color: var(--color-critical); }
.risk-text.warning { color: var(--color-warning); }
.risk-text.success { color: var(--color-success); }
.text-muted { color: var(--color-text-muted); font-size: var(--text-sm); }
.mono-num { font-family: var(--font-mono); font-size: var(--text-sm); }
</style>
