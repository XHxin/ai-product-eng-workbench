<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { endpoints } from '../api/endpoints'
import type { ReleaseRecord } from '../api/types'
import { useAuthStore } from '../stores/auth'
import { useWorkbenchStore } from '../stores/workbench'
import { can, formatDateTime, readableError } from '../ui/format'
import { notify } from '../ui/toast'

const store = useWorkbenchStore()
const auth = useAuthStore()
const busy = ref(false)
const error = ref('')
const showReminder = ref(false)
const showConfirm = ref(false)
const showManual = ref(false)
const reminderForm = ref({ iterationId: 0, version: '', notes: '' })
const confirmForm = ref({ record: null as ReleaseRecord | null, notes: '' })
const manualForm = ref({ record: null as ReleaseRecord | null, writebackStatus: 'DONE', notifyStatus: 'DONE', evidence: '' })
const lockedIterations = computed(() => store.iterations.filter(item => ['LOCKED', 'READY_FOR_RELEASE'].includes(item.status)))

onMounted(async () => {
  await Promise.all([store.loadReleases(), store.loadIterations()])
  if (lockedIterations.value.length) reminderForm.value.iterationId = lockedIterations.value[0].id
})

async function refresh() { await Promise.all([store.loadReleases(), store.loadSummary()]) }

function openReminder() {
  reminderForm.value = { iterationId: lockedIterations.value[0]?.id ?? 0, version: '', notes: '' }
  showReminder.value = true
}

async function sendReminder() {
  if (!reminderForm.value.iterationId || !reminderForm.value.version.trim()) { notify('请选择迭代并填写版本号', 'error'); return }
  busy.value = true
  try {
    await endpoints.releaseReminder(reminderForm.value.iterationId, reminderForm.value.version, reminderForm.value.notes)
    notify('发布提醒已记录，等待发布负责人确认')
    showReminder.value = false
    await refresh()
  } catch (reason) { error.value = readableError(reason); notify(error.value, 'error') }
  finally { busy.value = false }
}

function openConfirm(record: ReleaseRecord) {
  confirmForm.value = { record, notes: '' }
  showConfirm.value = true
}

async function confirmRelease() {
  const record = confirmForm.value.record
  if (!record) return
  busy.value = true
  try {
    await endpoints.releaseConfirm(record.iterationId, record.version, confirmForm.value.notes)
    notify('发布已人工确认，需求标记为已完成；回写与通知需人工登记结果')
    showConfirm.value = false
    await refresh()
  } catch (reason) { error.value = readableError(reason); notify(error.value, 'error') }
  finally { busy.value = false }
}

function openManual(record: ReleaseRecord) {
  manualForm.value = { record, writebackStatus: record.writebackStatus === 'DONE' ? 'DONE' : 'MANUAL_REQUIRED', notifyStatus: record.notifyStatus === 'DONE' ? 'DONE' : 'MANUAL_REQUIRED', evidence: '' }
  showManual.value = true
}

async function saveManual() {
  const record = manualForm.value.record
  if (!record) return
  busy.value = true
  try {
    await endpoints.releaseManualResult(record.releaseId, manualForm.value.writebackStatus, manualForm.value.notifyStatus, manualForm.value.evidence)
    notify('回写与通知结果已人工登记')
    showManual.value = false
    await refresh()
  } catch (reason) { notify(readableError(reason), 'error') }
  finally { busy.value = false }
}

async function retry(record: ReleaseRecord) {
  busy.value = true
  try {
    await endpoints.releaseRetry(record.releaseId)
    notify('已请求重试，回写/通知状态转为人工处理')
    await refresh()
  } catch (reason) { notify(readableError(reason), 'error') }
  finally { busy.value = false }
}
</script>
<template>
  <div class="view-head">
    <div><h1>发布中心</h1><div class="desc">迭代完成提醒 → 发布负责人人工确认 → 登记回写与通知结果</div></div>
    <button class="btn btn-primary btn-sm" :disabled="!can.release(auth.roles) || !lockedIterations.length" @click="openReminder">＋ 创建发布提醒</button>
  </div>
  <div v-if="error" class="alert">{{ error }}</div>
  <div class="notice">未配置钉钉、AI听记、表格或 Teambition 集成时，系统不会伪造“已回写/已通知”。请在人工登记中如实填写执行结果。</div>
  <div v-if="!store.releases.length" class="panel"><div class="empty">暂无发布记录</div></div>
  <div v-else class="panel">
    <div style="overflow:auto">
      <table>
        <thead><tr><th>版本</th><th>迭代</th><th>提醒</th><th>发布</th><th>回写</th><th>通知</th><th>更新时间</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="record in store.releases" :key="record.releaseId">
            <td><b>{{ record.version }}</b><div class="sub">{{ record.notes }}</div></td>
            <td>#{{ record.iterationId }}</td>
            <td><span class="chip chip-soft">{{ record.reminderStatus }}</span></td>
            <td><span class="chip chip-soft">{{ record.releaseStatus }}</span><div class="sub">{{ record.confirmedBy || '—' }}</div></td>
            <td><span :class="['chip', record.writebackStatus === 'DONE' ? 'st-done' : record.writebackStatus === 'FAILED' ? 'type-fix' : 'chip-soft']">{{ record.writebackStatus }}</span></td>
            <td><span :class="['chip', record.notifyStatus === 'DONE' ? 'st-done' : record.notifyStatus === 'FAILED' ? 'type-fix' : 'chip-soft']">{{ record.notifyStatus }}</span></td>
            <td class="sub">{{ formatDateTime(record.updatedAt) }}</td>
            <td>
              <div style="display:flex;gap:6px;flex-wrap:wrap">
                <button v-if="record.reminderStatus === 'SENT' && record.releaseStatus !== 'SUCCESS'" class="btn btn-ok btn-sm" :disabled="busy || !can.release(auth.roles)" @click="openConfirm(record)">确认发布</button>
                <button v-if="record.releaseStatus === 'SUCCESS'" class="btn btn-ghost btn-sm" :disabled="busy || !can.release(auth.roles)" @click="openManual(record)">登记结果</button>
                <button v-if="record.writebackStatus === 'FAILED' || record.notifyStatus === 'FAILED'" class="btn btn-ghost btn-sm" :disabled="busy || !can.release(auth.roles)" @click="retry(record)">重试</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>

  <div v-if="showReminder" class="modal-mask">
    <div class="modal">
      <div class="modal-h"><h2>创建发布提醒</h2><button class="x-btn" @click="showReminder = false">✕</button></div>
      <div class="modal-b">
        <div class="field"><label>迭代（已锁定）</label><select v-model.number="reminderForm.iterationId" class="in">
          <option v-for="iteration in lockedIterations" :key="iteration.id" :value="iteration.id">{{ iteration.name }}</option>
        </select></div>
        <div class="field"><label>发布版本</label><input v-model="reminderForm.version" class="in" maxlength="100" placeholder="例如 v1.0.0" /></div>
        <div class="field"><label>说明</label><textarea v-model="reminderForm.notes" class="in" rows="2" maxlength="10000" /></div>
      </div>
      <div class="modal-f"><button class="btn btn-ghost" @click="showReminder = false">取消</button><button class="btn btn-primary" :disabled="busy" @click="sendReminder">创建提醒</button></div>
    </div>
  </div>

  <div v-if="showConfirm" class="modal-mask">
    <div class="modal">
      <div class="modal-h"><h2>人工确认发布</h2><button class="x-btn" @click="showConfirm = false">✕</button></div>
      <div class="modal-b">
        <div class="warn-box">确认发布将标记相关需求为已完成。该操作必须由发布负责人执行。</div>
        <div class="meta"><span class="k">迭代</span><span class="v">#{{ confirmForm.record?.iterationId }}</span><span class="k">版本</span><span class="v">{{ confirmForm.record?.version }}</span></div>
        <div class="field"><label>发布说明</label><textarea v-model="confirmForm.notes" class="in" rows="3" maxlength="10000" /></div>
      </div>
      <div class="modal-f"><button class="btn btn-ghost" @click="showConfirm = false">取消</button><button class="btn btn-primary" :disabled="busy" @click="confirmRelease">确认发布</button></div>
    </div>
  </div>

  <div v-if="showManual" class="modal-mask">
    <div class="modal">
      <div class="modal-h"><h2>登记回写与通知结果</h2><button class="x-btn" @click="showManual = false">✕</button></div>
      <div class="modal-b">
        <div class="two-col">
          <div class="field"><label>回写状态</label><select v-model="manualForm.writebackStatus" class="in"><option>DONE</option><option>MANUAL_REQUIRED</option><option>FAILED</option></select></div>
          <div class="field"><label>通知状态</label><select v-model="manualForm.notifyStatus" class="in"><option>DONE</option><option>MANUAL_REQUIRED</option><option>FAILED</option></select></div>
        </div>
        <div class="field"><label>执行说明 / 证据</label><textarea v-model="manualForm.evidence" class="in" rows="3" maxlength="1000" placeholder="记录实际执行的表格行、群消息或失败原因" /></div>
      </div>
      <div class="modal-f"><button class="btn btn-ghost" @click="showManual = false">取消</button><button class="btn btn-primary" :disabled="busy" @click="saveManual">保存结果</button></div>
    </div>
  </div>
</template>
