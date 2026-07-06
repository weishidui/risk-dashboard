<template>
  <div class="full-page">
    <div class="page-header">
      <h2 class="page-title">账号管理</h2>
      <span class="header-sub">共 <b>{{ users.length }}</b> 个用户</span>
    </div>

    <div class="panel flex-1" style="margin-top:6px;">
      <el-table :data="users" stripe size="mini" max-height="100%" class="flex-table">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="username" label="用户名" width="150" />
        <el-table-column prop="role" label="角色" width="120">
          <template slot-scope="{ row }"><el-tag :type="rTag(row.role)" size="mini">{{ rLabel(row.role) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="200" v-if="!isAuditor">
          <template slot-scope="{ row }">
            <el-button size="mini" type="warning" @click="showPwd(row)">重置密码</el-button>
            <el-button size="mini" type="danger" @click="doDel(row)" :disabled="row.username === 'admin'">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog title="重置密码" :visible.sync="pwdVisible" width="360px" append-to-body>
      <el-form label-width="70px" size="small">
        <el-form-item label="用户名"><span>{{ pwdUser.username }}</span></el-form-item>
        <el-form-item label="新密码"><el-input v-model="newPwd" type="password" placeholder="请输入新密码" /></el-form-item>
      </el-form>
      <span slot="footer">
        <el-button size="small" @click="pwdVisible = false">取消</el-button>
        <el-button size="small" type="primary" @click="doPwd">确认</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import request from '@/api/request'
import { ROLE_LABELS } from '@/utils/constants'

export default {
  name: 'UserManage',
  data() { return { users: [], pwdVisible: false, pwdUser: {}, newPwd: '' } },
  computed: { isAuditor() { return this.$store.getters.userRole === 'auditor' } },
  mounted() { this.load() },
  methods: {
    async load() { try { const r = await request.get('/admin/users'); if (r.code === 200) this.users = r.data || [] } catch {} },
    rLabel(r) { return ROLE_LABELS[r] || r },
    rTag(r) { return r === 'admin' ? 'danger' : r === 'analyst' ? 'warning' : 'info' },
    showPwd(r) { this.pwdUser = r; this.newPwd = ''; this.pwdVisible = true },
    async doPwd() {
      if (!this.newPwd) return this.$message.warning('请输入新密码')
      try { const r = await request.put(`/admin/users/${this.pwdUser.username}/reset-password`, { password: this.newPwd }); if (r.code === 200) { this.$message.success('密码重置成功'); this.pwdVisible = false } else this.$message.error(r.message) } catch {}
    },
    async doDel(row) {
      try {
        await this.$confirm(`确认删除「${row.username}」？`, '提示', { type: 'warning' })
        const r = await request.delete(`/admin/users/${row.username}`)
        if (r.code === 200) { this.$message.success('删除成功'); this.load() } else this.$message.error(r.message)
      } catch {}
    }
  }
}
</script>

<style scoped>
.full-page { height: 100%; display: flex; flex-direction: column; overflow: hidden; }
.page-header { display: flex; align-items: center; gap: 10px; padding-bottom: 6px; border-bottom: 1px solid var(--color-border); flex-shrink: 0; }
.page-title { color: var(--color-text-primary); font-size: 15px; font-weight: 600; margin: 0; }
.header-sub { color: var(--color-text-muted); font-size: 12px; }
.header-sub b { color: var(--color-text-secondary); }

.flex-1 { flex: 1; min-height: 0; height: 0; }
.panel { background: var(--color-bg-elevated); border: 1px solid var(--color-border); border-radius: var(--radius-sm); padding: 6px 8px; display: flex; flex-direction: column; overflow: hidden; }
.flex-table { flex: 1; min-height: 0; }
</style>
