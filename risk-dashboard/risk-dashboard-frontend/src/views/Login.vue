<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-logo">RISK MONITOR</div>
      <div class="auth-title">金融交易风险实时监控平台</div>
      <el-form ref="form" :model="form" :rules="rules" size="medium" @submit.native.prevent="handleLogin">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" prefix-icon="el-icon-user" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" prefix-icon="el-icon-lock" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" native-type="submit" style="width:100%">登 录</el-button>
        </el-form-item>
      </el-form>
      <div class="auth-footer">
        还没有账号？<router-link to="/register">立即注册</router-link>
      </div>
      <div class="auth-hint">默认管理员: admin / admin123</div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'Login',
  data() {
    return {
      form: { username: '', password: '' },
      rules: {
        username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
        password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
      },
      loading: false
    }
  },
  methods: {
    async handleLogin() {
      try {
        await this.$refs.form.validate()
      } catch { return }
      this.loading = true
      try {
        const res = await this.$store.dispatch('login', this.form)
        if (res.code === 200) {
          this.$message.success('登录成功')
          this.$router.push('/dashboard')
        } else {
          this.$message.error(res.message || '登录失败')
        }
      } catch (e) {
        this.$message.error('网络异常')
      } finally { this.loading = false }
    }
  }
}
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-bg-deep);
}

.auth-card {
  width: 380px;
  padding: 40px 36px 28px;
  background: var(--color-bg-elevated);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}

.auth-logo {
  text-align: center;
  font-size: 22px;
  font-weight: 700;
  color: var(--color-primary);
  letter-spacing: 4px;
  margin-bottom: 4px;
}

.auth-title {
  text-align: center;
  font-size: 13px;
  color: var(--color-text-muted);
  margin-bottom: 28px;
}

.auth-footer {
  text-align: center;
  font-size: 13px;
  color: var(--color-text-muted);
}

.auth-footer a {
  color: var(--color-primary);
  text-decoration: none;
}

.auth-hint {
  text-align: center;
  font-size: 11px;
  color: var(--color-text-disabled);
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--color-border);
}
</style>
