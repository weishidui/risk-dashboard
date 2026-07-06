<template>
  <div class="full-page">
    <div class="page-header">
      <h2 class="page-title">风险告警管理</h2>
      <span class="header-sub">共 <b>{{ total }}</b> 条</span>
    </div>

    <div class="stats-row">
      <div class="stat-item" :class="{ active: filters.riskLevel === '极度危险' }" @click="quickFilter('极度危险')">
        <div class="stat-num critical">{{ stats.critical }}</div><div class="stat-label">极度危险</div>
      </div>
      <div class="stat-item" :class="{ active: filters.riskLevel === '高危' }" @click="quickFilter('高危')">
        <div class="stat-num danger">{{ stats.high }}</div><div class="stat-label">高危</div>
      </div>
      <div class="stat-item" :class="{ active: filters.riskLevel === '中危' }" @click="quickFilter('中危')">
        <div class="stat-num warning">{{ stats.mid }}</div><div class="stat-label">中危</div>
      </div>
      <div class="stat-item" :class="{ active: filters.riskLevel === '低危' }" @click="quickFilter('低危')">
        <div class="stat-num success">{{ stats.low }}</div><div class="stat-label">低危</div>
      </div>
    </div>

    <div class="filter-bar">
      <el-select v-model="filters.riskLevel" clearable placeholder="风险等级" size="small" class="filter-select">
        <el-option label="极度危险" value="极度危险" /><el-option label="高危" value="高危" />
        <el-option label="中危" value="中危" /><el-option label="低危" value="低危" />
      </el-select>
      <el-select v-model="filters.status" clearable placeholder="处理状态" size="small" class="filter-select">
        <el-option label="待处理" value="pending" /><el-option label="处理中" value="processing" />
        <el-option label="已核验" value="verified" /><el-option label="已拦截" value="blocked" />
        <el-option label="已关闭" value="closed" />
      </el-select>
      <el-button type="primary" size="small" @click="search">查询</el-button>
      <el-button size="small" @click="reset">重置</el-button>
    </div>

    <div class="panel flex-1" style="margin-top:6px;">
      <el-table :data="alertList" stripe size="mini" max-height="100%" class="flex-table">
        <el-table-column prop="alertId" label="告警编号" width="200" />
        <el-table-column prop="transId" label="交易流水" width="200" />
        <el-table-column prop="userId" label="用户" width="100" />
        <el-table-column prop="hitRules" label="触发规则" min-width="150" show-overflow-tooltip />
        <el-table-column prop="amount" label="金额" width="110" sortable>
          <template slot-scope="{ row }"><span class="mono-num">¥{{ row.amount?.toFixed(2) }}</span></template>
        </el-table-column>
        <el-table-column prop="finalScore" label="评分" width="70" sortable>
          <template slot-scope="{ row }">
            <span v-if="row.finalScore > 120" class="score-tag critical">{{ row.finalScore }}</span>
            <el-tag v-else :type="sType(row.finalScore)" size="mini" effect="dark">{{ row.finalScore }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="riskLevel" label="等级" width="80">
          <template slot-scope="{ row }"><span :class="['risk-text', rClass(row.riskLevel)]">{{ row.riskLevel }}</span></template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template slot-scope="{ row }"><el-tag :type="stType(row.status)" size="mini">{{ stLabel(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="alertLoc" label="城市" width="70" />
        <el-table-column prop="handler" label="处理人" width="80">
          <template slot-scope="{ row }"><span :class="{ 'text-muted': !row.handler }">{{ row.handler || '—' }}</span></template>
        </el-table-column>
        <el-table-column prop="createTime" label="时间" width="155" sortable />
        <el-table-column label="操作" width="160" fixed="right">
          <template slot-scope="{ row }">
            <template v-if="isAuditor">
              <span class="text-muted">{{ stLabel(row.status) }}</span>
            </template>
            <template v-else>
              <template v-if="row.status === 'pending'">
                <el-button type="primary" size="mini" @click.stop="handleAction(row, 'processing')">受理</el-button>
              </template>
              <template v-else-if="row.status === 'processing'">
                <span class="action-inline">
                  <el-button type="success" size="mini" @click.stop="handleAction(row, 'verified')">放行</el-button>
                  <el-button type="danger" size="mini" @click.stop="handleAction(row, 'blocked')">拦截</el-button>
                </span>
              </template>
              <el-button v-if="row.status === 'verified' || row.status === 'blocked'" type="info" size="mini" @click.stop="handleAction(row, 'closed')">关闭</el-button>
              <span v-if="row.status === 'closed'" class="text-muted">已关闭</span>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination style="padding:6px 0;text-align:right;flex-shrink:0" background layout="total, prev, pager, next"
        :total="total" :page-size="pageSize" :current-page.sync="page" @current-change="search" />
    </div>

    <el-dialog title="告警处理" :visible.sync="dialogVisible" width="400px" append-to-body>
      <el-form label-width="70px" size="small">
        <el-form-item label="当前状态"><el-tag :type="stType(currentAlert.status)">{{ stLabel(currentAlert.status) }}</el-tag></el-form-item>
        <el-form-item label="目标状态"><el-tag :type="stType(targetStatus)">{{ stLabel(targetStatus) }}</el-tag></el-form-item>
        <el-form-item label="处理备注"><el-input v-model="remark" type="textarea" :rows="2" placeholder="请输入处理意见..." /></el-form-item>
      </el-form>
      <span slot="footer">
        <el-button size="small" @click="dialogVisible = false">取消</el-button>
        <el-button size="small" type="primary" @click="confirmAction">确认</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import { getAlertList, getRiskLevelStat, updateAlertStatus } from '@/api/alert'

const ST = { pending: 'info', processing: 'warning', verified: 'success', blocked: 'danger', closed: 'info' }
const SL = { pending: '待处理', processing: '处理中', verified: '已核验', blocked: '已拦截', closed: '已关闭' }

export default {
  name: 'AlertManage',
  data() {
    return {
      filters: { riskLevel: '', status: '' }, alertList: [],
      stats: { critical: 0, high: 0, mid: 0, low: 0 },
      page: 1, pageSize: 20, total: 0,
      dialogVisible: false, currentAlert: {}, targetStatus: '', remark: ''
    }
  },
  computed: { isAuditor() { return this.$store.getters.userRole === 'auditor' } },
  mounted() { this.init() },
  methods: {
    async init() { await Promise.all([this.search(), this.loadStats()]) },
    async loadStats() {
      try {
        const res = await getRiskLevelStat()
        if (res.code === 200) {
          const d = res.data || []
          this.stats.critical = (d.find(x => x.name === '极度危险') || {}).value || 0
          this.stats.high = (d.find(x => x.name === '高危') || {}).value || 0
          this.stats.mid = (d.find(x => x.name === '中危') || {}).value || 0
          this.stats.low = (d.find(x => x.name === '低危') || {}).value || 0
        }
      } catch {}
    },
    quickFilter(level) {
      this.filters.riskLevel = this.filters.riskLevel === level ? '' : level
      this.page = 1; this.search()
    },
    async search() {
      try {
        const res = await getAlertList({ riskLevel: this.filters.riskLevel || undefined, status: this.filters.status || undefined, page: this.page, pageSize: this.pageSize })
        if (res.code === 200) { this.alertList = res.data.list; this.total = res.data.total }
      } catch {}
    },
    reset() { this.filters = { riskLevel: '', status: '' }; this.page = 1; this.search() },
    handleAction(row, status) { this.currentAlert = row; this.targetStatus = status; this.remark = ''; this.dialogVisible = true },
    async confirmAction() {
      try {
        const res = await updateAlertStatus(this.currentAlert.alertId, this.targetStatus, this.$store.getters.username || 'system', this.remark || '')
        if (res.code === 200) { this.$message.success('状态更新成功'); this.dialogVisible = false; this.search() }
        else this.$message.error(res.message || '操作失败')
      } catch { this.$message.error('网络错误') }
    },
    sType(s) { return s >= 71 ? 'danger' : s >= 41 ? 'warning' : 'success' },
    rClass(l) { if (l === '极度危险') return 'critical'; if (l === '高危') return 'danger'; if (l === '中危') return 'warning'; return 'success' },
    stType(s) { return ST[s] || 'info' },
    stLabel(s) { return SL[s] || s }
  }
}
</script>

<style scoped>
.full-page { height: 100%; display: flex; flex-direction: column; overflow: hidden; }

.page-header { display: flex; align-items: center; gap: 10px; padding-bottom: 6px; border-bottom: 1px solid var(--color-border); flex-shrink: 0; }
.page-title { color: var(--color-text-primary); font-size: 15px; font-weight: 600; margin: 0; }
.header-sub { color: var(--color-text-muted); font-size: 12px; }
.header-sub b { color: var(--color-text-secondary); }

.stats-row { display: flex; gap: 6px; padding: 6px 0; flex-shrink: 0; }
.stat-item { flex: 1; text-align: center; padding: 6px 8px; background: var(--color-bg-elevated); border: 1px solid var(--color-border); border-radius: var(--radius-sm); cursor: pointer; }
.stat-item.active { border-color: var(--color-primary); }
.stat-num { font-family: var(--font-mono); font-size: 18px; font-weight: 600; color: var(--color-text-primary); }
.stat-num.critical { color: var(--color-critical); }
.stat-num.danger   { color: var(--color-danger); }
.stat-num.warning  { color: var(--color-warning); }
.stat-num.success  { color: var(--color-success); }
.stat-label { font-size: 10px; color: var(--color-text-muted); }

.filter-bar { display: flex; align-items: center; gap: 8px; padding: 6px 8px; background: var(--color-bg-elevated); border: 1px solid var(--color-border); border-radius: var(--radius-sm); flex-shrink: 0; }
.filter-select { width: 110px; }

.flex-1 { flex: 1; min-height: 0; height: 0; }
.panel { background: var(--color-bg-elevated); border: 1px solid var(--color-border); border-radius: var(--radius-sm); padding: 6px 8px; display: flex; flex-direction: column; overflow: hidden; }
.flex-table { flex: 1; min-height: 0; }

.risk-text { font-weight: 600; font-size: 11px; }
.risk-text.critical { color: var(--color-critical); }
.risk-text.danger   { color: var(--color-danger); }
.risk-text.warning  { color: var(--color-warning); }
.risk-text.success  { color: var(--color-success); }
.text-muted { color: var(--color-text-muted); font-size: 11px; }
.mono-num { font-family: var(--font-mono); font-size: 11px; }
.action-inline { display: inline-flex; gap: 4px; }

.score-tag { display: inline-block; padding: 0 5px; height: 18px; line-height: 18px; font-size: 10px; font-weight: 700; font-family: var(--font-mono); color: #fff; background: #991B1B; border-radius: 2px; }
</style>
