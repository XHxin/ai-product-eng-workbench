<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { endpoints } from '../api/endpoints'
import type { IntegrationConfig } from '../api/types'
import { useAuthStore } from '../stores/auth'
import { can, formatDateTime, readableError } from '../ui/format'
import { notify } from '../ui/toast'

const auth = useAuthStore()
const integrations = ref<IntegrationConfig[]>([])
const error = ref('')
const busy = ref(false)
const form = ref({ provider: 'OPENAI', endpoint: '', secret: '', model: '' })
const providers = ['OPENAI', 'DINGTALK', 'TINGJI', 'SHEET', 'TEAMBITION']

onMounted(load)

async function load() {
  error.value = ''
  try { integrations.value = await endpoints.integrations() }
  catch (reason) { error.value = readableError(reason) }
}

async function save() {
  if (!form.value.endpoint.trim()) { notify('请填写 HTTPS 接口地址', 'error'); return }
  busy.value = true
  try {
    await endpoints.saveIntegration(form.value.provider, {
      endpoint: form.value.endpoint.trim(), secret: form.value.secret, model: form.value.model,
    })
    notify('集成配置已加密保存，密钥不会回显')
    form.value.secret = ''
    await load()
  } catch (reason) { error.value = readableError(reason); notify(error.value, 'error') }
  finally { busy.value = false }
}
</script>
<template>
  <div class="view-head"><div><h1>集成配置</h1><div class="desc">外部凭证仅由管理员配置，加密存储且不回显；未配置时使用人工降级模式</div></div></div>
  <div v-if="error" class="alert">{{ error }}</div>
  <div class="notice">MVP 中钉钉、AI听记、指定表格与 Teambition 采用人工导入/导出模式。模型接口可配置 OpenAI 兼容端点，调用失败会明确提示，不会伪造 AI 结果。</div>
  <div class="split-wide">
    <div class="panel">
      <div class="panel-h"><h3>已配置连接</h3><span class="sub">{{ integrations.length }} 项</span></div>
      <div class="panel-b">
        <div v-if="!integrations.length" class="empty">尚未配置任何外部连接</div>
        <div v-for="item in integrations" :key="item.provider" class="ticket">
          <div class="tt">{{ item.provider }} <span :class="['chip', item.configured ? 'st-done' : 'chip-soft']">{{ item.configured ? '已配置' : '未配置' }}</span></div>
          <div class="sub mono" style="margin-top:6px">{{ item.endpoint }}</div>
          <div class="sub">模型：{{ item.model || '—' }} · 密钥：{{ item.secret }}</div>
        </div>
      </div>
    </div>
    <div class="panel">
      <div class="panel-h"><h3>新增或更新连接</h3><span class="sub">仅管理员</span></div>
      <div class="panel-b">
        <div class="field"><label>集成类型</label><select v-model="form.provider" class="in"><option v-for="item in providers" :key="item">{{ item }}</option></select></div>
        <div class="field"><label>HTTPS 接口地址</label><input v-model="form.endpoint" class="in" maxlength="1000" placeholder="https://api.example.com/v1/chat/completions" /></div>
        <div class="field"><label>访问密钥（留空则不更新）</label><input v-model="form.secret" class="in" type="password" maxlength="5000" autocomplete="new-password" /></div>
        <div class="field"><label>模型名称</label><input v-model="form.model" class="in" maxlength="200" placeholder="例如 gpt-4o-mini" /></div>
        <div class="hint">系统拒绝 HTTP、内网、回环地址和含凭证的 URL，防止服务端请求伪造。</div>
        <button class="btn btn-primary" style="margin-top:10px" :disabled="busy || !can.admin(auth.roles)" @click="save">{{ busy ? '保存中…' : '加密保存' }}</button>
      </div>
    </div>
  </div>
</template>
