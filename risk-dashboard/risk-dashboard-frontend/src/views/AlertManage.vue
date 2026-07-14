<template>
  <div class="alert-manage">
    <div class="page-header">
      <div class="header-left">
        <h2 class="page-title">风险告警管理</h2>
        <span class="header-sub">共 <b>{{ total }}</b> 条</span>
      </div>
      <el-button size="mini" icon="el-icon-back" @click="$router.push('/dashboard')">返回主页</el-button>
    </div>

    <div class="stats-row">
      <div class="stat-item" :class="{ active: filters.riskLevel === '极度危险' }" @click="quickFilter('极度危险')">
        <div class="stat-num critical">{{ stats.critical }}</div>
        <div class="stat-label">极度危险</div>
      </div>
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
        <el-option label="极度危险" value="极度危险" />
        <el-option label="高危" value="高危" />
        <el-option label="中危" value="中危" />
        <el-option label="低危" value="低危" />
      </el-select>
      <el-select v-model="filters.status" clearable placeholder="处理状态" size="small" class="filter-select">
        <el-option label="待处理" value="pending" />
        <el-option label="处理中" value="processing" />
        <el-option label="已核验" value="verified" />
        <el-option label="已拦截" value="blocked" />
        <el-option label="已关闭" value="closed" />
      </el-select>
      <el-button type="primary" size="small" @click="search">查询</el-button>
      <el-button size="small" @click="reset">重置</el-button>
    </div>

    <div class="panel" style="margin-top:10px;">
      <el-table :data="alertList" stripe size="mini" @row-click="showDetail">
        <el-table-column prop="alertId" label="告警编号" width="170" />
        <el-table-column prop="transId" label="交易流水" width="170" />
        <el-table-column prop="userId" label="用户" width="80" />
        <el-table-column prop="hitRules" label="触发规则" min-width="140" show-overflow-tooltip />
        <el-table-column prop="amount" label="金额" width="100" sortable>
          <template slot-scope="{ row }">
            <span class="mono-num">¥{{ row.amount?.toFixed(2) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="finalScore" label="评分" width="70" sortable>
          <template slot-scope="{ row }">
            <span v-if="row.finalScore > 120" class="score-critical-tag">{{ row.finalScore }}</span>
            <el-tag v-else :type="scoreType(row.finalScore)" size="mini" effect="dark">{{ row.finalScore }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="riskLevel" label="等级" width="80">
          <template slot-scope="{ row }">
            <span :class="['risk-text', riskClass(row.riskLevel)]">{{ row.riskLevel }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template slot-scope="{ row }">
            <el-tag :type="statusTagType(row.status)" size="mini">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="alertLoc" label="城市" width="70" />
        <el-table-column prop="handler" label="处理人" width="80">
          <template slot-scope="{ row }">
            <span :class="{ 'text-muted': !row.handler }">{{ row.handler || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="时间" width="155" sortable />
        <el-table-column label="操作" width="160">
          <template slot-scope="{ row }">
            <!-- 审计员：只读状态文字 -->
            <template v-if="isAuditor">
              <span class="text-muted">{{ statusLabel(row.status) }}</span>
            </template>
            <!-- 其他角色：操作按钮 -->
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

      <el-pagination
        style="margin-top:10px;text-align:right;"
        background layout="total, prev, pager, next"
        :total="total" :page-size="pageSize"
        :current-page.sync="page"
        @current-change="search" />
    </div>

    <!-- 处理对话框 -->
    <el-dialog :title="'告警处理 — ' + currentAlert.alertId" :visible.sync="dialogVisible" width="420px" append-to-body>
      <el-form label-width="70px" size="small">
        <el-form-item label="当前状态">
          <el-tag :type="statusTagType(currentAlert.status)">{{ statusLabel(currentAlert.status) }}</el-tag>
        </el-form-item>
        <el-form-item label="目标状态">
          <el-tag :type="statusTagType(targetStatus)">{{ statusLabel(targetStatus) }}</el-tag>
        </el-form-item>
        <el-form-item label="处理备注">
          <el-input v-model="remark" type="textarea" :rows="3" placeholder="请输入处理意见..." />
        </el-form-item>
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
import { ACTION_MAP } from '@/utils/constants'

const STATUS_MAP = {
  'pending': { label: '待处理', type: 'info' },
  'processing': { label: '处理中', type: 'warning' },
  'verified': { label: '已核验', type: 'success' },
  'blocked': { label: '已拦截', type: 'danger' },
  'closed': { label: '已关闭', type: 'info' }
}

export default {
  name: 'AlertManage',
  data() {
    return {
      filters: { riskLevel: '', status: '' },
      alertList: [],
      stats: { critical: 0, high: 0, mid: 0, low: 0 },
      loading: false,
      page: 1,
      pageSize: 20,
      total: 0,
      dialogVisible: false,
      currentAlert: {},
      targetStatus: '',
      remark: ''
    }
  },
  computed: {
    isAuditor() { return ['auditor', 'analyst', 'realtime_analyst', 'offline_analyst'].includes(this.$store.getters.userRole) }
  },
  mounted() {
    const qRisk = this.$route.query.riskLevel
    if (qRisk) this.filters.riskLevel = qRisk
    this.init()
  },
  methods: {
    async init() {
      await Promise.all([this.search(), this.loadStats()])
    },
    async loadStats() {
      try {
        const levelRes = await getRiskLevelStat()
        if (levelRes.code === 200) {
          const d = levelRes.data || []
          this.stats.critical = (d.find(x => x.name === '极度危险') || {}).value || 0
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
          status: this.filters.status || undefined,
          page: this.page, pageSize: this.pageSize
        })
        if (res.code === 200) {
          this.alertList = res.data.list
          this.total = res.data.total
        }
      } finally { this.loading = false }
    },
    reset() {
      this.filters = { riskLevel: '', status: '' }
      this.page = 1
      this.search()
    },
    showDetail(row) { /* click highlight, no-op */ },
    handleAction(row, targetStatus) {
      this.currentAlert = row
      this.targetStatus = targetStatus
      this.remark = ''
      this.dialogVisible = true
    },
    async confirmAction() {
      try {
        const res = await updateAlertStatus(
          this.currentAlert.alertId,
          this.targetStatus,
          this.$store.getters.username || 'system',
          this.remark || ''
        )
        if (res.code === 200) {
          this.$message.success('状态更新成功')
          this.dialogVisible = false
          this.search()
        } else {
          this.$message.error(res.message || '操作失败')
        }
      } catch (e) { this.$message.error('网络错误') }
    },
    scoreType(s) { return s > 120 ? '' : s >= 71 ? 'danger' : s >= 41 ? 'warning' : 'success' },
    riskClass(l) {
      if (l === '极度危险') return 'critical'
      if (l === '高危') return 'danger'
      if (l === '中危') return 'warning'
      return 'success'
    },
    statusLabel(s) { return (STATUS_MAP[s] || {}).label || s },
    statusTagType(s) { return (STATUS_MAP[s] || {}).type || 'info' },
    actionLabel(a) { return ACTION_MAP[a]?.label || a }
  }
}
</script>

<style scoped>
.alert-manage { height: 100%; overflow-y: auto; }

.page-header {
  display: flex;
  justify-content: space-between;
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

.header-left { display: flex; align-items: center; gap: var(--space-3); }
.header-sub { color: var(--color-text-muted); font-size: var(--text-sm); }
.header-sub b { color: var(--color-text-secondary); }

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

.stat-item.active { border-color: var(--color-primary); }

.stat-num {
  font-family: var(--font-mono);
  font-size: 22px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.stat-num.danger   { color: var(--color-danger); }
.stat-num.critical { color: var(--color-critical); }
.stat-num.warning  { color: var(--color-warning); }
.stat-num.success  { color: var(--color-success); }
.stat-num.muted    { color: var(--color-text-secondary); }

.stat-label { font-size: var(--text-xs); color: var(--color-text-muted); margin-top: 2px; }

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

.panel {
  background: var(--color-bg-elevated);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: var(--space-3);
}

.risk-text { font-weight: 600; font-size: var(--text-sm); }
.risk-text.critical { color: var(--color-critical); }
.risk-text.danger   { color: var(--color-danger); }
.risk-text.warning  { color: var(--color-warning); }
.risk-text.success  { color: var(--color-success); }

.text-muted { color: var(--color-text-muted); font-size: var(--text-sm); }

.action-inline { display: inline-flex; gap: 4px; }
.mono-num { font-family: var(--font-mono); font-size: var(--text-sm); }

.score-critical-tag {
  display: inline-block;
  padding: 0 6px;
  height: 20px;
  line-height: 20px;
  font-size: 11px;
  font-weight: 700;
  font-family: var(--font-mono);
  color: #fff;
  background: #991B1B;
  border-radius: 2px;
}
</style>
