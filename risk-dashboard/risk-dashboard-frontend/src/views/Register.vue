<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-logo">RISK MONITOR</div>
      <div class="auth-title">创建新账号</div>
      <el-form ref="form" :model="form" :rules="rules" size="medium" @submit.native.prevent="handleRegister">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" prefix-icon="el-icon-user" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" prefix-icon="el-icon-lock" show-password />
        </el-form-item>
        <el-form-item prop="confirm">
          <el-input v-model="form.confirm" type="password" placeholder="确认密码" prefix-icon="el-icon-lock" show-password />
        </el-form-item>
        <el-form-item prop="role">
          <el-select v-model="form.role" placeholder="选择角色" style="width:100%">
            <el-option v-for="(label, val) in registerRoles" :key="val" :label="label" :value="val" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" native-type="submit" style="width:100%">注 册</el-button>
        </el-form-item>
      </el-form>
      <div class="auth-footer">
        已有账号？<router-link to="/login">立即登录</router-link>
      </div>
    </div>
  </div>
</template>

<script>
import { REGISTERABLE_ROLE_KEYS, ROLE_LABELS } from '@/utils/constants'

export default {
  name: 'Register',
  data() {
    const validateConfirm = (rule, value, callback) => {
      if (value !== this.form.password) {
        callback(new Error('两次密码不一致'))
      } else { callback() }
    }
    return {
      form: { username: '', password: '', confirm: '', role: '' },
      rules: {
        username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
        password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
        confirm: [
          { required: true, message: '请确认密码', trigger: 'blur' },
          { validator: validateConfirm, trigger: 'blur' }
        ],
        role: [{ required: true, message: '请选择角色', trigger: 'change' }]
      },
      registerRoles: Object.fromEntries(
        REGISTERABLE_ROLE_KEYS.map(key => [key, ROLE_LABELS[key]])
      ),
      loading: false
    }
  },
  methods: {
    async handleRegister() {
      try { await this.$refs.form.validate() } catch { return }
      this.loading = true
      try {
        const res = await this.$store.dispatch('register', {
          username: this.form.username,
          password: this.form.password,
          role: this.form.role
        })
        if (res.code === 200) {
          this.$message.success('注册成功')
          this.$router.push('/dashboard')
        } else {
          this.$message.error(res.message || '注册失败')
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
</style>
