<template>
  <div class="device-risk-page">
    <div class="page-header">
      <div class="header-left">
        <h2 class="page-title">设备风险分析</h2>
        <span class="header-sub">共 <b>{{ total }}</b> 设备</span>
      </div>
      <div class="header-right">
        <el-button size="mini" icon="el-icon-back" @click="$router.push('/offline-overview')">返回离线总览</el-button>
      </div>
    </div>

    <!-- 统计 -->
    <div class="stats-row">
      <div class="stat-item">
        <div class="stat-num muted">{{ stats.totalDevices | num }}</div>
        <div class="stat-label">总设备数</div>
      </div>
      <div class="stat-item">
        <div class="stat-num danger">{{ sharedCount }}</div>
        <div class="stat-label">共享设备（≥3用户）</div>
      </div>
    </div>

    <div class="panel" style="margin-top:10px;">
      <el-table :data="list" stripe size="mini" :row-class-name="rowClass">
        <el-table-column type="index" label="#" width="45" />
        <el-table-column prop="deviceId" label="设备ID" min-width="150" show-overflow-tooltip />
        <el-table-column prop="userCount" label="关联用户" width="80" sortable>
          <template slot-scope="{ row }">
            <el-tag v-if="row.userCount >= 3" type="danger" size="mini" effect="dark">{{ row.userCount }}</el-tag>
            <span v-else>{{ row.userCount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="transCount" label="交易次数" width="90" sortable />
        <el-table-column prop="avgScore" label="平均安全分" width="90" sortable>
          <template slot-scope="{ row }">
            <span :class="{ 'text-danger': row.avgScore < 50, 'text-warning': row.avgScore >= 50 && row.avgScore < 80 }">{{ row.avgScore }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="jailbreakCount" label="越狱次数" width="80">
          <template slot-scope="{ row }">
            <el-tag v-if="row.jailbreakCount > 0" type="danger" size="mini" effect="dark">{{ row.jailbreakCount }}</el-tag>
            <span v-else class="text-muted">0</span>
          </template>
        </el-table-column>
        <el-table-column prop="cityCount" label="涉足城市" width="80" />
        <el-table-column prop="isShared" label="共享" width="60">
          <template slot-scope="{ row }">
            <el-tag v-if="row.isShared" type="danger" size="mini">是</el-tag>
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
import { getDeviceStats, getDeviceList } from '@/api/device-risk'

export default {
  name: 'DeviceRisk',
  data() {
    return { list: [], stats: { totalDevices: 0 }, page: 1, pageSize: 20, total: 0 }
  },
  computed: {
    sharedCount() { return this.list.filter(d => d.isShared).length }
  },
  filters: { num(v) { return v ? Number(v).toLocaleString() : '0' } },
  async mounted() {
    await Promise.all([this.fetchData(), this.loadStats()])
  },
  methods: {
    async loadStats() {
      try { const r = await getDeviceStats(); if (r.code === 200) this.stats = r.data } catch (e) {}
    },
    async fetchData() {
      try {
        const r = await getDeviceList(this.page, this.pageSize)
        if (r.code === 200) { this.list = r.data.list; this.total = r.data.total }
      } catch (e) {}
    },
    rowClass({ row }) { return row.isShared ? 'row-shared' : '' }
  }
}
</script>

<style scoped>
.device-risk-page { height: 100%; overflow-y: auto; }
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
.stat-num.muted { color: var(--color-text-secondary); }
.stat-label { font-size: var(--text-xs); color: var(--color-text-muted); margin-top: 2px; }
.panel { background: var(--color-bg-elevated); border: 1px solid var(--color-border); border-radius: var(--radius-sm); padding: var(--space-3); }
.text-muted { color: var(--color-text-muted); font-size: var(--text-sm); }
.text-danger { color: var(--color-danger); font-weight: 600; }
.text-warning { color: var(--color-warning); }
.el-table .row-shared { background: rgba(220,38,38,0.06) !important; }
</style>
