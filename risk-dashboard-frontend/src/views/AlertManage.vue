<template>
  <div class="alert-manage">
    <div class="page-header">
      <h2 class="page-title">风险告警管理</h2>
      <span class="header-sub">共 <b>{{ total }}</b> 条</span>
    </div>

    <div class="stats-row">
      <div class="stat-item" :class="{ active: filters.riskLevel === '高危' }" @click="quickFilter('高危')">
        <div class="stat-num danger">{{ stats.high }}</div>
        <div class="stat-label">高危</div>
      </div>
      <div class="stat-item" :class="{ active: filters.riskLevel === '中危' }" @click="quickFilter('中危')">
        <div class="stat-num warning">{{ stats.mid }}</div>
        <div class="stat-label">中危</div>
      </div>
      <div class="stat-item" :class="{ active: filters.riskLevel === '低危' }" @click="quickFilter('低危')">
        <div class="stat-num success">{{ stats.low }}</div>
        <div class="stat-label">低危</div>
      </div>
    </div>

    <div class="filter-bar">
      <el-select v-model="filters.riskLevel" clearable placeholder="风险等级" size="small" class="filter-select">
        <el-option label="高危" value="高危" />
        <el-option label="中危" value="中危" />
        <el-option label="低危" value="低危" />
      </el-select>
      <el-button type="primary" size="small" @click="search">查询</el-button>
      <el-button size="small" @click="reset">重置</el-button>
    </div>

    <div class="panel" style="margin-top:10px;">
      <el-table :data="alertList" stripe size="mini" v-loading="loading">
        <el-table-column prop="alertId" label="告警编号" width="200" />
        <el-table-column prop="transId" label="交易流水" width="200" />
        <el-table-column prop="userId" label="用户" width="100" />
        <el-table-column prop="hitRules" label="触发规则" min-width="140" show-overflow-tooltip />
        <el-table-column prop="amount" label="金额" width="110">
          <template slot-scope="{ row }">
            <span class="mono-num">¥{{ row.amount?.toFixed(2) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="finalScore" label="评分" width="70">
          <template slot-scope="{ row }">
            <el-tag :type="scoreType(row.finalScore)" size="mini" effect="dark">{{ row.finalScore }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="riskLevel" label="等级" width="60">
          <template slot-scope="{ row }">
            <span :class="['risk-text', riskClass(row.riskLevel)]">{{ row.riskLevel }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="alertLoc" label="城市" width="70" />
        <el-table-column prop="action" label="动作" width="60">
          <template slot-scope="{ row }">{{ actionLabel(row.action) }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="时间" width="155" />
      </el-table>

      <el-pagination
        style="margin-top:10px;text-align:right;"
        background layout="total, prev, pager, next"
        :total="total" :page-size="pageSize"
        :current-page.sync="page"
        @current-change="search" />
    </div>
  </div>
</template>

<script>
import { getAlertList, getRiskLevelStat } from '@/api/alert'
import { ACTION_MAP } from '@/utils/constants'

export default {
  name: 'AlertManage',
  data() {
    return {
      filters: { riskLevel: '', action: '' },
      alertList: [],
      stats: { high: 0, mid: 0, low: 0 },
      loading: false,
      page: 1,
      pageSize: 20,
      total: 0
    }
  },
  mounted() { this.init() },
  methods: {
    async init() {
      await Promise.all([this.search(), this.loadStats()])
    },
    async loadStats() {
      try {
        const levelRes = await getRiskLevelStat()
        if (levelRes.code === 200) {
          const d = levelRes.data || []
          this.stats.high = (d.find(x => x.name === '高危') || {}).value || 0
          this.stats.mid = (d.find(x => x.name === '中危') || {}).value || 0
          this.stats.low = (d.find(x => x.name === '低危') || {}).value || 0
        }
      } catch (e) { /* ignore */ }
    },
    quickFilter(level) {
      this.filters.riskLevel = this.filters.riskLevel === level ? '' : level
      this.page = 1
      this.search()
    },
    async search() {
      this.loading = true
      try {
        const res = await getAlertList({
          riskLevel: this.filters.riskLevel || undefined,
          page: this.page, pageSize: this.pageSize
        })
        if (res.code === 200) {
          this.alertList = res.data.list
          this.total = res.data.total
        }
      } finally { this.loading = false }
    },
    reset() {
      this.filters = { riskLevel: '' }
      this.page = 1
      this.search()
    },
    refresh() { this.init() },
    scoreType(s) { return s >= 80 ? 'danger' : s >= 60 ? 'warning' : 'success' },
    riskClass(l) { return l === '高危' ? 'danger' : l === '中危' ? 'warning' : 'success' },
    actionLabel(a) { return ACTION_MAP[a]?.label || a }
  }
}
</script>

<style scoped>
.alert-manage { min-height: 100%; }

.page-header {
  display: flex;
  align-items: center;
  gap: var(--space-3);
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

.header-sub {
  color: var(--color-text-muted);
  font-size: var(--text-sm);
}

.header-sub b { color: var(--color-text-secondary); }

/* Stats */
.stats-row {
  display: flex;
  gap: var(--space-2);
  margin-bottom: var(--space-3);
}

.stat-item {
  flex: 1;
  text-align: center;
  padding: var(--space-3);
  background: var(--color-bg-elevated);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  cursor: pointer;
}

.stat-item.active {
  border-color: var(--color-primary);
}

.stat-num {
  font-family: var(--font-mono);
  font-size: 22px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.stat-num.danger  { color: var(--color-danger); }
.stat-num.warning { color: var(--color-warning); }
.stat-num.success { color: var(--color-success); }
.stat-num.muted   { color: var(--color-text-secondary); }

.stat-label {
  font-size: var(--text-xs);
  color: var(--color-text-muted);
  margin-top: 2px;
}

/* Filter */
.filter-bar {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  background: var(--color-bg-elevated);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
}

.filter-select { width: 120px; }

/* Panel */
.panel {
  background: var(--color-bg-elevated);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: var(--space-3);
}

.risk-text { font-weight: 600; font-size: var(--text-sm); }
.risk-text.danger  { color: var(--color-danger); }
.risk-text.warning { color: var(--color-warning); }
.risk-text.success { color: var(--color-success); }

.text-success { color: var(--color-success); }
.mono-num { font-family: var(--font-mono); font-size: var(--text-sm); }
</style>
