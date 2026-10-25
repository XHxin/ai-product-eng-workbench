<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { endpoints } from '../api/endpoints'
import type { Requirement, WorkflowRecord } from '../api/types'
import { formatDateTime, payloadText, readableError, sourceLabel } from '../ui/format'
import StatusChip from './StatusChip.vue'
import TypeChip from './TypeChip.vue'
import PriorityChip from './PriorityChip.vue'

const props = defineProps<{ requirementId: number | null }>()
const emit = defineEmits<{ (event: 'close'): void }>()
const requirement = ref<Requirement | null>(null)
const prds = ref<WorkflowRecord[]>([])
const confirmations = ref<WorkflowRecord[]>([])
const tasks = ref<WorkflowRecord[]>([])
const error = ref('')
const loading = ref(false)

async function load() {
  if (!props.requirementId) return
  loading.value = true
  error.value = ''
  try {
    requirement.value = await endpoints.requirement(props.requirementId)
    const [prdPage, confirmationPage, taskPage] = await Promise.all([
      endpoints.records(props.requirementId, 'PRD'),
      endpoints.records(props.requirementId, 'CONFIRMATION'),
      endpoints.records(props.requirementId, 'TASK'),
    ])
    prds.value = prdPage.content
    confirmations.value = confirmationPage.content
    tasks.value = taskPage.content
  } catch (reason) {
    error.value = readableError(reason)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
<template>
  <div class="mask" @click="emit('close')" />
  <aside class="drawer">
    <div class="drawer-h">
      <div>
        <h2>{{ requirement?.title || '需求详情' }}</h2>
        <div class="req-id">{{ requirement?.requirementNumber || '加载中…' }}</div>
      </div>
      <button class="x-btn" @click="emit('close')">✕</button>
    </div>
    <div class="drawer-b">
      <div v-if="error" class="alert">{{ error }}</div>
      <div v-if="loading" class="empty">加载中…</div>
      <template v-else-if="requirement">
        <div class="meta">
          <span class="k">状态</span><span class="v"><StatusChip :status="requirement.status" /></span>
          <span class="k">类型</span><span class="v"><TypeChip :type="requirement.requirementType" /></span>
          <span class="k">优先级</span><span class="v"><PriorityChip :priority="requirement.priority" /></span>
          <span class="k">来源</span><span class="v">{{ sourceLabel(requirement.sourceType) }} · {{ requirement.sourceRef }}</span>
          <span class="k">迭代</span><span class="v">{{ requirement.iterationId ? `#${requirement.iterationId}` : '未规划' }}</span>
          <span class="k">创建时间</span><span class="v">{{ formatDateTime(requirement.createdAt) }}</span>
        </div>

        <div class="sec-t">原始诉求</div>
        <div class="doc">{{ requirement.rawText }}</div>

        <div class="sec-t">标准化内容</div>
        <div class="doc">{{ requirement.normalizedText || '尚未确认标准化内容' }}</div>

        <div class="sec-t">PRD 版本（{{ prds.length }}）</div>
        <div v-if="!prds.length" class="empty">暂无 PRD 版本</div>
        <div v-for="prd in prds" :key="prd.id" class="ticket">
          <div class="tt">版本 V{{ prd.versionNumber }} <span class="req-id">#{{ prd.id }}</span></div>
          <div class="mf"><span class="sub">{{ prd.status }} · {{ formatDateTime(prd.createdAt) }} · {{ prd.actor }}</span></div>
          <div class="mf"><span class="sub">{{ payloadText(prd.payload, 'templateVersion') }}</span></div>
        </div>

        <div class="sec-t">确认问题（{{ confirmations.length }}）</div>
        <div v-if="!confirmations.length" class="empty">暂无确认记录</div>
        <div v-for="item in confirmations" :key="item.id" class="ticket">
          <div class="tt">{{ payloadText(item.payload, 'question') }}</div>
          <div class="mf"><span class="sub">{{ payloadText(item.payload, 'channel') }} · {{ item.status }}</span></div>
        </div>

        <div class="sec-t">研发任务（{{ tasks.length }}）</div>
        <div v-if="!tasks.length" class="empty">暂无研发任务</div>
        <div v-for="task in tasks" :key="task.id" class="ticket">
          <div class="tt">{{ payloadText(task.payload, 'title') }}</div>
          <div class="mf"><span class="sub">{{ task.status }} · 验收：{{ payloadText(task.payload, 'acceptance') }}</span></div>
        </div>
      </template>
    </div>
    <div class="drawer-f">
      <button class="btn btn-primary" @click="emit('close')">关闭</button>
    </div>
  </aside>
</template>
