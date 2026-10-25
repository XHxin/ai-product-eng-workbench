<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useWorkbenchStore } from '../stores/workbench'
import { formatDateTime, iterationStatusClass, iterationStatusLabel, statusLabel } from '../ui/format'
import StatusChip from '../components/StatusChip.vue'

const emit = defineEmits<{ (event: 'open', id: number): void; (event: 'navigate', page: string): void }>()
const store = useWorkbenchStore()
const summary = computed(() => store.summary)
const pipeline = [
  { key: 'EXTRACT', color: '#94a3b8' }, { key: 'POOL', color: '#2563eb' },
  { key: 'ITERATION', color: '#0891b2' }, { key: 'CONFIRMATION', color: '#d97706' },
  { key: 'REVIEW', color: '#ea580c' }, { key: 'PRD_CONFIRMED', color: '#7c3aed' },
  { key: 'DEVELOPMENT', color: '#4f46e5' }, { key: 'COMPLETED', color: '#16a34a' },
]
onMounted(() => store.loadAll())
</script>
<template>
  <div class="view-head">
    <div><h1>概览看板</h1><div class="desc">需求全流程态势，数据来自后端实时统计</div></div>
    <div class="head-actions">
      <button class="btn btn-ghost btn-sm" @click="store.loadAll()">刷新数据</button>
      <button class="btn btn-primary btn-sm" @click="emit('navigate', 'intake')">＋ 录入需求</button>
    </div>
  </div>
  <div v-if="store.error" class="alert">{{ store.error }}</div>
  <div class="grid kpis" style="margin-bottom:16px">
    <div class="kpi"><div class="t"><span class="ic-chip" style="background:#4f46e5">需</span>需求总量</div><div class="v">{{ summary?.totalRequirements ?? 0 }}</div><div class="d">覆盖全流程</div></div>
    <div class="kpi"><div class="t"><span class="ic-chip" style="background:#0ea5e9">提</span>待提取</div><div class="v">{{ summary?.statusCounts?.EXTRACT ?? 0 }}</div><div class="d">待产品经理确认</div></div>
    <div class="kpi"><div class="t"><span class="ic-chip" style="background:#7c3aed">评</span>待确认 / 待评审</div><div class="v">{{ (summary?.statusCounts?.CONFIRMATION ?? 0) + (summary?.statusCounts?.REVIEW ?? 0) }}</div><div class="d">需产品与评审介入</div></div>
    <div class="kpi"><div class="t"><span class="ic-chip" style="background:#db2777">发</span>待发布迭代</div><div class="v">{{ summary?.pendingReleases ?? 0 }}</div><div class="d">发布必须人工确认</div></div>
  </div>
  <div class="split-wide">
    <div class="panel">
      <div class="panel-h"><h3>需求状态分布</h3><span class="sub">9 状态机</span></div>
      <div class="panel-b">
        <div class="pipe">
          <template v-for="(step, index) in pipeline" :key="step.key">
            <div class="step">
              <div class="dot" :style="{ background: step.color }">{{ summary?.statusCounts?.[step.key] ?? 0 }}</div>
              <div class="lbl">{{ statusLabel(step.key) }}</div>
            </div>
            <div v-if="index < pipeline.length - 1" class="arrow" />
          </template>
        </div>
        <div class="legend" style="padding-top:14px">
          <span v-for="step in pipeline" :key="step.key"><i :style="{ background: step.color }" />{{ statusLabel(step.key) }}</span>
        </div>
      </div>
    </div>
    <div class="panel">
      <div class="panel-h"><h3>最近动态</h3><span class="sub">最近创建需求</span></div>
      <div class="panel-b" style="padding-top:6px">
        <div v-if="!summary?.recentRequirements?.length" class="empty">暂无需求</div>
        <div v-for="item in summary?.recentRequirements" :key="item.id" style="display:flex;gap:10px;padding:9px 0;border-bottom:1px solid var(--line-2);cursor:pointer" @click="emit('open', item.id)">
          <div style="width:7px;height:7px;border-radius:50%;background:#4f46e5;margin-top:6px;flex:none" />
          <div><div style="font-size:13px;font-weight:500">{{ item.title }}</div><div class="sub">{{ item.requirementNumber }} · {{ formatDateTime(item.createdAt) }}</div></div>
        </div>
      </div>
    </div>
  </div>
  <div class="split-2" style="margin-top:16px">
    <div class="panel">
      <div class="panel-h"><h3>迭代进度</h3><span class="sub">共 {{ summary?.totalIterations ?? 0 }} 个迭代</span></div>
      <div class="panel-b">
        <div v-if="!summary?.iterations?.length" class="empty">尚未创建迭代</div>
        <div v-for="iteration in summary?.iterations" :key="iteration.id" class="ticket">
          <div class="mf"><b style="font-size:13.5px">{{ iteration.name }}</b><span :class="['chip', iterationStatusClass(iteration.status)]">{{ iterationStatusLabel(iteration.status) }}</span></div>
          <div class="sub" style="margin-top:6px">{{ iteration.startDate }} 至 {{ iteration.endDate }}</div>
        </div>
      </div>
    </div>
    <div class="panel">
      <div class="panel-h"><h3>发布记录</h3></div>
      <div class="panel-b">
        <div v-if="!summary?.recentReleases?.length" class="empty">暂无发布记录</div>
        <div v-for="release in summary?.recentReleases" :key="release.releaseId" class="ticket">
          <div class="mf"><b style="font-size:13.5px">{{ release.version }}</b><span class="chip chip-soft">{{ release.releaseStatus }}</span></div>
          <div class="sub" style="margin-top:6px">回写：{{ release.writebackStatus }} · 通知：{{ release.notifyStatus }}</div>
        </div>
      </div>
    </div>
  </div>
</template>
