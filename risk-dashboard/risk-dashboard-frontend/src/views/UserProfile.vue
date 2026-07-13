<template>
  <div class="profile-page">
    <div class="page-header">
      <div class="header-left">
        <h2 class="page-title">用户画像浏览</h2>
        <span class="header-sub">共 <b>{{ total }}</b> 位用户</span>
      </div>
      <el-button size="mini" icon="el-icon-back" @click="$router.push('/offline-overview')">返回总览</el-button>
    </div>
    <div class="stats-row">
      <div class="stat-item" :class="{ active: filters.accountStatus === 'normal' }" @click="quickStatus('normal')"><div class="stat-num success">{{ stats.normal }}</div><div class="stat-label">正常</div></div>
      <div class="stat-item" :class="{ active: filters.accountStatus === 'frozen' }" @click="quickStatus('frozen')"><div class="stat-num critical">{{ stats.frozen }}</div><div class="stat-label">冻结</div></div>
      <div class="stat-item" :class="{ active: filters.accountStatus === 'flagged' }" @click="quickStatus('flagged')"><div class="stat-num warning">{{ stats.flagged }}</div><div class="stat-label">已标记</div></div>
      <div class="stat-item" :class="{ active: filters.accountStatus === 'dormant' }" @click="quickStatus('dormant')"><div class="stat-num muted">{{ stats.dormant }}</div><div class="stat-label">休眠</div></div>
    </div>
    <div class="filter-bar">
      <el-select v-model="filters.accountStatus" clearable placeholder="账户状态" size="small" class="filter-select">
        <el-option label="正常" value="normal" /><el-option label="冻结" value="frozen" /><el-option label="已标记" value="flagged" /><el-option label="休眠" value="dormant" />
      </el-select>
      <el-select v-model="filters.riskLevel" clearable placeholder="风险等级" size="small" class="filter-select">
        <el-option label="高风险" value="high" /><el-option label="中风险" value="medium" /><el-option label="低风险" value="low" />
      </el-select>
      <el-button type="primary" size="small" @click="search">查询</el-button>
      <el-button size="small" @click="reset">重置</el-button>
    </div>
    <div class="panel" style="margin-top:10px;">
      <el-table :data="list" stripe size="mini" @row-click="showDetail">
        <el-table-column prop="userId" label="用户ID" width="140" />
        <el-table-column prop="avgAmt30d" label="30日均额" width="110" sortable><template slot-scope="{ row }"><span class="mono-num">¥{{ row.avgAmt30d?.toFixed(2) }}</span></template></el-table-column>
        <el-table-column prop="commonCities" label="常用城市" min-width="120" show-overflow-tooltip />
        <el-table-column prop="totalBalance" label="余额" width="110" sortable><template slot-scope="{ row }"><span class="mono-num">¥{{ row.totalBalance?.toFixed(2) }}</span></template></el-table-column>
        <el-table-column prop="accountStatus" label="账户状态" width="80"><template slot-scope="{ row }"><el-tag :type="statusType(row.accountStatus)" size="mini">{{ statusLabel(row.accountStatus) }}</el-tag></template></el-table-column>
        <el-table-column prop="riskScore" label="风险评分" width="80" sortable><template slot-scope="{ row }"><span :class="['risk-text', scoreClass(row.riskScore)]">{{ row.riskScore }}</span></template></el-table-column>
        <el-table-column prop="transCount24h" label="24H交易" width="80" />
        <el-table-column prop="transCount7d" label="7天交易" width="80" />
        <el-table-column label="上次交易" width="155"><template slot-scope="{ row }"><span class="text-muted">{{ fmtTime(row.lastTransTs) }}</span></template></el-table-column>
        <el-table-column prop="updateTime" label="更新时间" width="155" />
      </el-table>
      <el-pagination style="margin-top:10px;text-align:right;" background layout="total, prev, pager, next" :total="total" :page-size="pageSize" :current-page.sync="page" @current-change="search" />
    </div>
    <el-dialog :title="'用户画像 — ' + detail.userId" :visible.sync="dialogVisible" width="560px" append-to-body>
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
        <el-tab-pane label="账户信息">
          <el-form label-width="120px" size="small">
            <el-form-item label="余额">¥{{ detail.totalBalance?.toFixed(2) }}</el-form-item>
            <el-form-item label="单笔限额">¥{{ detail.singleLimit?.toFixed(2) }}</el-form-item>
            <el-form-item label="日累计限额">¥{{ detail.dailyLimit?.toFixed(2) }}</el-form-item>
            <el-form-item label="账户状态"><el-tag :type="statusType(detail.accountStatus)" size="mini">{{ statusLabel(detail.accountStatus) }}</el-tag></el-form-item>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="风险指标">
          <el-form label-width="120px" size="small">
            <el-form-item label="风险评分"><span :class="['risk-text', scoreClass(detail.riskScore)]">{{ detail.riskScore }}</span></el-form-item>
            <el-form-item label="风险标签"><el-tag v-if="detail.riskTags" size="mini" type="danger">{{ detail.riskTags }}</el-tag><span v-else class="text-muted">无</span></el-form-item>
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
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>
  </div>
</template>

<script>
import { getProfileList, getProfileDetail, getProfileStats } from '@/api/profile'
const SM = { normal:{label:'正常',type:'success'}, frozen:{label:'冻结',type:'danger'}, flagged:{label:'已标记',type:'warning'}, dormant:{label:'休眠',type:'info'} }
export default {
  name: 'UserProfile',
  data() { return { filters:{accountStatus:'',riskLevel:''}, list:[], stats:{normal:0,frozen:0,flagged:0,dormant:0}, page:1, pageSize:20, total:0, dialogVisible:false, detail:{} } },
  async mounted() { await Promise.all([this.search(),this.loadStats()]) },
  methods: {
    async loadStats() { try { const r=await getProfileStats(); if(r.code===200){ const d=r.data; this.stats.normal=d.normal||0; this.stats.frozen=d.frozen||0; this.stats.flagged=d.flagged||0; this.stats.dormant=d.dormant||0 } } catch(e){} },
    quickStatus(s){ this.filters.accountStatus=this.filters.accountStatus===s?'':s; this.page=1; this.search() },
    async search(){ try { const p={page:this.page,pageSize:this.pageSize}; if(this.filters.accountStatus)p.accountStatus=this.filters.accountStatus; if(this.filters.riskLevel)p.riskLevel=this.filters.riskLevel; const r=await getProfileList(p); if(r.code===200){ this.list=r.data.list; this.total=r.data.total } } catch(e){} },
    reset(){ this.filters={accountStatus:'',riskLevel:''}; this.page=1; this.search() },
    async showDetail(row){ try { const r=await getProfileDetail(row.userId); if(r.code===200){ this.detail=r.data; this.dialogVisible=true } } catch(e){} },
    statusType(s){ return (SM[s]||{}).type||'info' }, statusLabel(s){ return (SM[s]||{}).label||s },
    scoreClass(s){ if(s>=80)return'critical'; if(s>=40)return'warning'; return'success' },
    fmtTime(ts){ return ts?new Date(ts).toLocaleString('zh-CN'):'--' }
  }
}
</script>

<style scoped>
.profile-page { height: 100%; overflow-y: auto; }
.page-header { display: flex; justify-content: space-between; align-items: center; gap: var(--space-3); margin-bottom: var(--space-3); padding-bottom: var(--space-3); border-bottom: 1px solid var(--color-border); }
.page-title { color: var(--color-text-primary); font-size: var(--text-lg); font-weight: 600; margin: 0; }
.header-left { display: flex; align-items: center; gap: var(--space-3); }
.header-sub { color: var(--color-text-muted); font-size: var(--text-sm); }
.header-sub b { color: var(--color-text-secondary); }
.stats-row { display: flex; gap: var(--space-2); margin-bottom: var(--space-3); }
.stat-item { flex: 1; text-align: center; padding: var(--space-3); background: var(--color-bg-elevated); border: 1px solid var(--color-border); border-radius: var(--radius-sm); cursor: pointer; }
.stat-item.active { border-color: var(--color-primary); }
.stat-num { font-family: var(--font-mono); font-size: 22px; font-weight: 600; }
.stat-num.success { color: var(--color-success); } .stat-num.critical { color: var(--color-critical); } .stat-num.warning { color: var(--color-warning); } .stat-num.muted { color: var(--color-text-secondary); }
.stat-label { font-size: var(--text-xs); color: var(--color-text-muted); margin-top: 2px; }
.filter-bar { display: flex; align-items: center; gap: var(--space-2); padding: var(--space-2) var(--space-3); background: var(--color-bg-elevated); border: 1px solid var(--color-border); border-radius: var(--radius-sm); }
.filter-select { width: 120px; }
.panel { background: var(--color-bg-elevated); border: 1px solid var(--color-border); border-radius: var(--radius-sm); padding: var(--space-3); }
.risk-text { font-weight: 600; font-size: var(--text-sm); }
.risk-text.critical { color: var(--color-critical); } .risk-text.warning { color: var(--color-warning); } .risk-text.success { color: var(--color-success); }
.text-muted { color: var(--color-text-muted); font-size: var(--text-sm); }
.mono-num { font-family: var(--font-mono); font-size: var(--text-sm); }
</style>
