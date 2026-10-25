export type Role = 'ADMIN' | 'PM' | 'DEV' | 'RELEASE_MANAGER'

export type Requirement = {
  id: number
  requirementNumber: string
  title: string
  rawText: string
  sourceType: string
  sourceRef: string
  normalizedText: string
  requirementType: 'FEATURE' | 'IMPROVEMENT' | 'BUG'
  priority: 'P0' | 'P1' | 'P2' | 'P3'
  status: string
  iterationId: number | null
  createdAt: string
}

export type Iteration = {
  id: number
  name: string
  startDate: string
  endDate: string
  status: 'PLANNING' | 'LOCKED'
}

export type WorkflowRecord = {
  id: number
  category: string
  requirementId: number | null
  iterationId: number | null
  parentId: number | null
  versionNumber: number
  status: string
  payload: Record<string, unknown>
  actor: string
  createdAt: string
}

export type ReleaseRecord = {
  releaseId: number
  iterationId: number
  version: string
  reminderStatus: string
  confirmationStatus: string
  releaseStatus: string
  writebackStatus: string
  notifyStatus: string
  notes: string
  confirmedBy: string
  lastError: string
  updatedAt: string
}

export type AuditRecord = {
  id: number
  action: string
  actor: string
  resource: string
  details: string
  createdAt: string
}

export type IntegrationConfig = {
  provider: string
  endpoint: string
  model: string
  secret: string
  configured: boolean
}

export type Page<T> = {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export type DashboardSummary = {
  totalRequirements: number
  statusCounts: Record<string, number>
  totalIterations: number
  lockedIterations: number
  pendingReleases: number
  recentRequirements: Requirement[]
  iterations: Iteration[]
  recentReleases: ReleaseRecord[]
}

export type PrdPayload = {
  content: string
  htmlContent: string
  templateVersion: string
  inputSnapshot: string
}

export type ConfirmationPayload = {
  question: string
  channel: string
  sourceRef: string
  rawReply: string
}

export type TaskPayload = {
  title: string
  scope: string
  acceptance: string
  dependencies: string
}

export type DevelopmentPayload = {
  confirmedHours: number
  technicalPlan: string
  subtasks: string[]
  testPoints: string[]
}

export type ReviewPayload = {
  prdId: number
  decision: 'APPROVED' | 'REJECTED'
  participants: string
  meetingAt: string
  conclusion: string
}
