<script setup lang="ts">
import { ref } from 'vue'
import { useAuthStore } from '../stores/auth'
const auth = useAuthStore()
const username = ref('pm')
const password = ref('')
async function submit() {
  if (!username.value || !password.value) return
  try { await auth.login(username.value, password.value) } catch { /* error rendered by store */ }
}
</script>
<template>
  <main class="login">
    <section class="login-card">
      <div class="logo">AI</div>
      <h1>AI产研协作工作台</h1>
      <p>统一连接需求、研发与发布流程</p>
      <form @submit.prevent="submit">
        <label>账号<input v-model="username" autocomplete="username" maxlength="100" /></label>
        <label>密码<input v-model="password" type="password" autocomplete="current-password" maxlength="200" /></label>
        <button class="btn btn-primary" :disabled="auth.loading">{{ auth.loading ? '登录中…' : '登录' }}</button>
        <div v-if="auth.error" class="error" style="margin-top:12px">{{ auth.error }}</div>
      </form>
      <div class="login-hint">角色由后端分配：admin / pm / dev / release。权限校验以服务端为准。</div>
    </section>
  </main>
</template>
