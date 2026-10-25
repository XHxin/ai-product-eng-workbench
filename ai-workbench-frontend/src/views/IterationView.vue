<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { endpoints } from '../api/endpoints'
import type { Requirement } from '../api/types'
import { useAuthStore } from '../stores/auth'
import { useWorkbenchStore } from '../stores/workbench'
import { can, iterationStatusClass, iterationStatusLabel, readableError } from '../ui/format'
import { buildRefinementPrompt, buildTaskBreakdownPrompt } from '../ui/prompt'
import { notify } from '../ui/toast'
import StatusChip from '../components/StatusChip.vue'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import PromptPreviewModal from '../components/PromptPreviewModal.vue'
const emit = defineEmits<{ (event: 'open', id: number): void }>()
const store = useWorkbenchStore()
const auth = useAuthStore()
const selectedId = ref<number | null>(null)
const busy = ref(false)
const error = ref('')
const showCreate = ref(false)
const form = ref({ name: '', startDate: '', endDate: '' })
const promptVisible = ref(false)
const promptText = ref('')
const promptSource = ref('')
const promptTitle = ref('')
const removeTarget = ref<Requirement | null>(null)

const selected = computed(() => store.iterations.find(item => item.id === selectedId.value) ?? store.iterations[0])
const assigned = computed(() => store.requirements.filter(item => item.iterationId === selected.value?.id))
const pool = computed(() => store.requirements.filter(item => item.status === 'POOL' || item.status === 'EXTRACT'))
const canPromptIteration = computed(() => selected.value?.status === 'LOCKED' && can.manageRequirements(auth.roles))
const canPublishIteration = computed(() => can.manageRequirements(auth.roles) || can.release(auth.roles))
const promptDisabledReason = computed(() => {
  if (!selected.value) return ''
  if (selected.value.status === 'PLANNING') return '锁定范围后可生成提示词'
  if (selected.value.status !== 'LOCKED') return '当前迭代状态不可生成提示词'
  return can.manageRequirements(auth.roles) ? '' : '仅产品经理或管理员可生成提示词'
})

onMounted(async () => {
  await Promise.all([store.loadIterations(), store.loadRequirements()])
  selectedId.value = store.iterations[0]?.id ?? null
})

async function createIteration() {
  if (!form.value.name.trim() || !form.value.startDate || !form.value.endDate) { notify('请填写迭代名称与起止日期', 'error'); return }
  busy.value = true
  try {
    const created = await endpoints.createIteration(form.value)
    notify(`已创建迭代 ${created.name}`)
    showCreate.value = false
    form.value = { name: '', startDate: '', endDate: '' }
    await store.loadIterations(); selectedId.value = created.id
  } catch (reason) { error.value = readableError(reason); notify(error.value, 'error') }
  finally { busy.value = false }
}

async function lockIteration() {
  if (!selected.value) return
  busy.value = true
  try {
    await endpoints.lockIteration(selected.value.id)
    notify('迭代范围已锁定，未包含的需求不能再加入')
    await store.loadIterations()
  } catch (reason) { notify(readableError(reason), 'error') }
  finally { busy.value = false }
}

async function releaseIteration() {
  if (!selected.value) return
  busy.value = true
  try {
    await endpoints.releaseIteration(selected.value.id)
    notify(`迭代「${selected.value.name}」已发布`)
    await Promise.all([store.loadIterations(), store.loadRequirements(), store.loadSummary()])
  } catch (reason) { notify(readableError(reason), 'error') }
  finally { busy.value = false }
}

function openIterationPrompt(kind: 'refinement' | 'taskBreakdown') {
  if (!selected.value) return
  if (selected.value.status !== 'LOCKED') { notify(promptDisabledReason.value || '当前迭代状态不可生成提示词', 'error'); return }
  const target = { id: selected.value.id, name: selected.value.name }
  promptTitle.value = kind === 'refinement' ? '需求分析提示词预览' : '研发任务拆分提示词预览'
  promptSource.value = `迭代：${selected.value.name} · ${kind === 'refinement' ? '需求分析' : '研发任务拆分'}`
  promptText.value = kind === 'refinement'
    ? buildRefinementPrompt(target)
    : buildTaskBreakdownPrompt(target)
  promptVisible.value = true
}

async function confirmRemove() {
  if (!removeTarget.value || !selected.value) return
  busy.value = true
  try {
    await endpoints.removeRequirement(selected.value.id, removeTarget.value.id)
    notify(`已移除 ${removeTarget.value.requirementNumber}`)
    removeTarget.value = null
    await Promise.all([store.loadRequirements(), store.loadIterations(), store.loadSummary()])
  } catch (reason) { notify(readableError(reason), 'error') }
  finally { busy.value = false }
}

async function assign(requirementId: number) {
  if (!selected.value) return
  busy.value = true
  try {
    await endpoints.assignRequirement(selected.value.id, requirementId)
    notify('需求已纳入当前迭代')
    await Promise.all([store.loadRequirements(), store.loadIterations(), store.loadSummary()])
  } catch (reason) { notify(readableError(reason), 'error') }
  finally { busy.value = false }
}
</script>
<template>
  <div class="view-head">
    <div><h1>迭代规划</h1><div class="desc">创建迭代、纳入需求并锁定范围；锁定后方可生成正式 PRD</div></div>
    <button class="btn btn-primary btn-sm" :disabled="!can.manageRequirements(auth.roles)" @click="showCreate = true">＋ 创建迭代</button>
  </div>
  <div v-if="error" class="alert">{{ error }}</div>
  <div class="filters">
    <span class="sub">选择迭代：</span>
    <div class="seg">
      <button v-for="iteration in store.iterations" :key="iteration.id" :class="{ on: selected?.id === iteration.id }" @click="selectedId = iteration.id">
        {{ iteration.name }} · {{ iterationStatusLabel(iteration.status) }}
      </button>
    </div>
  </div>
  <div v-if="!store.iterations.length" class="panel"><div class="empty">尚未创建迭代，请先创建。</div></div>
  <div v-else class="split-wide">
    <div class="panel">
      <div class="panel-h">
        <h3>{{ selected?.name }} <span :class="['chip', iterationStatusClass(selected?.status ?? '')]" style="margin-left:8px">{{ iterationStatusLabel(selected?.status ?? '') }}</span></h3>
        <div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap;justify-content:flex-end">
          <button
            v-if="selected"
            class="btn btn-ghost btn-sm"
            :disabled="!canPromptIteration"
            :title="promptDisabledReason"
            @click="openIterationPrompt('refinement')"
          >生成需求分析提示词</button>
          <button
            v-if="selected"
            class="btn btn-ghost btn-sm"
            :disabled="!canPromptIteration"
            :title="promptDisabledReason"
            @click="openIterationPrompt('taskBreakdown')"
          >生成研发任务拆分提示词</button>
          <button
            v-if="selected?.status === 'READY_FOR_RELEASE'"
            class="btn btn-primary btn-sm"
            :disabled="busy || !canPublishIteration"
            :title="canPublishIteration ? '' : '仅管理员、产品经理或发布负责人可发布'"
            @click="releaseIteration"
          >发布</button>
          <button v-if="selected?.status === 'PLANNING'" class="btn btn-ghost btn-sm" :disabled="busy || !can.manageRequirements(auth.roles)" @click="lockIteration">锁定范围</button>
        </div>
      </div>
      <div class="panel-b">
        <div class="sub" style="margin-bottom:12px">{{ selected?.startDate }} 至 {{ selected?.endDate }} · {{ assigned.length }} 个需求</div>
        <div v-if="!assigned.length" class="empty">本期迭代暂无需求，请从右侧需求池纳入。</div>
        <div v-for="item in assigned" :key="item.id" class="ticket clickable" @click="emit('open', item.id)">
          <div class="tt">{{ item.title }} <span class="req-id">{{ item.requirementNumber }}</span></div>
          <div class="mf">
            <StatusChip :status="item.status" />
            <div style="display:flex;gap:8px;align-items:center">
              <span class="sub">#{{ item.id }}</span>
              <button
                v-if="selected?.status === 'PLANNING'"
                class="btn btn-ghost btn-sm"
                :disabled="busy || !can.manageRequirements(auth.roles)"
                :title="can.manageRequirements(auth.roles) ? '' : '仅产品经理或管理员可移除'"
                @click.stop="removeTarget = item"
              >移除</button>
            </div>
          </div>
        </div>
      </div>
    </div>
    <div class="panel">
      <div class="panel-h"><h3>未纳入迭代</h3><span class="sub">仅未锁定迭代可加入</span></div>
      <div class="panel-b">
        <div v-if="selected && selected.status !== 'PLANNING'" class="warn-box">
          {{ selected.status === 'LOCKED' ? '迭代范围已锁定。新增需求请创建新迭代。' : '迭代已进入发布阶段，不能再纳入需求。' }}
        </div>
        <div v-if="!pool.length" class="empty">需求池暂无可纳入需求</div>
        <div v-for="item in pool" :key="item.id" class="ticket">
          <div class="tt">{{ item.title }} <span class="req-id">{{ item.requirementNumber }}</span></div>
          <div class="mf">
            <StatusChip :status="item.status" />
            <button class="btn btn-ghost btn-sm" :disabled="selected?.status !== 'PLANNING' || busy || !can.manageRequirements(auth.roles)" @click="assign(item.id)">纳入本期</button>
          </div>
        </div>
      </div>
    </div>
  </div>

  <div v-if="showCreate" class="modal-mask">
    <div class="modal">
      <div class="modal-h"><h2>创建迭代</h2><button class="x-btn" @click="showCreate = false">✕</button></div>
      <div class="modal-b">
        <div class="field"><label>迭代名称</label><input v-model="form.name" class="in" maxlength="200" placeholder="例如：Q4迭代二" /></div>
        <div class="two-col">
          <div class="field"><label>开始日期</label><input v-model="form.startDate" class="in" type="date" /></div>
          <div class="field"><label>结束日期</label><input v-model="form.endDate" class="in" type="date" /></div>
        </div>
        <div class="hint">创建后处于规划中，纳入需求并锁定范围后才能生成正式 PRD。</div>
      </div>
      <div class="modal-f"><button class="btn btn-ghost" @click="showCreate = false">取消</button><button class="btn btn-primary" :disabled="busy" @click="createIteration">创建</button></div>
    </div>
  </div>

  <ConfirmDialog
    :visible="Boolean(removeTarget)"
    title="移出迭代"
    :message="removeTarget ? `确定将 ${removeTarget.requirementNumber}「${removeTarget.title}」移出当前迭代吗？需求会回到需求池。` : ''"
    confirm-text="确认移除"
    :busy="busy"
    @update:visible="value => { if (!value) removeTarget = null }"
    @confirm="confirmRemove"
  />

  <PromptPreviewModal
    v-model:visible="promptVisible"
    :title="promptTitle"
    :source="promptSource"
    :prompt="promptText"
  />
</template>
