const STATUS_LABELS: Record<string, string> = {
  EXTRACT: '待提取',
  POOL: '需求池',
  ITERATION: '已纳入迭代',
  CONFIRMATION: '待确认',
  REVIEW: '待评审',
  PRD_CONFIRMED: 'PRD已确认',
  DEVELOPMENT: '开发中',
  COMPLETED: '已完成',
}

const STATUS_CLASSES: Record<string, string> = {
  EXTRACT: 'st-extract',
  POOL: 'st-pool',
  ITERATION: 'st-iter',
  CONFIRMATION: 'st-confirm',
  REVIEW: 'st-review',
  PRD_CONFIRMED: 'st-analysis',
  DEVELOPMENT: 'st-dev',
  COMPLETED: 'st-done',
}

const TYPE_LABELS: Record<string, string> = { FEATURE: '新功能', IMPROVEMENT: '功能优化', BUG: '问题修复' }
const TYPE_CLASSES: Record<string, string> = { FEATURE: 'type-new', IMPROVEMENT: 'type-opt', BUG: 'type-fix' }
const SOURCE_LABELS: Record<string, string> = {
  MANUAL: '人工录入', DINGTALK: '钉钉消息', TINGJI: 'AI听记', SHEET: '需求表格', TABLE: '需求表格', DOCUMENT: '钉钉文档',
}
const ROLE_LABELS: Record<string, string> = {
  ADMIN: '管理员', PM: '产品经理', DEV: '开发人员', RELEASE_MANAGER: '发布负责人',
}

export const statusLabel = (status: string) => STATUS_LABELS[status] ?? status
export const statusClass = (status: string) => STATUS_CLASSES[status] ?? 'st-extract'
export const typeLabel = (type: string) => TYPE_LABELS[type] ?? type
export const typeClass = (type: string) => TYPE_CLASSES[type] ?? 'type-new'
export const sourceLabel = (source: string) => SOURCE_LABELS[source] ?? source
export const roleLabel = (role: string) => ROLE_LABELS[role] ?? role

export function formatDateTime(value: string | null | undefined) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

export function formatDate(value: string | null | undefined) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleDateString('zh-CN')
}

export function payloadText(payload: Record<string, unknown>, key: string, fallback = '—') {
  const value = payload?.[key]
  return typeof value === 'string' && value.trim() ? value : fallback
}

export const can = {
  manageRequirements: (roles: string[]) => roles.includes('ADMIN') || roles.includes('PM'),
  develop: (roles: string[]) => roles.includes('ADMIN') || roles.includes('DEV'),
  release: (roles: string[]) => roles.includes('ADMIN') || roles.includes('RELEASE_MANAGER'),
  admin: (roles: string[]) => roles.includes('ADMIN'),
}

export function readableError(error: unknown) {
  const candidate = error as { message?: string }
  return candidate?.message || '操作失败，请稍后重试'
}
