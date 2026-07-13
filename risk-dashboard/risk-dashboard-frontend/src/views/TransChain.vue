<template>
  <div class="ch-page">
    <div class="page-header">
      <div class="header-left"><h2 class="page-title">资金链路追踪</h2><span class="header-sub">共 <b>{{ total }}</b> 条</span></div>
      <el-button size="mini" icon="el-icon-back" @click="$router.push('/offline-overview')">返回离线总览</el-button>
    </div>
    <div class="stats-row">
      <div class="stat-item"><div class="stat-num muted">{{ stats.totalChains }}</div><div class="stat-label">总链路</div></div>
      <div class="stat-item" :class="{active:showLoop}" @click="toggleLoop"><div class="stat-num danger">{{ stats.loopChains }}</div><div class="stat-label">环形回流</div></div>
    </div>
    <div class="filter-bar">
      <el-switch v-model="showLoop" active-text="仅环形" @change="toggleLoop"/>
    </div>
    <div class="panel" style="margin-top:10px;">
      <el-table :data="list" stripe size="mini" :row-class-name="rowClass" @row-click="showDetail">
        <el-table-column prop="chainId" label="链路ID" width="140"/>
        <el-table-column prop="fromUserId" label="转出方" width="100"/>
        <el-table-column label="" width="40" align="center"><template><span style="color:var(--color-primary)">→</span></template></el-table-column>
        <el-table-column prop="toUserId" label="转入方" width="100"/>
        <el-table-column prop="amount" label="金额" width="100" sortable><template slot-scope="{row}"><span class="mn">¥{{ row.amount?.toFixed(2) }}</span></template></el-table-column>
        <el-table-column prop="hopOrder" label="跳转序" width="70"/>
        <el-table-column prop="chainDepth" label="深度" width="60"/>
        <el-table-column prop="isLoop" label="环形" width="60"><template slot-scope="{row}"><el-tag v-if="row.isLoop===1" type="danger" size="mini" effect="dark">是</el-tag><span v-else class="tm">否</span></template></el-table-column>
        <el-table-column label="交易时间" width="160"><template slot-scope="{row}"><span class="tm">{{ ft(row.transTime) }}</span></template></el-table-column>
        <el-table-column prop="createTime" label="录入时间" width="155"/>
      </el-table>
      <el-pagination style="margin-top:10px;text-align:right;" background layout="total, prev, pager, next" :total="total" :page-size="pageSize" :current-page.sync="page" @current-change="fetchData"/>
    </div>
    <el-dialog :title="'链路 — '+chainId" :visible.sync="dv" width="600px" append-to-body>
      <div class="cf" v-if="nodes.length">
        <div class="cn" v-for="(n,i) in nodes" :key="i">
          <div class="nc" :class="{loop:n.isLoop===1}"><div class="nl">节点 {{i+1}}</div><div class="nu">{{n.fromUserId}}</div><div class="na">¥{{n.amount?.toFixed(2)}}</div></div>
          <div class="nar" v-if="i<nodes.length-1"><span class="ai">↓</span></div>
        </div>
        <div class="cn"><div class="nc end"><div class="nl">终点</div><div class="nu">{{nodes[nodes.length-1]?.toUserId}}</div></div></div>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { getChainList, getLoopChains, getChainDetail, getChainStats } from '@/api/chain'
export default {
  name:'TransChain',
  data(){ return { list:[], nodes:[], stats:{totalChains:0,loopChains:0}, showLoop:false, page:1, pageSize:20, total:0, dv:false, chainId:'' } },
  async mounted(){ await Promise.all([this.fetchData(),this.loadStats()]) },
  methods:{
    async loadStats(){ try{const r=await getChainStats(); if(r.code===200)this.stats=r.data}catch(e){} },
    async fetchData(){ try{const r=this.showLoop?await getLoopChains(this.pageSize):await getChainList(this.page,this.pageSize); if(r.code===200){if(this.showLoop){this.list=r.data||[];this.total=this.list.length}else{this.list=r.data.list;this.total=r.data.total}}}catch(e){} },
    toggleLoop(){ this.page=1; this.fetchData() },
    async showDetail(row){ this.chainId=row.chainId; this.nodes=[]; this.dv=true; try{const r=await getChainDetail(row.chainId); if(r.code===200)this.nodes=r.data||[]}catch(e){} },
    rowClass({row}){ return row.isLoop===1?'rl':'' },
    ft(ts){ return ts?new Date(ts).toLocaleString('zh-CN'):'--' }
  }
}
</script>

<style scoped>
.ch-page{height:100%;overflow-y:auto}.page-header{display:flex;justify-content:space-between;align-items:center;gap:var(--space-3);margin-bottom:var(--space-3);padding-bottom:var(--space-3);border-bottom:1px solid var(--color-border)}.page-title{color:var(--color-text-primary);font-size:var(--text-lg);font-weight:600;margin:0}.header-left{display:flex;align-items:center;gap:var(--space-3)}.header-sub{color:var(--color-text-muted);font-size:var(--text-sm)}.header-sub b{color:var(--color-text-secondary)}.header-link{font-size:11px;color:var(--color-text-muted);text-decoration:none;padding:3px 8px;border-radius:var(--radius-sm)}.header-link:hover{color:var(--color-primary)}.stats-row{display:flex;gap:var(--space-2);margin-bottom:var(--space-3)}.stat-item{flex:1;text-align:center;padding:var(--space-3);background:var(--color-bg-elevated);border:1px solid var(--color-border);border-radius:var(--radius-sm);cursor:pointer}.stat-item.active{border-color:var(--color-danger)}.stat-num{font-family:var(--font-mono);font-size:22px;font-weight:600}.stat-num.danger{color:var(--color-danger)}.stat-num.muted{color:var(--color-text-secondary)}.stat-label{font-size:var(--text-xs);color:var(--color-text-muted);margin-top:2px}.filter-bar{display:flex;align-items:center;padding:var(--space-2) var(--space-3);background:var(--color-bg-elevated);border:1px solid var(--color-border);border-radius:var(--radius-sm);margin-bottom:var(--space-2)}.panel{background:var(--color-bg-elevated);border:1px solid var(--color-border);border-radius:var(--radius-sm);padding:var(--space-3)}.tm{color:var(--color-text-muted);font-size:var(--text-sm)}.mn{font-family:var(--font-mono);font-size:var(--text-sm)}.el-table .rl{background:rgba(220,38,38,.06)!important}.cf{display:flex;flex-direction:column;align-items:center;gap:0}.cn{display:flex;flex-direction:column;align-items:center}.nc{background:var(--color-bg-surface);border:1px solid var(--color-border);border-radius:var(--radius-sm);padding:10px 20px;text-align:center;min-width:120px}.nc.loop{border-color:var(--color-danger)}.nc.end{border-color:var(--color-primary)}.nl{font-size:10px;color:var(--color-text-muted)}.nu{font-size:var(--text-sm);font-weight:600;color:var(--color-text-primary)}.na{font-size:12px;font-family:var(--font-mono);color:var(--color-warning);margin-top:2px}.nar{display:flex;flex-direction:column;align-items:center;padding:4px 0}.ai{font-size:18px;color:var(--color-primary)}
</style>
