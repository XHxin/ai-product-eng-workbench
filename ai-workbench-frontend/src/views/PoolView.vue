<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { endpoints } from '../api/endpoints'
import type { Requirement } from '../api/types'
import { useAuthStore } from '../stores/auth'
import { useWorkbenchStore } from '../stores/workbench'
import { can, formatDateTime, readableError, sourceLabel } from '../ui/format'
import { notify } from '../ui/toast'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import PriorityChip from '../components/PriorityChip.vue'
import StatusChip from '../components/StatusChip.vue'
import TypeChip from '../components/TypeChip.vue'

const emit = defineEmits<{ (event: 'open', id: number): void; (event: 'navigate', page: string): void }>()
const store = useWorkbenchStore()
const auth = useAuthStore()
const filter = ref('ALL')
const busy = ref(false)
const deleteTarget = ref<Requirement | null>(null)
const filters = [
  { key: 'ALL', label: '全部' }, { key: 'EXTRACT', label: '待提取' }, { key: 'POOL', label: '需求池' },
  { key: 'ITERATION', label: '已纳入迭代' }, { key: 'CONFIRMATION', label: '待确认' },
  { key: 'REVIEW', label: '待评审' }, { key: 'DEVELOPMENT', label: '开发中' }, { key: 'COMPLETED', label: '已完成' },
]
const rows = computed(() => {
  const source = store.query ? store.filteredRequirements : store.requirements
  return filter.value === 'ALL' ? source : source.filter(item => item.status === filter.value)
})
const countOf = (status: string) => store.requirements.filter(item => item.status === status).length
const canDelete = (item: Requirement) => !item.iterationId && ['EXTRACT', 'POOL'].includes(item.status)

async function confirmDelete() {
  if (!deleteTarget.value) return
  busy.value = true
  try {
    await endpoints.deleteRequirement(deleteTarget.value.id)
    notify(`已删除 ${deleteTarget.value.requirementNumber}`)
    deleteTarget.value = null
    await Promise.all([store.loadRequirements(), store.loadSummary()])
  } catch (reason) { notify(readableError(reason), 'error') }
  finally { busy.value = false }
}

onMounted(() => store.loadRequirements())
</script>
<template>
  <div class="view-head">
    <div><h1>需求池</h1><div class="desc">查看全部需求、状态与所属迭代</div></div>
    <div class="head-actions">
      <input v-model="store.query" class="in pool-search" maxlength="200" placeholder="搜索需求编号或标题" />
      <button class="btn btn-ghost btn-sm" @click="store.loadRequirements()">刷新</button>
      <button class="btn btn-primary btn-sm" @click="emit('navigate', 'intake')">＋ 录入需求</button>
    </div>
  </div>
  <div v-if="store.error" class="alert">{{ store.error }}</div>
  <div class="filters">
    <div class="seg">
      <button v-for="item in filters" :key="item.key" :class="{ on: filter === item.key }" @click="filter = item.key">
        {{ item.label }} {{ item.key === 'ALL' ? store.requirements.length : countOf(item.key) }}
      </button>
    </div>
    <span class="sub">共 {{ rows.length }} 条{{ store.query ? `（搜索：${store.query}）` : '' }}</span>
  </div>
  <div class="panel">
    <div style="overflow:auto">
      <table>
        <thead><tr><th>需求</th><th>类型</th><th>优先级</th><th>状态</th><th>来源</th><th>所属迭代</th><th>创建</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-if="!rows.length"><td colspan="8" class="empty">无匹配需求</td></tr>
          <tr v-for="item in rows" :key="item.id" class="clickable" @click="emit('open', item.id)">
            <td><div class="req-title">{{ item.title }}</div><div class="req-id">{{ item.requirementNumber }}</div></td>
            <td><TypeChip :type="item.requirementType" /></td>
            <td><PriorityChip :priority="item.priority" /></td>
            <td><StatusChip :status="item.status" /></td>
            <td class="sub">{{ sourceLabel(item.sourceType) }}</td>
            <td><span v-if="item.iterationId" class="chip chip-soft">#{{ item.iterationId }}</span><span v-else class="sub">—</span></td>
            <td class="sub">{{ formatDateTime(item.createdAt) }}</td>
            <td>
              <button
                v-if="canDelete(item)"
                class="btn btn-ghost btn-sm"
                :disabled="busy || !can.manageRequirements(auth.roles)"
                :title="can.manageRequirements(auth.roles) ? '' : '仅产品经理或管理员可删除'"
                @click.stop="deleteTarget = item"
              >删除</button>
              <span v-else class="sub">—</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>

  <ConfirmDialog
    :visible="Boolean(deleteTarget)"
    title="删除需求"
    :message="deleteTarget ? `确定删除 ${deleteTarget.requirementNumber}「${deleteTarget.title}」吗？删除后不可恢复。` : ''"
    confirm-text="确认删除"
    :busy="busy"
    @update:visible="value => { if (!value) deleteTarget = null }"
    @confirm="confirmDelete"
  />
</template>
