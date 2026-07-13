<template>
  <div class="cpty-risk-page">
    <div class="page-header">
      <div class="header-left">
        <h2 class="page-title">收款方风险分析</h2>
        <span class="header-sub">共 <b>{{ total }}</b> 收款方</span>
      </div>
      <div class="header-right">
        <router-link to="/blacklist" class="header-link">收款方黑名单</router-link>
        <el-button size="mini" icon="el-icon-back" @click="$router.push('/offline-overview')">返回离线总览</el-button>
      </div>
    </div>

    <!-- 统计 -->
    <div class="stats-row">
      <div class="stat-item">
        <div class="stat-num muted">{{ stats.totalCounterparties | num }}</div>
        <div class="stat-label">收款方总数</div>
      </div>
      <div class="stat-item">
        <div class="stat-num danger">{{ stats.blacklistCount | num }}</div>
        <div class="stat-label">黑名单</div>
      </div>
      <div class="stat-item">
        <div class="stat-num warning">{{ stats.manyToOneCount | num }}</div>
        <div class="stat-label">多对一集中收款</div>
      </div>
    </div>

    <div class="panel" style="margin-top:10px;">
      <el-table :data="list" stripe size="mini">
        <el-table-column type="index" label="#" width="45" />
        <el-table-column prop="counterpartyId" label="收款方ID" min-width="150" show-overflow-tooltip />
        <el-table-column prop="transCount" label="交易次数" width="90" sortable />
        <el-table-column prop="totalAmount" label="收款总额" width="120" sortable>
          <template slot-scope="{ row }"><span class="mono-num">¥{{ fmtK(row.totalAmount) }}</span></template>
        </el-table-column>
        <el-table-column prop="payerCount" label="付款方数" width="90" sortable>
          <template slot-scope="{ row }">
            <el-tag v-if="row.payerCount >= 20" type="danger" size="mini" effect="dark">{{ row.payerCount }}</el-tag>
            <el-tag v-else-if="row.payerCount >= 5" type="warning" size="mini">{{ row.payerCount }}</el-tag>
            <span v-else>{{ row.payerCount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="isManyToOne" label="多对一" width="70">
          <template slot-scope="{ row }">
            <el-tag v-if="row.isManyToOne" type="danger" size="mini">是</el-tag>
            <span v-else class="text-muted">否</span>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination style="margin-top:10px;text-align:right;" background layout="total, prev, pager, next"
        :total="total" :page-size="pageSize" :current-page.sync="page" @current-change="fetchData" />
    </div>
  </div>
</template>

<script>
import { getCptyRiskStats, getCptyRiskList } from '@/api/counterparty-risk'

export default {
  name: 'CounterpartyRisk',
  data() {
    return { list: [], stats: {}, page: 1, pageSize: 20, total: 0 }
  },
  filters: { num(v) { return v ? Number(v).toLocaleString() : '0' } },
  async mounted() {
    await Promise.all([this.fetchData(), this.loadStats()])
  },
  methods: {
    async loadStats() {
      try { const r = await getCptyRiskStats(); if (r.code === 200) this.stats = r.data } catch (e) {}
    },
    async fetchData() {
      try {
        const r = await getCptyRiskList(this.page, this.pageSize)
        if (r.code === 200) { this.list = r.data.list; this.total = r.data.total }
      } catch (e) {}
    },
    fmtK(v) { return v ? (v / 10000).toFixed(1) + '万' : '0' }
  }
}
</script>

<style scoped>
.cpty-risk-page { height: 100%; overflow-y: auto; }
.page-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; margin-bottom: var(--space-3); padding-bottom: var(--space-3); border-bottom: 1px solid var(--color-border); }
.page-title { color: var(--color-text-primary); font-size: var(--text-lg); font-weight: 600; margin: 0; }
.header-left { display: flex; align-items: center; gap: var(--space-3); }
.header-right { display: flex; align-items: center; gap: var(--space-2); }
.header-sub { color: var(--color-text-muted); font-size: var(--text-sm); }
.header-sub b { color: var(--color-text-secondary); }
.header-link { font-size: 11px; color: var(--color-text-muted); text-decoration: none; padding: 3px 8px; border-radius: var(--radius-sm); transition: all 0.2s; }
.header-link:hover { color: var(--color-primary); background: var(--color-bg-hover); }
.stats-row { display: flex; gap: var(--space-2); margin-bottom: var(--space-3); }
.stat-item { flex: 1; text-align: center; padding: var(--space-3); background: var(--color-bg-elevated); border: 1px solid var(--color-border); border-radius: var(--radius-sm); }
.stat-num { font-family: var(--font-mono); font-size: 22px; font-weight: 600; color: var(--color-text-primary); }
.stat-num.danger { color: var(--color-danger); }
.stat-num.warning { color: var(--color-warning); }
.stat-num.muted { color: var(--color-text-secondary); }
.stat-label { font-size: var(--text-xs); color: var(--color-text-muted); margin-top: 2px; }
.panel { background: var(--color-bg-elevated); border: 1px solid var(--color-border); border-radius: var(--radius-sm); padding: var(--space-3); }
.text-muted { color: var(--color-text-muted); font-size: var(--text-sm); }
.mono-num { font-family: var(--font-mono); font-size: var(--text-sm); }
</style>
