<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { endpoints } from '../api/endpoints'
import type { WorkflowRecord } from '../api/types'
import { useAuthStore } from '../stores/auth'
import { useWorkbenchStore } from '../stores/workbench'
import { can, formatDateTime, payloadText, readableError } from '../ui/format'
import { notify } from '../ui/toast'
import StatusChip from '../components/StatusChip.vue'

const emit = defineEmits<{ (event: 'open', id: number): void }>()
const store = useWorkbenchStore()
const auth = useAuthStore()
const tasks = ref<WorkflowRecord[]>([])
const busy = ref(false)
const error = ref('')
const showCreate = ref(false)
const createForm = ref({ requirementId: 0, title: '', scope: '', acceptance: '', dependencies: '' })
const developmentTarget = ref<WorkflowRecord | null>(null)
const developmentForm = ref({ confirmedHours: 1, technicalPlan: '', subtasks: '', testPoints: '' })
const developmentRecord = ref<WorkflowRecord | null>(null)
const detailTarget = ref<WorkflowRecord | null>(null)
const selectedIterationId = ref<number | null>(null)
const selectedTaskIds = ref<number[]>([])

const readyRequirements = computed(() => store.requirements.filter(item => ['PRD_CONFIRMED', 'DEVELOPMENT'].includes(item.status)))
const iterationTasks = computed(() => tasks.value.filter(item => item.iterationId === selectedIterationId.value))
const taskStatusLabel: Record<string, string> = { DRAFT: '待确认', CONFIRMED: '已确认', IN_PROGRESS: '开发中', DONE: '已完成' }

onMounted(async () => {
  await Promise.all([store.loadRequirements(), store.loadIterations(), loadTasks()])
  const lockedIteration = store.iterations.find(item => item.status === 'LOCKED')
  selectedIterationId.value = lockedIteration?.id ?? store.iterations[0]?.id ?? null
})

watch(selectedIterationId, () => { selectedTaskIds.value = [] })

async function loadTasks() {
  try { tasks.value = (await endpoints.recordsByCategory('TASK')).content }
  catch (reason) { error.value = readableError(reason) }
}

function openCreate() {
  createForm.value = { requirementId: readyRequirements.value[0]?.id ?? 0, title: '', scope: '', acceptance: '', dependencies: '' }
  showCreate.value = true
}

async function createTask() {
  if (!createForm.value.requirementId || !createForm.value.title.trim() || !createForm.value.scope.trim() || !createForm.value.acceptance.trim()) {
    notify('需求、标题、范围与验收标准必填', 'error'); return
  }
  busy.value = true
  try {
    await endpoints.createTask(createForm.value.requirementId, {
      title: createForm.value.title, scope: createForm.value.scope,
      acceptance: createForm.value.acceptance, dependencies: createForm.value.dependencies,
    })
    notify('任务已创建，待产品经理确认下发')
    showCreate.value = false
    await loadTasks()
  } catch (reason) { error.value = readableError(reason); notify(error.value, 'error') }
  finally { busy.value = false }
}

async function confirmTask(task: WorkflowRecord) {
  busy.value = true
  try {
    await endpoints.confirmTask(task.id)
    notify('任务已确认，需求进入开发中')
    await Promise.all([loadTasks(), store.loadRequirements(), store.loadSummary()])
  } catch (reason) { notify(readableError(reason), 'error') }
  finally { busy.value = false }
}

function openDevelopment(task: WorkflowRecord) {
  developmentTarget.value = task
  developmentForm.value = { confirmedHours: 1, technicalPlan: '', subtasks: '', testPoints: '' }
}

async function submitDevelopment() {
  if (!developmentTarget.value) return
  if (!developmentForm.value.technicalPlan.trim() || !developmentForm.value.subtasks.trim() || !developmentForm.value.testPoints.trim()) {
    notify('技术方案、子任务与测试点必填', 'error'); return
  }
  busy.value = true
  try {
    await endpoints.developTask(developmentTarget.value.id, {
      confirmedHours: Number(developmentForm.value.confirmedHours),
      technicalPlan: developmentForm.value.technicalPlan,
      subtasks: developmentForm.value.subtasks.split(/[,，\n]/).map(item => item.trim()).filter(Boolean),
      testPoints: developmentForm.value.testPoints.split(/[,，\n]/).map(item => item.trim()).filter(Boolean),
    })
    notify('开发信息已保存，任务进入进行中')
    developmentTarget.value = null
    await loadTasks()
  } catch (reason) { notify(readableError(reason), 'error') }
  finally { busy.value = false }
}

async function updateProgress(task: WorkflowRecord, status: 'IN_PROGRESS' | 'DONE') {
  busy.value = true
  try {
    await endpoints.progressTask(task.id, status)
    notify(status === 'DONE' ? '任务已完成' : '任务进行中')
    await Promise.all([loadTasks(), store.loadSummary()])
  } catch (reason) { notify(readableError(reason), 'error') }
  finally { busy.value = false }
}

async function batchCompleteTasks() {
  if (!selectedTaskIds.value.length) return
  const selectedTasks = iterationTasks.value.filter(task => selectedTaskIds.value.includes(task.id))
  const completableTasks = selectedTasks.filter(task => task.status === 'IN_PROGRESS')
  if (!completableTasks.length) {
    notify('所选任务均未进入开发中，请先补充开发信息', 'error')
    return
  }
  busy.value = true
  let completedCount = 0
  try {
    for (const task of completableTasks) {
      await endpoints.progressTask(task.id, 'DONE')
      completedCount += 1
    }
    const skippedCount = selectedTasks.length - completableTasks.length
    notify(`已完成 ${completedCount} 个任务${skippedCount ? `，跳过 ${skippedCount} 个未进入开发中的任务` : ''}`)
    selectedTaskIds.value = []
    await Promise.all([loadTasks(), store.loadSummary()])
  } catch (reason) {
    notify(`已完成 ${completedCount} 个任务，其余失败：${readableError(reason)}`, 'error')
    await Promise.all([loadTasks(), store.loadSummary()])
  } finally { busy.value = false }
}

async function showDetail(task: WorkflowRecord) {
  detailTarget.value = task
  developmentRecord.value = null
  if (!task.requirementId) return
  try {
    const page = await endpoints.records(task.requirementId, 'DEVELOPMENT')
    developmentRecord.value = page.content[0] ?? null
  } catch (reason) { notify(readableError(reason), 'error') }
}
</script>
<template>
  <div class="view-head">
    <div><h1>研发任务</h1><div class="desc">从最终 PRD 生成任务，人工确认后由开发补充工时、技术方案与测试点</div></div>
    <div style="display:flex;gap:8px;flex-wrap:wrap">
      <button
        class="btn btn-ok btn-sm"
        :disabled="busy || !selectedTaskIds.length || !can.develop(auth.roles)"
        :title="can.develop(auth.roles) ? '仅开发中任务可批量完成' : '仅管理员或开发可完成任务'"
        @click="batchCompleteTasks"
      >批量完成任务{{ selectedTaskIds.length ? `（${selectedTaskIds.length}）` : '' }}</button>
      <button class="btn btn-primary btn-sm" :disabled="!can.manageRequirements(auth.roles) || !readyRequirements.length" @click="openCreate">＋ 新建任务</button>
    </div>
  </div>
  <div v-if="error" class="alert">{{ error }}</div>
  <div v-if="store.iterations.length" class="filters">
    <span class="sub">选择迭代：</span>
    <div class="seg">
      <button v-for="iteration in store.iterations" :key="iteration.id" :class="{ on: selectedIterationId === iteration.id }" @click="selectedIterationId = iteration.id">
        {{ iteration.name }}
      </button>
    </div>
  </div>
  <div v-if="!readyRequirements.length" class="notice">暂无可生成任务的已确认需求。请先完成 PRD 评审。</div>
  <div v-if="!store.iterations.length" class="panel"><div class="empty">暂无迭代，请先在迭代规划中创建迭代。</div></div>
  <div v-else-if="!iterationTasks.length" class="panel"><div class="empty">该迭代暂无研发任务</div></div>
  <div v-for="task in iterationTasks" :key="task.id" class="ticket">
    <div class="tt" style="display:flex;align-items:center;gap:8px;flex-wrap:wrap">
      <input v-model="selectedTaskIds" type="checkbox" :value="task.id" style="width:15px;height:15px;flex:none" />
      <span>{{ payloadText(task.payload, 'title') }}</span><span class="req-id">#{{ task.id }}</span> <span class="chip chip-soft">{{ taskStatusLabel[task.status] || task.status }}</span>
    </div>
    <div class="sub" style="margin-top:6px">范围：{{ payloadText(task.payload, 'scope') }}</div>
    <div class="sub">验收：{{ payloadText(task.payload, 'acceptance') }}</div>
    <div class="sub">依赖：{{ payloadText(task.payload, 'dependencies') }} · 创建：{{ formatDateTime(task.createdAt) }}</div>
    <div class="mf">
      <div style="display:flex;gap:8px;flex-wrap:wrap">
        <button class="btn btn-ghost btn-sm" @click="showDetail(task)">开发信息</button>
        <button class="btn btn-ghost btn-sm" @click="task.requirementId && emit('open', task.requirementId)">查看需求</button>
      </div>
      <div style="display:flex;gap:8px;flex-wrap:wrap">
        <button v-if="task.status === 'DRAFT'" class="btn btn-ok btn-sm" :disabled="busy || !can.manageRequirements(auth.roles)" @click="confirmTask(task)">确认任务</button>
        <button v-if="['CONFIRMED', 'IN_PROGRESS'].includes(task.status)" class="btn btn-ghost btn-sm" :disabled="busy || !can.develop(auth.roles)" @click="openDevelopment(task)">补充开发信息</button>
        <button v-if="task.status === 'IN_PROGRESS'" class="btn btn-primary btn-sm" :disabled="busy || !can.develop(auth.roles)" @click="updateProgress(task, 'DONE')">标记完成</button>
      </div>
    </div>
  </div>

  <div v-if="showCreate" class="modal-mask">
    <div class="modal">
      <div class="modal-h"><h2>新建研发任务</h2><button class="x-btn" @click="showCreate = false">✕</button></div>
      <div class="modal-b">
        <div class="field"><label>关联已确认需求</label><select v-model.number="createForm.requirementId" class="in">
          <option v-for="item in readyRequirements" :key="item.id" :value="item.id">{{ item.requirementNumber }} {{ item.title }}</option>
        </select></div>
        <div class="field"><label>任务标题</label><input v-model="createForm.title" class="in" maxlength="200" /></div>
        <div class="field"><label>范围</label><textarea v-model="createForm.scope" class="in" rows="2" maxlength="10000" /></div>
        <div class="field"><label>验收标准</label><textarea v-model="createForm.acceptance" class="in" rows="2" maxlength="10000" /></div>
        <div class="field"><label>依赖</label><input v-model="createForm.dependencies" class="in" maxlength="2000" /></div>
      </div>
      <div class="modal-f"><button class="btn btn-ghost" @click="showCreate = false">取消</button><button class="btn btn-primary" :disabled="busy" @click="createTask">创建任务</button></div>
    </div>
  </div>

  <div v-if="developmentTarget" class="modal-mask">
    <div class="modal">
      <div class="modal-h"><h2>补充开发信息</h2><button class="x-btn" @click="developmentTarget = null">✕</button></div>
      <div class="modal-b">
        <div class="field"><label>确认工时（小时）</label><input v-model.number="developmentForm.confirmedHours" class="in" type="number" min="0.1" max="10000" step="0.5" /></div>
        <div class="field"><label>技术方案</label><textarea v-model="developmentForm.technicalPlan" class="in" rows="4" maxlength="10000" /></div>
        <div class="field"><label>技术子任务（逗号或换行分隔）</label><textarea v-model="developmentForm.subtasks" class="in" rows="3" /></div>
        <div class="field"><label>测试点（逗号或换行分隔）</label><textarea v-model="developmentForm.testPoints" class="in" rows="3" /></div>
      </div>
      <div class="modal-f"><button class="btn btn-ghost" @click="developmentTarget = null">取消</button><button class="btn btn-primary" :disabled="busy" @click="submitDevelopment">保存</button></div>
    </div>
  </div>

  <div v-if="detailTarget" class="modal-mask">
    <div class="modal">
      <div class="modal-h"><h2>开发信息</h2><button class="x-btn" @click="detailTarget = null">✕</button></div>
      <div class="modal-b">
        <div v-if="!developmentRecord" class="empty">开发尚未补充工时与技术方案</div>
        <template v-else>
          <div class="meta">
            <span class="k">确认工时</span><span class="v">{{ payloadText(developmentRecord.payload, 'confirmedHours') }} 小时</span>
            <span class="k">技术方案</span><span class="v">{{ payloadText(developmentRecord.payload, 'technicalPlan') }}</span>
            <span class="k">子任务</span><span class="v">{{ (developmentRecord.payload.subtasks as string[] | undefined)?.join('、') || '—' }}</span>
            <span class="k">测试点</span><span class="v">{{ (developmentRecord.payload.testPoints as string[] | undefined)?.join('、') || '—' }}</span>
          </div>
        </template>
      </div>
      <div class="modal-f"><button class="btn btn-primary" @click="detailTarget = null">关闭</button></div>
    </div>
  </div>
</template>
