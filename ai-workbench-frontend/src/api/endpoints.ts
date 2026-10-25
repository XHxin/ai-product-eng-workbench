import { api } from './client'
import type {
  AuditRecord, ConfirmationPayload, DashboardSummary, DevelopmentPayload, IntegrationConfig,
  Iteration, Page, PrdPayload, ReleaseRecord, Requirement, ReviewPayload, TaskPayload, WorkflowRecord,
} from './types'

export type RequirementInput = {
  title: string
  rawText: string
  sourceType: string
  sourceRef: string
  idempotencyKey: string
}

export type NormalizeInput = {
  title: string
  normalizedText: string
  requirementType: 'FEATURE' | 'IMPROVEMENT' | 'BUG'
  priority: 'P0' | 'P1' | 'P2' | 'P3'
  rationale: string
}

const pageQuery = (page = 0, size = 50) => `?page=${page}&size=${size}`

export const endpoints = {
  me: () => api.get<{ username: string; roles: string[] }>('/auth/me'),

  summary: () => api.get<DashboardSummary>('/dashboard/summary'),

  requirements: (page = 0, size = 50) => api.get<Page<Requirement>>(`/requirements${pageQuery(page, size)}`),
  requirement: (id: number) => api.get<Requirement>(`/requirements/${id}`),
  createRequirement: (input: RequirementInput) => api.post<Requirement>('/requirements', input),
  deleteRequirement: (id: number) => api.delete<void>(`/requirements/${id}`),
  normalize: (id: number, input: NormalizeInput) => api.post<Requirement>(`/requirements/${id}/normalize`, input),
  plan: (id: number, iterationId: number) => api.post<Requirement>(`/requirements/${id}/planning`, { iterationId }),
  readyForReview: (id: number) => api.post<Requirement>(`/requirements/${id}/ready-for-review`, {}),
  review: (id: number, input: ReviewPayload) => api.post<WorkflowRecord>(`/requirements/${id}/reviews`, input),
  createTask: (id: number, input: TaskPayload) => api.post<WorkflowRecord>(`/requirements/${id}/tasks`, input),
  records: (id: number, category: string) =>
    api.get<Page<WorkflowRecord>>(`/requirements/${id}/records?category=${encodeURIComponent(category)}&page=0&size=50`),
  recordsByCategory: (category: string) =>
    api.get<Page<WorkflowRecord>>(`/records?category=${encodeURIComponent(category)}&page=0&size=100`),

  createPrd: (id: number, input: Pick<PrdPayload, 'content' | 'htmlContent'>) =>
    api.post<WorkflowRecord>(`/requirements/${id}/prd`, input),
  createConfirmation: (id: number, input: ConfirmationPayload) =>
    api.post<WorkflowRecord>(`/requirements/${id}/confirmations`, input),
  concludeConfirmation: (id: number, conclusion: string) =>
    api.post<WorkflowRecord>(`/confirmations/${id}/confirm`, { conclusion }),

  confirmTask: (id: number) => api.post<WorkflowRecord>(`/tasks/${id}/confirm`, {}),
  developTask: (id: number, input: DevelopmentPayload) => api.post<WorkflowRecord>(`/tasks/${id}/development`, input),
  progressTask: (id: number, status: 'IN_PROGRESS' | 'DONE') =>
    api.post<WorkflowRecord>(`/tasks/${id}/progress`, { status }),

  iterations: (page = 0, size = 50) => api.get<Page<Iteration>>(`/iterations${pageQuery(page, size)}`),
  createIteration: (input: { name: string; startDate: string; endDate: string }) =>
    api.post<Iteration>('/iterations', input),
  lockIteration: (id: number) => api.post<Iteration>(`/iterations/${id}/lock`, {}),
  assignRequirement: (iterationId: number, requirementId: number) =>
    api.post<Requirement>(`/iterations/${iterationId}/requirements`, { requirementId }),
  removeRequirement: (iterationId: number, requirementId: number) =>
    api.delete<void>(`/iterations/${iterationId}/requirements/${requirementId}`),

  releases: (page = 0, size = 50) => api.get<Page<ReleaseRecord>>(`/releases${pageQuery(page, size)}`),
  releasesByIteration: (iterationId: number) => api.get<ReleaseRecord[]>(`/iterations/${iterationId}/releases`),
  releaseReminder: (iterationId: number, version: string, notes: string) =>
    api.post<ReleaseRecord>(`/iterations/${iterationId}/release-reminder`, { version, notes }),
  releaseConfirm: (iterationId: number, version: string, notes: string) =>
    api.post<ReleaseRecord>(`/iterations/${iterationId}/release-confirm`, { version, notes }),
  releaseManualResult: (recordId: number, writebackStatus: string, notifyStatus: string, evidence: string) =>
    api.post<ReleaseRecord>(`/releases/${recordId}/manual-result`, { writebackStatus, notifyStatus, evidence }),
  releaseRetry: (recordId: number) => api.post<ReleaseRecord>(`/releases/${recordId}/retry`, {}),

  audits: (page = 0, size = 50) => api.get<Page<AuditRecord>>(`/audits${pageQuery(page, size)}`),
  integrations: () => api.get<IntegrationConfig[]>('/integrations'),
  saveIntegration: (provider: string, input: { endpoint: string; secret: string; model: string }) =>
    api.put<{ provider: string; status: string; secret: string }>(`/integrations/${provider}`, input),
  generate: (prompt: string) => api.post<{ content: string }>('/ai/generate', { prompt }),
}
