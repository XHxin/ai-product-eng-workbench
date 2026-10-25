<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useAuthStore } from './stores/auth'
import LoginView from './views/LoginView.vue'
import WorkbenchView from './views/WorkbenchView.vue'

const auth = useAuthStore()
const restoring = ref(true)

onMounted(async () => {
  try { await auth.restore() } catch { /* unauthenticated is expected */ }
  finally { restoring.value = false }
})
</script>
<template>
  <div v-if="restoring" class="login"><div class="login-card">正在校验登录状态…</div></div>
  <WorkbenchView v-else-if="auth.loggedIn" />
  <LoginView v-else />
</template>
