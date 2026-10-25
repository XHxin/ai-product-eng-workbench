<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useAuthStore } from '../stores/auth'
import { useWorkbenchStore } from '../stores/workbench'
import { roleLabel } from '../ui/format'
import RequirementDrawer from '../components/RequirementDrawer.vue'
import OverviewView from '../views/OverviewView.vue'
import FlowView from '../views/FlowView.vue'
import IntakeView from '../views/IntakeView.vue'
import PoolView from '../views/PoolView.vue'
import IterationView from '../views/IterationView.vue'
import AnalysisView from '../views/AnalysisView.vue'
import TaskView from '../views/TaskView.vue'
import ReleaseView from '../views/ReleaseView.vue'
import SettingsView from '../views/SettingsView.vue'
import AuditView from '../views/AuditView.vue'
import ToastHost from '../components/ToastHost.vue'

type PageKey = 'overview' | 'flow' | 'intake' | 'pool' | 'iterations' | 'analysis' | 'tasks' | 'release' | 'settings' | 'audit'
const auth = useAuthStore()
const store = useWorkbenchStore()
const active = ref<PageKey>('overview')
const drawerRequirementId = ref<number | null>(null)
const navGroups = [
  { section: '工作区', items: [{ key: 'overview' as PageKey, label: '概览看板', icon: '⌂' }] },
  { section: '需求全生命周期', items: [
    { key: 'intake' as PageKey, label: '需求录入', icon: '＋' }, { key: 'pool' as PageKey, label: '需求池', icon: '▤' },
    { key: 'iterations' as PageKey, label: '迭代规划', icon: '◷' }, { key: 'analysis' as PageKey, label: '需求分析', icon: '▣' },
    { key: 'tasks' as PageKey, label: '研发任务', icon: '✓' },
  ] },
  { section: '系统', items: [{ key: 'audit' as PageKey, label: '审计日志', icon: '◌' }] },
]
const titles: Record<PageKey, string> = {
  overview: '概览看板', flow: '业务流程', intake: '需求录入', pool: '需求池', iterations: '迭代规划',
  analysis: '需求分析', tasks: '研发任务', release: '发布中心', settings: '集成配置', audit: '审计日志',
}
const pages = { overview: OverviewView, flow: FlowView, intake: IntakeView, pool: PoolView, iterations: IterationView, analysis: AnalysisView, tasks: TaskView, release: ReleaseView, settings: SettingsView, audit: AuditView }
const currentPage = computed(() => pages[active.value])
const roleText = computed(() => auth.roles.map(roleLabel).join(' / ') || '未登录')

function navigate(page: string) {
  if (page in pages) active.value = page as PageKey
}
function openRequirement(id: number) { drawerRequirementId.value = id }
onMounted(() => store.loadAll())
</script>
<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <div class="logo">AI</div>
        <div><b>产研协作工作台</b><small>V2 · 录入→分析→交付</small></div>
      </div>
      <nav>
        <template v-for="group in navGroups" :key="group.section">
          <div class="nav-sec">{{ group.section }}</div>
          <button v-for="item in group.items" :key="item.key" :data-testid="`nav-${item.key}`" :class="['nav-item', { active: active === item.key }]" @click="active = item.key">
            <span class="ic">{{ item.icon }}</span><span>{{ item.label }}</span>
          </button>
        </template>
      </nav>
      <div class="sidebar-foot">AI 负责提取与建议<br />关键决策必须人工确认<br />{{ roleText }}</div>
    </aside>
    <section class="main">
      <header class="topbar">
        <div class="crumb">AI产研协作工作台 / <b>{{ titles[active] }}</b></div>
        <span class="role-badge" style="margin-left:auto">{{ roleText }}</span>
        <button class="avatar" :title="`${auth.user?.username} · 退出登录`" @click="auth.logout()">{{ auth.user?.username?.slice(0, 1).toUpperCase() }}</button>
      </header>
      <main class="content">
        <component :is="currentPage" @open="openRequirement" @navigate="navigate" />
      </main>
    </section>
    <RequirementDrawer v-if="drawerRequirementId" :requirement-id="drawerRequirementId" @close="drawerRequirementId = null" />
    <ToastHost />
  </div>
</template>
