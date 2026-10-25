<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { endpoints } from '../api/endpoints'
import type { Requirement, WorkflowRecord } from '../api/types'
import { useAuthStore } from '../stores/auth'
import { useWorkbenchStore } from '../stores/workbench'
import { can, formatDateTime, payloadText, readableError, statusLabel } from '../ui/format'
import { renderMarkdown } from '../ui/markdown'
import { notify } from '../ui/toast'
import StatusChip from '../components/StatusChip.vue'

const emit = defineEmits<{ (event: 'open', id: number): void }>()
const store = useWorkbenchStore()
const auth = useAuthStore()
const selectedIterationId = ref<number | null>(null)
const selectedRequirementId = ref<number | null>(null)
const prds = ref<WorkflowRecord[]>([])
const confirmations = ref<WorkflowRecord[]>([])
const reviews = ref<WorkflowRecord[]>([])
const busy = ref(false)
const error = ref('')
const prdTab = ref<'doc' | 'html'>('doc')
const prdForm = ref({ content: '', htmlContent: '' })
const questionForm = ref({ question: '', channel: 'DINGTALK', sourceRef: '', rawReply: '' })
const conclusionForm = ref({ confirmationId: 0, conclusion: '' })
const reviewForm = ref({ decision: 'APPROVED', participants: '', meetingAt: '', conclusion: '' })
const activePrd = ref<WorkflowRecord | null>(null)
const activePrdTab = ref<'doc' | 'html'>('doc')
const activePrdHtml = computed(() => {
  const value = activePrd.value?.payload?.htmlContent
  return typeof value === 'string' ? value : ''
})
const aiConfigured = ref(false)
const isFullscreen = ref(false)

const iterationRequirements = computed(() => store.requirements.filter(item => item.iterationId === selectedIterationId.value))
const selectedRequirement = computed<Requirement | undefined>(() => store.requirements.find(item => item.id === selectedRequirementId.value))
const selectedIteration = computed(() => store.iterations.find(item => item.id === selectedIterationId.value))
const openConfirmations = computed(() => confirmations.value.filter(item => item.status !== 'CONFIRMED'))
const latestPrd = computed(() => prds.value[0] ?? null)

onMounted(async () => {
  document.addEventListener('fullscreenchange', syncFullscreen)
  await Promise.all([store.loadIterations(), store.loadRequirements()])
  const locked = store.iterations.find(item => item.status === 'LOCKED')
  selectedIterationId.value = locked?.id ?? store.iterations[0]?.id ?? null
  try {
    const configs = await endpoints.integrations()
    aiConfigured.value = configs.some(item => item.provider === 'OPENAI' && item.configured)
  } catch { aiConfigured.value = false }
})

onUnmounted(() => document.removeEventListener('fullscreenchange', syncFullscreen))

function syncFullscreen() {
  isFullscreen.value = Boolean(document.fullscreenElement)
}

function toggleFullscreen(event: MouseEvent) {
  const box = (event.currentTarget as HTMLElement).closest('.iframe-box') as HTMLElement | null
  if (!box) return
  if (document.fullscreenElement) {
    document.exitFullscreen().catch(() => notify('退出全屏失败', 'error'))
    return
  }
  box.requestFullscreen().catch(() => notify('当前浏览器不支持全屏展示', 'error'))
}

watch(selectedIterationId, () => {
  selectedRequirementId.value = iterationRequirements.value[0]?.id ?? null
  activePrd.value = null
})

watch(selectedRequirementId, async () => {
  if (!selectedRequirementId.value) return
  activePrdTab.value = 'doc'
  await loadRecords()
  const latest = prds.value[0]
  if (latest) {
    const payload = latest.payload ?? {}
    prdForm.value = {
      content: typeof payload.content === 'string' ? payload.content : '',
      htmlContent: typeof payload.htmlContent === 'string' ? payload.htmlContent : '',
    }
    return
  }
  const requirement = selectedRequirement.value
  if (requirement) {
    prdForm.value = {
      content: `# ${requirement.title}\n\n## 背景\n${requirement.normalizedText}\n\n## 目标\n待细化\n\n## 验收标准\n待确认`,
      htmlContent: `<div style="font-family:sans-serif;padding:16px"><h2>${requirement.title}</h2><p>原型占位，请在需求分析中补充业务原型。</p></div>`,
    }
  }
})

function openPrdPanel(tab: 'doc' | 'html') {
  activePrd.value = latestPrd.value
  activePrdTab.value = tab
  nextTick(() => document.getElementById('prd-panel')?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
}

function scrollToReview() {
  nextTick(() => document.getElementById('review-section')?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
}

async function loadRecords() {
  if (!selectedRequirementId.value) return
  try {
    const [prdPage, confirmationPage, reviewPage] = await Promise.all([
      endpoints.records(selectedRequirementId.value, 'PRD'),
      endpoints.records(selectedRequirementId.value, 'CONFIRMATION'),
      endpoints.records(selectedRequirementId.value, 'REVIEW'),
    ])
    prds.value = prdPage.content
    confirmations.value = confirmationPage.content
    reviews.value = reviewPage.content
    activePrd.value = prds.value[0] ?? null
  } catch (reason) { notify(readableError(reason), 'error') }
}

async function createPrd() {
  if (!selectedRequirementId.value) return
  if (!prdForm.value.content.trim() || !prdForm.value.htmlContent.trim()) { notify('需求文档与原型 HTML 均不能为空', 'error'); return }
  busy.value = true
  try {
    await endpoints.createPrd(selectedRequirementId.value, {
      content: prdForm.value.content, htmlContent: prdForm.value.htmlContent,
    })
    notify('PRD 新版本已保存，状态进入待确认')
    await Promise.all([loadRecords(), store.loadRequirements()])
  } catch (reason) { error.value = readableError(reason); notify(error.value, 'error') }
  finally { busy.value = false }
}

async function generatePrdDraft() {
  if (!selectedRequirement.value) return
  try {
    const result = await endpoints.generate(`请为以下需求生成Markdown PRD，包含背景、目标、范围、验收标准：\n${selectedRequirement.value.normalizedText}`)
    prdForm.value.content = result.content
    notify('AI 已生成 PRD 草稿，请产品经理审阅后保存')
  } catch (reason) { notify(`AI 不可用：${readableError(reason)}，可人工填写`, 'error') }
}

async function addConfirmation() {
  if (!selectedRequirementId.value) return
  if (!questionForm.value.question.trim() || !questionForm.value.rawReply.trim()) { notify('问题与来源回复必填', 'error'); return }
  busy.value = true
  try {
    await endpoints.createConfirmation(selectedRequirementId.value, {
      question: questionForm.value.question, channel: questionForm.value.channel,
      sourceRef: questionForm.value.sourceRef || questionForm.value.channel, rawReply: questionForm.value.rawReply,
    })
    questionForm.value = { question: '', channel: 'DINGTALK', sourceRef: '', rawReply: '' }
    notify('待确认问题已记录')
    await loadRecords()
  } catch (reason) { notify(readableError(reason), 'error') }
  finally { busy.value = false }
}

async function concludeConfirmation() {
  if (!conclusionForm.value.confirmationId || !conclusionForm.value.conclusion.trim()) { notify('请填写确认结论', 'error'); return }
  busy.value = true
  try {
    await endpoints.concludeConfirmation(conclusionForm.value.confirmationId, conclusionForm.value.conclusion)
    notify('确认结论已保存')
    conclusionForm.value = { confirmationId: 0, conclusion: '' }
    await loadRecords()
  } catch (reason) { notify(readableError(reason), 'error') }
  finally { busy.value = false }
}

async function readyForReview() {
  if (!selectedRequirementId.value) return
  busy.value = true
  try {
    await endpoints.readyForReview(selectedRequirementId.value)
    notify('已进入待评审状态')
    await Promise.all([loadRecords(), store.loadRequirements(), store.loadSummary()])
  } catch (reason) { notify(readableError(reason), 'error') }
  finally { busy.value = false }
}

async function submitReview() {
  if (!selectedRequirementId.value || !latestPrd.value) { notify('请先生成 PRD', 'error'); return }
  if (!reviewForm.value.participants.trim() || !reviewForm.value.meetingAt || !reviewForm.value.conclusion.trim()) {
    notify('评审参与人、时间与结论必填', 'error'); return
  }
  busy.value = true
  try {
    await endpoints.review(selectedRequirementId.value, {
      prdId: latestPrd.value.id,
      decision: reviewForm.value.decision as 'APPROVED' | 'REJECTED',
      participants: reviewForm.value.participants,
      meetingAt: reviewForm.value.meetingAt,
      conclusion: reviewForm.value.conclusion,
    })
    notify(reviewForm.value.decision === 'APPROVED' ? '评审通过，已形成最终 PRD' : '评审已驳回，退回确认环节')
    await Promise.all([loadRecords(), store.loadRequirements(), store.loadSummary()])
  } catch (reason) { notify(readableError(reason), 'error') }
  finally { busy.value = false }
}
</script>
<template>
  <div class="view-head">
    <div><h1>需求分析</h1><div class="desc">按迭代汇总需求，生成 PRD 文档与原型；确认与评审必须人工完成</div></div>
    <div style="display:flex;gap:8px;align-items:center">
      <button v-if="latestPrd" class="btn btn-primary btn-sm" @click="openPrdPanel('doc')">最新 PRD V{{ latestPrd.versionNumber }}（{{ latestPrd.status }}）</button>
      <button v-if="latestPrd" class="btn btn-ghost btn-sm" @click="openPrdPanel('html')">查看原型</button>
      <button class="btn btn-ghost btn-sm" @click="store.loadRequirements()">刷新</button>
    </div>
  </div>
  <div v-if="error" class="alert">{{ error }}</div>
  <div class="filters">
    <span class="sub">选择迭代：</span>
    <div class="seg">
      <button v-for="iteration in store.iterations" :key="iteration.id" :class="{ on: selectedIterationId === iteration.id }" @click="selectedIterationId = iteration.id">
        {{ iteration.name }}
      </button>
    </div>
  </div>
  <div v-if="selectedIteration && selectedIteration.status !== 'LOCKED'" class="warn-box">该迭代尚未锁定范围，生成正式 PRD 会失败。请先在迭代规划中锁定范围。</div>
  <div v-if="selectedRequirement" class="panel" style="margin-bottom:16px">
    <div class="panel-h">
      <h3>状态流转</h3>
      <span class="sub">仅管理员和产品经理可操作</span>
    </div>
    <div class="panel-b" style="display:flex;justify-content:space-between;align-items:center;gap:14px;flex-wrap:wrap">
      <div style="display:flex;align-items:center;gap:10px;flex-wrap:wrap">
        <StatusChip :status="selectedRequirement.status" />
        <b>{{ selectedRequirement.title }}</b>
        <span class="sub">{{ statusLabel(selectedRequirement.status) }}</span>
      </div>
      <div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap">
        <template v-if="can.manageRequirements(auth.roles)">
          <button v-if="selectedRequirement.status === 'CONFIRMATION'" class="btn btn-primary btn-sm" :disabled="busy" @click="readyForReview">提交待评审</button>
          <button v-else-if="selectedRequirement.status === 'REVIEW'" class="btn btn-primary btn-sm" @click="scrollToReview">登记评审结果</button>
          <span v-else-if="selectedRequirement.status === 'PRD_CONFIRMED'" class="sub">PRD 已确认，请到研发任务创建任务</span>
          <span v-else-if="selectedRequirement.status === 'DEVELOPMENT'" class="sub">开发中，请到研发任务更新进度</span>
          <span v-else-if="selectedRequirement.status === 'COMPLETED'" class="sub">需求已完成</span>
          <span v-else class="sub">请先完成 PRD 与待确认问题处理</span>
        </template>
        <span v-else class="sub">当前账号无操作权限，请使用 admin 或 pm 账号</span>
      </div>
    </div>
  </div>
  <div class="split-wide">
    <div class="panel">
      <div class="panel-h"><h3>迭代需求 · {{ selectedIteration?.name || '未选择' }}</h3><span class="sub">{{ iterationRequirements.length }} 个需求</span></div>
      <div class="panel-b">
        <div v-if="!iterationRequirements.length" class="empty">本期迭代暂无需求</div>
        <div v-for="item in iterationRequirements" :key="item.id" :class="['ticket', 'clickable']" :style="item.id === selectedRequirementId ? 'border-color:#4f46e5;background:#f8f7ff' : ''" @click="selectedRequirementId = item.id">
          <div class="tt">{{ item.title }} <span class="req-id">{{ item.requirementNumber }}</span></div>
          <div class="mf"><StatusChip :status="item.status" /><span class="sub">{{ item.priority }}</span></div>
        </div>
      </div>
    </div>
    <div class="panel">
      <div class="panel-h"><h3>确认问题</h3><span class="sub">{{ openConfirmations.length }} 个待确认</span></div>
      <div class="panel-b">
        <div v-if="!confirmations.length" class="empty">暂无确认问题</div>
        <div v-for="item in confirmations" :key="item.id" class="ticket">
          <div class="tt">{{ payloadText(item.payload, 'question') }}</div>
          <div class="mf"><span class="sub">{{ payloadText(item.payload, 'channel') }} · {{ item.status === 'CONFIRMED' ? '已确认' : '待确认' }}</span></div>
          <div class="mf"><span class="sub">回复：{{ payloadText(item.payload, 'rawReply') }}</span></div>
          <button v-if="item.status !== 'CONFIRMED'" class="btn btn-ok btn-sm" :disabled="!can.manageRequirements(auth.roles)" @click="conclusionForm.confirmationId = item.id">录入结论</button>
        </div>
        <div v-if="conclusionForm.confirmationId" class="panel" style="margin-top:10px">
          <div class="panel-b">
            <div class="field"><label>确认结论</label><textarea v-model="conclusionForm.conclusion" class="in" rows="2" maxlength="10000" /></div>
            <div style="display:flex;gap:8px"><button class="btn btn-ghost btn-sm" @click="conclusionForm = { confirmationId: 0, conclusion: '' }">取消</button><button class="btn btn-primary btn-sm" :disabled="busy" @click="concludeConfirmation">保存结论</button></div>
          </div>
        </div>
        <div v-if="selectedRequirement" style="margin-top:14px;border-top:1px solid var(--line-2);padding-top:12px">
          <div class="field"><label>新增确认问题</label><input v-model="questionForm.question" class="in" maxlength="2000" placeholder="需要产品经理确认的问题" /></div>
          <div class="two-col">
            <div class="field"><label>渠道</label><select v-model="questionForm.channel" class="in"><option>DINGTALK</option><option>TINGJI</option><option>MANUAL</option></select></div>
            <div class="field"><label>来源引用</label><input v-model="questionForm.sourceRef" class="in" maxlength="1000" placeholder="消息ID / 听记链接" /></div>
          </div>
          <div class="field"><label>来源回复</label><textarea v-model="questionForm.rawReply" class="in" rows="2" maxlength="10000" /></div>
          <button class="btn btn-ghost btn-sm" :disabled="busy || !can.manageRequirements(auth.roles)" @click="addConfirmation">记录问题</button>
        </div>
      </div>
    </div>
  </div>

  <div id="prd-panel" class="panel" style="margin-top:16px">
    <div class="panel-h">
      <h3>PRD 版本</h3>
      <div style="display:flex;gap:8px">
        <button v-if="aiConfigured" class="btn btn-ghost btn-sm" @click="generatePrdDraft">AI 生成草稿</button>
        <button class="btn btn-primary btn-sm" :disabled="busy || !selectedRequirement" @click="createPrd">保存为新版本</button>
      </div>
    </div>
    <div class="panel-b">
      <div v-if="!selectedRequirement" class="empty">请选择一条迭代需求</div>
      <template v-else>
        <div class="tabs">
          <button :class="{ on: prdTab === 'doc' }" @click="prdTab = 'doc'">需求文档</button>
          <button :class="{ on: prdTab === 'html' }" @click="prdTab = 'html'">原型 HTML</button>
        </div>
        <div v-if="prdTab === 'doc'" class="field"><textarea v-model="prdForm.content" class="in" rows="9" maxlength="100000" /></div>
        <div v-else>
          <div class="field"><textarea v-model="prdForm.htmlContent" class="in" rows="6" maxlength="100000" /></div>
          <div class="iframe-box">
            <button class="fs-btn" @click="toggleFullscreen">{{ isFullscreen ? '退出全屏' : '全屏展示' }}</button>
            <iframe :srcdoc="prdForm.htmlContent" sandbox="" title="PRD 原型预览" />
          </div>
        </div>
        <div class="sec-t">历史版本（{{ prds.length }}）</div>
        <div v-if="!prds.length" class="empty">暂无 PRD 版本</div>
        <div v-for="prd in prds" :key="prd.id" class="ticket">
          <div class="tt">V{{ prd.versionNumber }} <span class="req-id">#{{ prd.id }}</span> <span class="chip chip-soft">{{ prd.status }}</span></div>
          <div class="mf"><span class="sub">{{ formatDateTime(prd.createdAt) }} · {{ prd.actor }}</span><button class="btn btn-ghost btn-sm" @click="activePrd = prd; activePrdTab = 'doc'">查看</button></div>
        </div>
        <div v-if="activePrd" style="margin-top:10px">
          <div class="tabs">
            <button :class="{ on: activePrdTab === 'doc' }" @click="activePrdTab = 'doc'">需求文档</button>
            <button :class="{ on: activePrdTab === 'html' }" @click="activePrdTab = 'html'">原型预览</button>
          </div>
          <div v-if="activePrdTab === 'doc'" class="doc md" v-html="renderMarkdown(payloadText(activePrd.payload, 'content'))" />
          <div v-else-if="activePrdHtml" class="iframe-box">
            <button class="fs-btn" @click="toggleFullscreen">{{ isFullscreen ? '退出全屏' : '全屏展示' }}</button>
            <iframe :srcdoc="activePrdHtml" sandbox="" title="PRD 原型预览" />
          </div>
          <div v-else class="empty">该 PRD 版本未包含原型 HTML</div>
        </div>

        <div id="review-section" class="sec-t">评审登记</div>
        <div v-if="reviews.length" class="warn-box">已登记 {{ reviews.length }} 次评审，最近结论：{{ reviews[0].status }}</div>
        <div class="two-col">
          <div class="field"><label>决策</label><select v-model="reviewForm.decision" class="in"><option value="APPROVED">通过</option><option value="REJECTED">驳回</option></select></div>
          <div class="field"><label>评审时间</label><input v-model="reviewForm.meetingAt" class="in" type="datetime-local" /></div>
        </div>
        <div class="field"><label>参与人</label><input v-model="reviewForm.participants" class="in" maxlength="1000" placeholder="例如：张敏, 陈宇" /></div>
        <div class="field"><label>评审结论</label><textarea v-model="reviewForm.conclusion" class="in" rows="2" maxlength="10000" /></div>
        <button class="btn btn-primary" :disabled="busy || !can.manageRequirements(auth.roles)" @click="submitReview">登记评审结果</button>
      </template>
    </div>
  </div>
</template>
