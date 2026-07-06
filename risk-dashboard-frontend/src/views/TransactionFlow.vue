<template>
  <div class="full-page">
    <div class="page-header">
      <h2 class="page-title">交易流水实时监控</h2>
      <div class="header-actions">
        <span :class="['status-dot', wsConnected ? 'online' : 'offline']"></span>
        <span class="status-text">{{ wsConnected ? '实时推送中' : '推送断开' }}</span>
        <el-button size="mini" icon="el-icon-refresh" @click="refresh">刷新</el-button>
      </div>
    </div>

    <div class="stat-row">
      <div class="stat-card" v-for="card in statCards" :key="card.key">
        <span class="stat-label">{{ card.label }}</span>
        <span class="stat-value" :class="card.variant">{{ card.value }}</span>
      </div>
    </div>

    <div class="panel flex-1">
      <div class="panel-header">
        <span class="panel-title">实时交易流水 (最新</span>
        <el-input-number v-model="limit" :min="10" :max="500" :step="10" size="mini" @change="refresh" style="width:90px" />
        <span class="panel-title">条)</span>
      </div>
      <el-table :data="transactions" stripe size="mini" max-height="100%" class="flex-table">
        <el-table-column prop="transId" label="交易流水号" width="200" fixed />
        <el-table-column prop="userId" label="用户" width="90" />
        <el-table-column prop="amount" label="金额" width="100" sortable>
          <template slot-scope="{ row }">
            <span :class="{ 'amount-high': row.amount > 10000 }" class="mono-num">¥{{ row.amount?.toFixed(2) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="transType" label="交易类型" width="80" />
        <el-table-column prop="payChannel" label="支付渠道" width="80" />
        <el-table-column prop="city" label="城市" width="70" />
        <el-table-column prop="counterpartyId" label="收款方" width="100" show-overflow-tooltip />
        <el-table-column prop="networkType" label="网络" width="60">
          <template slot-scope="{ row }">
            <el-tag v-if="row.networkType === 'VPN'" type="danger" size="mini" effect="dark">VPN</el-tag>
            <span v-else class="text-muted">{{ row.networkType }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="devScore" label="设备分" width="70" sortable>
          <template slot-scope="{ row }">
            <el-tag :type="sType(row.devScore)" size="mini" effect="dark">{{ row.devScore }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="rootJailbreak" label="越狱" width="50">
          <template slot-scope="{ row }">
            <el-tag v-if="row.rootJailbreak === 1" type="danger" size="mini">是</el-tag>
            <span v-else class="text-muted">否</span>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="150">
          <template slot-scope="{ row }">
            <span class="text-muted">{{ fmt(row.transTimestamp) }}</span>
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
  data() { return { transactions: [], limit: 50, loading: false, wsConnected: false, timer: null } },
  computed: {
    statCards() {
      const list = this.transactions
      const risk = list.filter(t => (t.devScore || 0) < 50 || t.networkType === 'VPN' || t.rootJailbreak === 1).length
      const avg = list.length ? list.reduce((s, t) => s + (t.amount || 0), 0) / list.length : 0
      return [
        { key: 'total', label: '当前交易量', value: list.length, variant: '' },
        { key: 'pass', label: '安全交易', value: list.filter(t => (t.devScore || 0) >= 80 && t.networkType !== 'VPN').length, variant: 'green' },
        { key: 'risk', label: '可疑交易', value: risk, variant: 'orange' },
        { key: 'vpn', label: 'VPN交易', value: list.filter(t => t.networkType === 'VPN').length, variant: 'red' },
        { key: 'amt', label: '平均金额', value: '¥' + avg.toFixed(0), variant: '' },
        { key: 'jb', label: '越狱设备', value: list.filter(t => t.rootJailbreak === 1).length, variant: 'orange' }
      ]
    }
  },
  mounted() { this.refresh(); this.timer = setInterval(() => this.refresh(), REFRESH_INTERVAL) },
  beforeDestroy() { clearInterval(this.timer) },
  methods: {
    async refresh() {
      try {
        const res = await getRecentTransactions(this.limit)
        if (res.code === 200) this.transactions = res.data || []
      } finally {}
    },
    fmt(ts) { return ts ? new Date(ts).toLocaleString('zh-CN') : '' },
    sType(s) { return s >= 80 ? 'success' : s >= 50 ? 'warning' : 'danger' }
  }
}
</script>

<style scoped>
.full-page { height: 100%; display: flex; flex-direction: column; overflow: hidden; }

.page-header {
  display: flex; justify-content: space-between; align-items: center;
  padding-bottom: 6px; border-bottom: 1px solid var(--color-border); flex-shrink: 0;
}

.page-title { color: var(--color-text-primary); font-size: 15px; font-weight: 600; margin: 0; }
.header-actions { display: flex; align-items: center; gap: 8px; }
.status-dot { width: 6px; height: 6px; border-radius: 50%; flex-shrink: 0; }
.status-dot.online { background: var(--color-success); }
.status-dot.offline { background: var(--color-danger); }
.status-text { color: var(--color-text-muted); font-size: 11px; }

.stat-row { display: flex; gap: 8px; padding: 4px 0; flex-shrink: 0; }
.stat-card {
  flex: 1; display: flex; align-items: center; justify-content: space-between;
  background: var(--color-bg-elevated); border: 1px solid var(--color-border);
  border-radius: var(--radius-sm); padding: 4px 10px;
}
.stat-label { color: var(--color-text-muted); font-size: 10px; }
.stat-value { font-family: var(--font-mono); font-size: 16px; font-weight: 600; color: var(--color-text-primary); }
.stat-value.green { color: var(--color-success); }
.stat-value.orange { color: var(--color-warning); }
.stat-value.red { color: var(--color-danger); }

.flex-1 { flex: 1; min-height: 0; height: 0; }
.panel {
  background: var(--color-bg-elevated); border: 1px solid var(--color-border);
  border-radius: var(--radius-sm); padding: 6px 8px; display: flex; flex-direction: column;
  overflow: hidden;
}
.panel-header { padding-bottom: 4px; border-bottom: 1px solid var(--color-border); flex-shrink: 0; display: flex; align-items: center; gap: 6px; }
.panel-title { color: var(--color-text-secondary); font-size: 11px; font-weight: 500; }
.flex-table { flex: 1; min-height: 0; }

.mono-num { font-family: var(--font-mono); font-size: 11px; }
.amount-high { color: var(--color-danger); font-weight: 600; }
.text-muted { color: var(--color-text-muted); font-size: 11px; }
</style>
