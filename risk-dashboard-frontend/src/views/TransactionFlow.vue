<template>
  <div class="transaction-flow">
    <div class="page-header">
      <h2 class="page-title">交易流水实时监控</h2>
      <div class="header-actions">
        <span :class="['status-dot', wsConnected ? 'online' : 'offline']"></span>
        <span class="status-text">{{ wsConnected ? '实时推送中' : '推送断开' }}</span>
        <el-button size="mini" icon="el-icon-refresh" @click="refresh">刷新</el-button>
      </div>
    </div>

    <el-row :gutter="10">
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-label">当前交易量</div>
          <div class="stat-value">{{ totalCount }}</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-label">正常交易</div>
          <div class="stat-value green">{{ passCount }}</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-label">风险交易</div>
          <div class="stat-value orange">{{ riskCount }}</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-label">平均金额</div>
          <div class="stat-value">{{ avgAmount }}</div>
        </div>
      </el-col>
    </el-row>

    <div class="panel" style="margin-top:10px;">
      <div class="panel-header">
        <span class="panel-title">实时交易流水 (最新50条)</span>
      </div>
      <el-table :data="transactions" stripe size="mini" max-height="520" v-loading="loading">
        <el-table-column prop="transId" label="交易流水号" width="200" />
        <el-table-column prop="userId" label="用户" width="100" />
        <el-table-column prop="amount" label="金额" width="110">
          <template slot-scope="{ row }">
            <span :class="{ 'amount-high': row.amount > 10000 }" class="mono-num">
              ¥{{ row.amount?.toFixed(2) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="city" label="城市" width="80" />
        <el-table-column prop="geoLocation" label="经纬度" width="110" />
        <el-table-column prop="deviceId" label="设备指纹" width="130" />
        <el-table-column prop="networkType" label="网络" width="70">
          <template slot-scope="{ row }">
            <el-tag v-if="row.networkType === 'VPN'" type="danger" size="mini" effect="dark">VPN</el-tag>
            <span v-else class="text-muted">{{ row.networkType }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="devScore" label="设备分" width="80">
          <template slot-scope="{ row }">
            <el-tag :type="devScoreType(row.devScore)" size="mini" effect="dark">
              {{ row.devScore }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="155">
          <template slot-scope="{ row }">
            <span class="text-muted">{{ formatTime(row.transTimestamp) }}</span>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script>
import { getRecentTransactions } from '@/api/transaction'
import { REFRESH_INTERVAL } from '@/utils/constants'

export default {
  name: 'TransactionFlow',
  data() {
    return {
      transactions: [],
      loading: false,
      wsConnected: false,
      timer: null,
      totalCount: 0, passCount: 0, riskCount: 0, avgAmount: '¥0'
    }
  },
  mounted() {
    this.refresh()
    this.timer = setInterval(() => this.refresh(), REFRESH_INTERVAL)
  },
  beforeDestroy() { clearInterval(this.timer) },
  methods: {
    async refresh() {
      this.loading = true
      try {
        const res = await getRecentTransactions(50)
        if (res.code === 200) {
          this.transactions = res.data || []
          this.updateStats()
        }
      } finally { this.loading = false }
    },
    updateStats() {
      const list = this.transactions
      this.totalCount = list.length
      this.passCount = list.filter(t => (t.devScore || 0) >= 80 && t.networkType !== 'VPN').length
      this.riskCount = list.filter(t => (t.devScore || 0) < 50 || t.networkType === 'VPN').length
      const avg = list.length ? list.reduce((s, t) => s + (t.amount || 0), 0) / list.length : 0
      this.avgAmount = '¥' + avg.toFixed(0)
    },
    formatTime(ts) {
      if (!ts) return ''
      return new Date(ts).toLocaleString('zh-CN')
    },
    devScoreType(s) {
      return s >= 80 ? 'success' : s >= 50 ? 'warning' : 'danger'
    }
  }
}
</script>

<style scoped>
.transaction-flow { min-height: 100%; }

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

.header-actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.status-dot {
  width: 6px; height: 6px; border-radius: 50%;
  flex-shrink: 0;
}

.status-dot.online { background: var(--color-success); }
.status-dot.offline { background: var(--color-danger); }

.status-text {
  color: var(--color-text-muted);
  font-size: var(--text-xs);
}

/* Stat Cards */
.stat-card {
  background: var(--color-bg-elevated);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: var(--space-3) var(--space-4);
}

.stat-label {
  color: var(--color-text-muted);
  font-size: var(--text-xs);
}

.stat-value {
  font-family: var(--font-mono);
  font-size: 24px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-top: 2px;
}

.stat-value.green { color: var(--color-success); }
.stat-value.orange { color: var(--color-warning); }

/* Panel */
.panel {
  background: var(--color-bg-elevated);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: var(--space-3);
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

.mono-num { font-family: var(--font-mono); font-size: var(--text-sm); }
.amount-high { color: var(--color-danger); font-weight: 600; }
.text-muted { color: var(--color-text-muted); font-size: var(--text-sm); }
</style>
