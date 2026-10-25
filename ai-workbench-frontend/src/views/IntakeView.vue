<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { endpoints } from '../api/endpoints'
import type { Requirement } from '../api/types'
import { can, readableError, sourceLabel, typeLabel } from '../ui/format'
import { buildPrompt, type ExternalSource } from '../ui/prompt'
import { notify } from '../ui/toast'
import { newIdempotencyKey } from '../ui/id'
import { useAuthStore } from '../stores/auth'
import { useWorkbenchStore } from '../stores/workbench'
import PriorityChip from '../components/PriorityChip.vue'
import TypeChip from '../components/TypeChip.vue'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import PromptPreviewModal from '../components/PromptPreviewModal.vue'

const store = useWorkbenchStore()
const auth = useAuthStore()
const emit = defineEmits<{ (event: 'navigate', page: string): void }>()
const activeTab = ref<'manual' | 'dingtalk' | 'tingji' | 'sheet'>('manual')
const title = ref('')
const rawText = ref('')
const busy = ref(false)
const error = ref('')
const normalizeTarget = ref<Requirement | null>(null)
const normalizeForm = ref({ title: '', normalizedText: '', requirementType: 'FEATURE', priority: 'P2', rationale: '' })
const aiBusy = ref(false)
const aiConfigured = ref(false)
const externalInput = ref('')
const promptVisible = ref(false)
const promptText = ref('')
const promptSource = ref<ExternalSource>('dingtalk')
const dingtalkHistory = ref<string[]>(loadDingtalkHistory())
const showDingtalkHistory = ref(false)
const deleteTarget = ref<Requirement | null>(null)

const DINGTALK_HISTORY_KEY = 'workbench:dingtalk-targets'

function loadDingtalkHistory(): string[] {
  try {
    const parsed = JSON.parse(localStorage.getItem(DINGTALK_HISTORY_KEY) || '[]')
    if (!Array.isArray(parsed)) return []
    return parsed.filter((item): item is string => typeof item === 'string').slice(0, 3)
  } catch { return [] }
}

function rememberDingtalkTarget(value: string) {
  const target = value.trim()
  if (!target) return
  dingtalkHistory.value = [target, ...dingtalkHistory.value.filter(item => item !== target)].slice(0, 3)
  try { localStorage.setItem(DINGTALK_HISTORY_KEY, JSON.stringify(dingtalkHistory.value)) } catch { /* 缓存失败不影响主流程 */ }
}

function applyDingtalkTarget(value: string) {
  externalInput.value = value
  showDingtalkHistory.value = false
}

function openDingtalkHistory() {
  if (activeTab.value === 'dingtalk') showDingtalkHistory.value = true
}

const tabs = [
  { key: 'manual', label: '人工录入' },
  { key: 'dingtalk', label: '钉钉消息' },
  { key: 'tingji', label: 'AI听记' },
  { key: 'sheet', label: '需求表格' },
] as const

const externalMeta = {
  dingtalk: { label: '钉钉用户或群聊名称', placeholder: '例如：Nelson的团队，张三' },
  tingji: { label: 'AI 听记链接或名称', placeholder: '例如：需求讨论会议261024' },
  sheet: { label: '需求表格名称或链接', placeholder: '例如：Nelson需求管理1.0' },
} as const

const activeExternalMeta = computed(() => externalMeta[activeTab.value === 'manual' ? 'dingtalk' : activeTab.value])
const promptSourceLabel = computed(() => tabs.find(item => item.key === promptSource.value)?.label ?? '')

const tabDescription = computed(() => ({
  manual: '输入原始需求内容，创建后由产品经理确认结构化结果。',
  dingtalk: '输入钉钉用户或群聊名称，生成提示词后在 WorkBuddy 中执行 dingtalk-chat-to-requirements。',
  tingji: '输入 AI 听记链接或名称，生成提示词后在 WorkBuddy 中执行 dingtalk-minutes-to-requirements。',
  sheet: '输入需求表格名称或链接，生成提示词后在 WorkBuddy 中执行 dingtalk-doc-to-requirements。',
}[activeTab.value]))

watch(activeTab, () => {
  externalInput.value = ''
  promptVisible.value = false
  showDingtalkHistory.value = false
})

onMounted(async () => {
  await store.loadRequirements()
  try {
    const configs = await endpoints.integrations()
    aiConfigured.value = configs.some(item => item.provider === 'OPENAI' && item.configured)
  } catch { aiConfigured.value = false }
})

async function createRequirement() {
  if (!title.value.trim() || !rawText.value.trim()) { notify('请填写标题与原始内容', 'error'); return }
  busy.value = true; error.value = ''
  try {
    const created = await endpoints.createRequirement({
      title: title.value.trim(), rawText: rawText.value.trim(), sourceType: activeTab.value.toUpperCase(),
      sourceRef: `${activeTab.value}-${Date.now()}`,
      idempotencyKey: newIdempotencyKey(),
    })
    notify(`已创建 ${created.requirementNumber}，请确认提取结果`)
    title.value = ''; rawText.value = ''
    await store.loadRequirements()
    openNormalize(created)
  } catch (reason) { error.value = readableError(reason); notify(error.value, 'error') }
  finally { busy.value = false }
}

function generatePrompt() {
  const input = externalInput.value.trim()
  if (!input) { notify('请输入来源名称或链接', 'error'); return }
  if (activeTab.value === 'dingtalk') rememberDingtalkTarget(input)
  promptSource.value = activeTab.value as ExternalSource
  promptText.value = buildPrompt(promptSource.value, input)
  promptVisible.value = true
}

async function openNormalize(requirement: Requirement) {
  normalizeTarget.value = requirement
  normalizeForm.value = {
    title: requirement.title,
    normalizedText: requirement.normalizedText || requirement.rawText,
    requirementType: requirement.requirementType || 'FEATURE',
    priority: requirement.priority || 'P2',
    rationale: '',
  }
}

async function requestAiSuggestion() {
  if (!normalizeTarget.value) return
  aiBusy.value = true
  try {
    const prompt = [
      '你是产品经理助手。请把下面的原始诉求整理为JSON，字段：title, normalizedText, requirementType(FEATURE/IMPROVEMENT/BUG), priority(P0-P3), rationale。',
      '只输出JSON，不要输出其他文字。',
      `原始诉求：${normalizeTarget.value.rawText}`,
    ].join('\n')
    const result = await endpoints.generate(prompt)
    const parsed = JSON.parse(result.content.replace(/```json|```/g, '').trim())
    normalizeForm.value.title = parsed.title ?? normalizeForm.value.title
    normalizeForm.value.normalizedText = parsed.normalizedText ?? normalizeForm.value.normalizedText
    normalizeForm.value.requirementType = parsed.requirementType ?? normalizeForm.value.requirementType
    normalizeForm.value.priority = parsed.priority ?? normalizeForm.value.priority
    normalizeForm.value.rationale = parsed.rationale ?? normalizeForm.value.rationale
    notify('AI 建议已填入，仅作为建议，请确认后保存')
  } catch (reason) {
    error.value = readableError(reason)
    notify(`AI 不可用：${error.value}，可继续人工填写`, 'error')
  } finally { aiBusy.value = false }
}

async function confirmNormalize() {
  if (!normalizeTarget.value) return
  if (!normalizeForm.value.title.trim() || !normalizeForm.value.normalizedText.trim()) {
    notify('标题和需求原文必填', 'error'); return
  }
  busy.value = true
  try {
    await endpoints.normalize(normalizeTarget.value.id, {
      ...normalizeForm.value,
      requirementType: normalizeForm.value.requirementType as 'FEATURE' | 'IMPROVEMENT' | 'BUG',
      priority: normalizeForm.value.priority as 'P0' | 'P1' | 'P2' | 'P3',
    })
    notify('已确认并同步至需求池')
    normalizeTarget.value = null
    await store.loadRequirements(); await store.loadSummary()
  } catch (reason) { notify(readableError(reason), 'error') }
  finally { busy.value = false }
}

async function confirmDeleteExtract() {
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
</script>
<template>
  <div class="view-head">
    <div><h1>需求录入</h1><div class="desc">人工录入与外部来源导入；外部来源先生成提示词，再交给 WorkBuddy 执行对应技能</div></div>
    <button class="btn btn-ghost btn-sm" @click="emit('navigate', 'pool')">查看需求池</button>
  </div>
  <div v-if="error" class="alert">{{ error }}</div>
  <div class="tabs">
    <button v-for="tab in tabs" :key="tab.key" :class="{ on: activeTab === tab.key }" @click="activeTab = tab.key">{{ tab.label }}</button>
  </div>
  <div class="panel panel-visible">
    <div class="panel-h"><h3>{{ tabs.find(item => item.key === activeTab)?.label }}</h3><span class="sub">{{ aiConfigured ? '模型已配置' : '模型未配置 · 人工模式' }}</span></div>
    <div class="panel-b">
      <div class="notice">{{ tabDescription }}</div>
      <template v-if="activeTab === 'manual'">
        <div class="field"><label>需求标题</label><input v-model="title" class="in" maxlength="200" placeholder="例如：看板支持自定义指标" /></div>
        <div class="field"><label>原始内容</label><textarea v-model="rawText" class="in" rows="6" maxlength="20000" placeholder="粘贴客户原话、群消息或听记纪要…" /></div>
        <button class="btn btn-primary" :disabled="busy || !can.manageRequirements(auth.roles)" :title="can.manageRequirements(auth.roles) ? '' : '仅产品经理或管理员可创建'" @click="createRequirement">
          {{ busy ? '处理中…' : '创建需求并确认提取结果' }}
        </button>
        <div class="hint">未配置外部接口时不执行自动读取；所有 AI 输出均需人工确认后才写入需求池。</div>
      </template>
      <template v-else>
        <div class="field">
          <label>{{ activeExternalMeta.label }}</label>
          <div class="input-row">
            <div class="input-wrap">
              <input
                v-model="externalInput"
                class="in"
                maxlength="1000"
                :placeholder="activeExternalMeta.placeholder"
                @focus="openDingtalkHistory"
                @blur="showDingtalkHistory = false"
                @keyup.enter="generatePrompt"
              />
              <div v-if="activeTab === 'dingtalk' && showDingtalkHistory && dingtalkHistory.length" class="history-pop">
                <div class="history-hint">最近输入</div>
                <button
                  v-for="item in dingtalkHistory"
                  :key="item"
                  class="history-item"
                  @mousedown.prevent="applyDingtalkTarget(item)"
                >{{ item }}</button>
              </div>
            </div>
            <button class="btn btn-primary" :disabled="!can.manageRequirements(auth.roles)" :title="can.manageRequirements(auth.roles) ? '' : '仅产品经理或管理员可生成提示词'" @click="generatePrompt">生成提示词</button>
          </div>
        </div>
        <div class="hint">生成后可预览并修改提示词，再点击“打开WorkBuddy”让 WorkBuddy 执行对应技能。</div>
      </template>
    </div>
  </div>
  <div class="panel" style="margin-top:16px">
    <div class="panel-h">
      <h3>待提取需求</h3>
      <div style="display:flex;align-items:center;gap:8px">
        <button class="btn btn-ghost btn-sm" :disabled="store.loading" @click="store.loadRequirements()">{{ store.loading ? '刷新中…' : '刷新数据' }}</button>
        <span class="sub">AI或人工提取完成 · 待产品经理确认</span>
      </div>
    </div>
    <div class="panel-b">
      <div v-if="!store.extractRequirements.length" class="empty">暂无待提取需求</div>
      <div v-for="requirement in store.extractRequirements" :key="requirement.id" class="ticket">
        <div class="tt">{{ requirement.title }} <span class="req-id">{{ requirement.requirementNumber }}</span></div>
        <div class="mf">
          <span><TypeChip :type="requirement.requirementType" /> <PriorityChip :priority="requirement.priority" /> <span class="sub">{{ sourceLabel(requirement.sourceType) }}</span></span>
          <div style="display:flex;gap:8px">
            <button class="btn btn-ghost btn-sm" :disabled="busy || !can.manageRequirements(auth.roles)" @click="deleteTarget = requirement">删除</button>
            <button class="btn btn-ok btn-sm" :disabled="!can.manageRequirements(auth.roles)" @click="openNormalize(requirement)">确认提取结果</button>
          </div>
        </div>
      </div>
    </div>
  </div>

  <div v-if="normalizeTarget" class="modal-mask">
    <div class="modal">
      <div class="modal-h"><h2>AI提取结果确认</h2><button class="x-btn" @click="normalizeTarget = null">✕</button></div>
      <div class="modal-b">
        <div class="notice">提取结果可编辑。确认后需求进入需求池；AI 建议不等同于产品决策。</div>
        <div class="two-col">
          <div class="field"><label>标题</label><input v-model="normalizeForm.title" class="in" maxlength="200" /></div>
          <div class="field"><label>类型</label><select v-model="normalizeForm.requirementType" class="in">
            <option value="FEATURE">{{ typeLabel('FEATURE') }}</option><option value="IMPROVEMENT">{{ typeLabel('IMPROVEMENT') }}</option><option value="BUG">{{ typeLabel('BUG') }}</option>
          </select></div>
        </div>
        <div class="field"><label>优先级</label><select v-model="normalizeForm.priority" class="in"><option>P0</option><option>P1</option><option>P2</option><option>P3</option></select></div>
        <div class="field"><label>需求原文</label><textarea v-model="normalizeForm.normalizedText" class="in" rows="4" maxlength="20000" /></div>
        <div class="field"><label>备注</label><textarea v-model="normalizeForm.rationale" class="in" rows="2" maxlength="2000" placeholder="选填，补充说明类型与优先级判断依据" /></div>
        <button class="btn btn-ghost btn-sm" :disabled="aiBusy || !aiConfigured" @click="requestAiSuggestion">{{ aiBusy ? 'AI分析中…' : aiConfigured ? 'AI 生成建议' : 'AI 未配置' }}</button>
      </div>
      <div class="modal-f">
        <button class="btn btn-ghost" @click="normalizeTarget = null">取消</button>
        <button class="btn btn-primary" :disabled="busy" @click="confirmNormalize">确认并同步需求池</button>
      </div>
    </div>
  </div>

  <ConfirmDialog
    :visible="Boolean(deleteTarget)"
    title="删除待提取需求"
    :message="deleteTarget ? `确定删除 ${deleteTarget.requirementNumber}「${deleteTarget.title}」吗？删除后不可恢复。` : ''"
    confirm-text="确认删除"
    :busy="busy"
    @update:visible="value => { if (!value) deleteTarget = null }"
    @confirm="confirmDeleteExtract"
  />

  <PromptPreviewModal
    v-model:visible="promptVisible"
    title="提示词预览"
    :source="promptSourceLabel"
    :prompt="promptText"
  />
</template>
