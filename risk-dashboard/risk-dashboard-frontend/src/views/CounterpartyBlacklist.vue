<template>
  <div class="bl-page">
    <div class="page-header">
      <div class="header-left"><h2 class="page-title">收款方黑名单管理</h2><span class="header-sub">共 <b>{{ total }}</b> 条</span></div>
      <el-button size="mini" icon="el-icon-back" @click="$router.push('/offline-overview')">返回离线总览</el-button>
    </div>
    <div class="stats-row">
      <div class="stat-item" :class="{active:filters.riskLevel==='high'}" @click="quickFilter('high')"><div class="stat-num danger">{{ stats.high }}</div><div class="stat-label">高风险</div></div>
      <div class="stat-item" :class="{active:filters.riskLevel==='medium'}" @click="quickFilter('medium')"><div class="stat-num warning">{{ stats.medium }}</div><div class="stat-label">中风险</div></div>
      <div class="stat-item" :class="{active:filters.riskLevel==='low'}" @click="quickFilter('low')"><div class="stat-num success">{{ stats.low }}</div><div class="stat-label">低风险</div></div>
    </div>
    <div class="filter-bar">
      <el-select v-model="filters.riskLevel" clearable placeholder="风险等级" size="small" class="fs"><el-option label="高风险" value="high"/><el-option label="中风险" value="medium"/><el-option label="低风险" value="low"/></el-select>
      <el-button type="primary" size="small" @click="search">查询</el-button>
      <el-button size="small" @click="reset">重置</el-button>
    </div>
    <div class="panel" style="margin-top:10px;">
      <el-table :data="list" stripe size="mini" @row-click="showDetail">
        <el-table-column prop="counterpartyId" label="收款方ID" width="140"/>
        <el-table-column prop="counterpartyName" label="名称" width="100"/>
        <el-table-column prop="riskLevel" label="等级" width="80"><template slot-scope="{row}"><span :class="['rt',rc(row.riskLevel)]">{{ rl(row.riskLevel) }}</span></template></el-table-column>
        <el-table-column prop="riskType" label="风险类型" min-width="100" show-overflow-tooltip/>
        <el-table-column prop="totalReceived24h" label="24H收款" width="100" sortable><template slot-scope="{row}"><span class="mn">¥{{ fk(row.totalReceived24h) }}</span></template></el-table-column>
        <el-table-column prop="uniquePayers24h" label="付款方数" width="80"/>
        <el-table-column prop="registrationDays" label="注册天数" width="80"/>
        <el-table-column prop="riskTags" label="标签" width="120"><template slot-scope="{row}"><el-tag v-if="row.riskTags" size="mini" type="danger" effect="dark">{{ row.riskTags }}</el-tag><span v-else class="tm">--</span></template></el-table-column>
        <el-table-column prop="updateTime" label="更新时间" width="155"/>
      </el-table>
      <el-pagination style="margin-top:10px;text-align:right;" background layout="total, prev, pager, next" :total="total" :page-size="pageSize" :current-page.sync="page" @current-change="search"/>
    </div>
    <el-dialog :title="'收款方 — '+detail.counterpartyId" :visible.sync="dv" width="480px" append-to-body>
      <el-form v-if="detail.counterpartyId" label-width="100px" size="small">
        <el-form-item label="ID">{{detail.counterpartyId}}</el-form-item>
        <el-form-item label="名称">{{detail.counterpartyName}}</el-form-item>
        <el-form-item label="等级"><span :class="['rt',rc(detail.riskLevel)]">{{rl(detail.riskLevel)}}</span></el-form-item>
        <el-form-item label="类型">{{detail.riskType}}</el-form-item>
        <el-form-item label="24H收款">¥{{detail.totalReceived24h?.toFixed(2)}}</el-form-item>
        <el-form-item label="7天收款">¥{{detail.totalReceived7d?.toFixed(2)}}</el-form-item>
        <el-form-item label="付款方数">{{detail.uniquePayers24h}}</el-form-item>
        <el-form-item label="标签"><el-tag v-if="detail.riskTags" size="mini" type="danger">{{detail.riskTags}}</el-tag></el-form-item>
      </el-form>
    </el-dialog>
  </div>
</template>

<script>
import { getBlacklist, getByRiskLevel, getRiskLevelStats, getBlacklistDetail } from '@/api/blacklist'
export default {
  name:'CounterpartyBlacklist',
  data(){ return { filters:{riskLevel:''}, list:[], stats:{high:0,medium:0,low:0}, page:1, pageSize:20, total:0, dv:false, detail:{} } },
  async mounted(){ await Promise.all([this.search(),this.loadStats()]) },
  methods:{
    async loadStats(){ try{const r=await getRiskLevelStats(); if(r.code===200){const d=r.data||[]; this.stats.high=(d.find(x=>x.name==='high')||{}).value||0; this.stats.medium=(d.find(x=>x.name==='medium')||{}).value||0; this.stats.low=(d.find(x=>x.name==='low')||{}).value||0}}catch(e){} },
    quickFilter(l){ this.filters.riskLevel=this.filters.riskLevel===l?'':l; this.page=1; this.search() },
    async search(){ try{const r=this.filters.riskLevel?await getByRiskLevel(this.filters.riskLevel,this.page,this.pageSize):await getBlacklist({page:this.page,pageSize:this.pageSize}); if(r.code===200){this.list=r.data.list;this.total=r.data.total}}catch(e){} },
    reset(){ this.filters.riskLevel=''; this.page=1; this.search() },
    async showDetail(row){ try{const r=await getBlacklistDetail(row.counterpartyId); if(r.code===200){this.detail=r.data;this.dv=true}}catch(e){} },
    rc(l){ if(l==='high')return'critical'; if(l==='medium')return'warning'; return'success' },
    rl(l){ if(l==='high')return'高风险'; if(l==='medium')return'中风险'; if(l==='low')return'低风险'; return l },
    fk(v){ return v?(v/10000).toFixed(1)+'万':'0' }
  }
}
</script>

<style scoped>
.bl-page{height:100%;overflow-y:auto}.page-header{display:flex;justify-content:space-between;align-items:center;gap:var(--space-3);margin-bottom:var(--space-3);padding-bottom:var(--space-3);border-bottom:1px solid var(--color-border)}.page-title{color:var(--color-text-primary);font-size:var(--text-lg);font-weight:600;margin:0}.header-left{display:flex;align-items:center;gap:var(--space-3)}.header-sub{color:var(--color-text-muted);font-size:var(--text-sm)}.header-sub b{color:var(--color-text-secondary)}.header-link{font-size:11px;color:var(--color-text-muted);text-decoration:none;padding:3px 8px;border-radius:var(--radius-sm)}.header-link:hover{color:var(--color-primary)}.stats-row{display:flex;gap:var(--space-2);margin-bottom:var(--space-3)}.stat-item{flex:1;text-align:center;padding:var(--space-3);background:var(--color-bg-elevated);border:1px solid var(--color-border);border-radius:var(--radius-sm);cursor:pointer}.stat-item.active{border-color:var(--color-primary)}.stat-num{font-family:var(--font-mono);font-size:22px;font-weight:600}.stat-num.danger{color:var(--color-danger)}.stat-num.warning{color:var(--color-warning)}.stat-num.success{color:var(--color-success)}.stat-label{font-size:var(--text-xs);color:var(--color-text-muted);margin-top:2px}.filter-bar{display:flex;align-items:center;gap:var(--space-2);padding:var(--space-2) var(--space-3);background:var(--color-bg-elevated);border:1px solid var(--color-border);border-radius:var(--radius-sm)}.fs{width:120px}.panel{background:var(--color-bg-elevated);border:1px solid var(--color-border);border-radius:var(--radius-sm);padding:var(--space-3)}.rt{font-weight:600;font-size:var(--text-sm)}.rt.critical{color:var(--color-critical)}.rt.warning{color:var(--color-warning)}.rt.success{color:var(--color-success)}.tm{color:var(--color-text-muted);font-size:var(--text-sm)}.mn{font-family:var(--font-mono);font-size:var(--text-sm)}
</style>
