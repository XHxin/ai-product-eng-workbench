<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { endpoints } from '../api/endpoints'
import type { AuditRecord } from '../api/types'
import { useAuthStore } from '../stores/auth'
import { can, formatDateTime, readableError } from '../ui/format'

const auth = useAuthStore()
const audits = ref<AuditRecord[]>([])
const error = ref('')
const loading = ref(false)

onMounted(load)

async function load() {
  if (!can.admin(auth.roles)) return
  loading.value = true
  error.value = ''
  try { audits.value = (await endpoints.audits()).content }
  catch (reason) { error.value = readableError(reason) }
  finally { loading.value = false }
}
</script>
<template>
  <div class="view-head"><div><h1>审计日志</h1><div class="desc">AI 输入输出、人工修改、状态流转与发布操作均可追溯</div></div></div>
  <div v-if="!can.admin(auth.roles)" class="panel"><div class="empty">审计日志仅管理员可查看。</div></div>
  <template v-else>
    <div v-if="error" class="alert">{{ error }}</div>
    <div class="panel">
      <div class="panel-h"><h3>操作记录</h3><button class="btn btn-ghost btn-sm" @click="load">刷新</button></div>
      <div v-if="loading" class="empty">加载中…</div>
      <div v-else-if="!audits.length" class="empty">暂无审计记录</div>
      <div v-else style="overflow:auto">
        <table>
          <thead><tr><th>时间</th><th>操作人</th><th>动作</th><th>资源</th><th>详情</th></tr></thead>
          <tbody>
            <tr v-for="item in audits" :key="item.id">
              <td class="sub">{{ formatDateTime(item.createdAt) }}</td>
              <td>{{ item.actor }}</td>
              <td><span class="chip chip-soft">{{ item.action }}</span></td>
              <td>{{ item.resource }}</td>
              <td class="mono">{{ item.details }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </template>
</template>
