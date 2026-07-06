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
      <el-col :span="4" v-for="card in statCards" :key="card.key">
        <div class="stat-card">
          <div class="stat-label">{{ card.label }}</div>
          <div class="stat-value" :class="card.variant">{{ card.value }}</div>
        </div>
      </el-col>
    </el-row>

    <div class="panel" style="margin-top:10px;">
      <div class="panel-header">
        <span class="panel-title">实时交易流水 (最新50条)</span>
        <span class="panel-sub">交易类型: <b>同行/跨行/对公</b> | 支付渠道: <b>银行卡/余额/微信/支付宝</b></span>
      </div>
      <el-table :data="transactions" stripe size="mini" max-height="520" style="width:100%">
        <el-table-column prop="transId" label="交易流水号" width="200" fixed />
        <el-table-column prop="userId" label="用户" width="90" />
        <el-table-column prop="amount" label="金额" width="100" sortable>
          <template slot-scope="{ row }">
            <span :class="{ 'amount-high': row.amount > 10000 }" class="mono-num">
              ¥{{ row.amount?.toFixed(2) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="transType" label="交易类型" width="80" />
        <el-table-column prop="payChannel" label="支付渠道" width="80">
          <template slot-scope="{ row }">
            <el-tag v-if="row.payChannel" size="mini" type="info">{{ payChannelLabel(row.payChannel) }}</el-tag>
            <span v-else class="text-muted">--</span>
          </template>
        </el-table-column>
        <el-table-column prop="city" label="城市" width="70" />
        <el-table-column prop="counterpartyId" label="收款方" width="100" show-overflow-tooltip />
        <el-table-column prop="deviceId" label="设备指纹" width="120" show-overflow-tooltip />
        <el-table-column prop="osType" label="系统" width="80">
          <template slot-scope="{ row }">
            <span class="text-muted">{{ row.osType }} {{ row.osVersion }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="networkType" label="网络" width="60">
          <template slot-scope="{ row }">
            <el-tag v-if="row.networkType === 'VPN'" type="danger" size="mini" effect="dark">VPN</el-tag>
            <span v-else class="text-muted">{{ row.networkType }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="ipAddress" label="IP" width="110" />
        <el-table-column prop="devScore" label="设备分" width="70" sortable>
          <template slot-scope="{ row }">
            <el-tag :type="devScoreType(row.devScore)" size="mini" effect="dark">{{ row.devScore }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="loginFailCount" label="登录失败" width="70">
          <template slot-scope="{ row }">
            <el-tag v-if="row.loginFailCount > 0" type="warning" size="mini" effect="dark">{{ row.loginFailCount }}次</el-tag>
            <span v-else class="text-muted">0</span>
          </template>
        </el-table-column>
        <el-table-column prop="inputMethod" label="输入方式" width="70">
          <template slot-scope="{ row }">
            <el-tag v-if="row.inputMethod === 'paste'" type="warning" size="mini">粘贴</el-tag>
            <span v-else class="text-muted">{{ row.inputMethod }}</span>
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

const PAY_CHANNEL_MAP = {
  'bank_card': '银行卡', 'balance': '余额', 'wechat': '微信', 'alipay': '支付宝'
}

export default {
  name: 'TransactionFlow',
  data() {
    return {
      transactions: [],
      loading: false,
      wsConnected: false,
      timer: null
    }
  },
  computed: {
    statCards() {
      const list = this.transactions
      const total = list.length
      const passCount = list.filter(t => (t.devScore || 0) >= 80 && t.networkType !== 'VPN').length
      const riskCount = list.filter(t => (t.devScore || 0) < 50 || t.networkType === 'VPN' || t.rootJailbreak === 1).length
      const vpnCount = list.filter(t => t.networkType === 'VPN').length
      const avg = list.length ? list.reduce((s, t) => s + (t.amount || 0), 0) / list.length : 0
      const avgAmount = '¥' + avg.toFixed(0)
      return [
        { key: 'total', label: '当前交易量', value: total, variant: '' },
        { key: 'pass', label: '安全交易', value: passCount, variant: 'green' },
        { key: 'risk', label: '可疑交易', value: riskCount, variant: 'orange' },
        { key: 'vpn', label: 'VPN交易', value: vpnCount, variant: 'red' },
        { key: 'amt', label: '平均金额', value: avgAmount, variant: '' },
        { key: 'jailbreak', label: '越狱设备', value: list.filter(t => t.rootJailbreak === 1).length, variant: 'orange' }
      ]
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
        }
      } finally { this.loading = false }
    },
    formatTime(ts) {
      if (!ts) return ''
      return new Date(ts).toLocaleString('zh-CN')
    },
    devScoreType(s) {
      return s >= 80 ? 'success' : s >= 50 ? 'warning' : 'danger'
    },
    payChannelLabel(ch) {
      return PAY_CHANNEL_MAP[ch] || ch
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

.stat-card {
  background: var(--color-bg-elevated);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: var(--space-2) var(--space-3);
}

.stat-label {
  color: var(--color-text-muted);
  font-size: var(--text-xs);
}

.stat-value {
  font-family: var(--font-mono);
  font-size: 20px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-top: 2px;
}

.stat-value.green { color: var(--color-success); }
.stat-value.orange { color: var(--color-warning); }
.stat-value.red { color: var(--color-danger); }

.panel {
  background: var(--color-bg-elevated);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: var(--space-3);
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

.panel-sub {
  color: var(--color-text-muted);
  font-size: var(--text-xs);
}

.panel-sub b { color: var(--color-text-secondary); }

.mono-num { font-family: var(--font-mono); font-size: var(--text-sm); }
.amount-high { color: var(--color-danger); font-weight: 600; }
.text-muted { color: var(--color-text-muted); font-size: var(--text-sm); }
</style>
