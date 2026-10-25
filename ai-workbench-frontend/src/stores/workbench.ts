import { defineStore } from 'pinia'
import { endpoints } from '../api/endpoints'
import type { DashboardSummary, Iteration, ReleaseRecord, Requirement } from '../api/types'
import { readableError } from '../ui/format'

export const useWorkbenchStore = defineStore('workbench', {
  state: () => ({
    requirements: [] as Requirement[],
    iterations: [] as Iteration[],
    releases: [] as ReleaseRecord[],
    summary: null as DashboardSummary | null,
    loading: false,
    error: '',
    query: '',
  }),
  getters: {
    requirementById: state => (id: number | null) => state.requirements.find(item => item.id === id),
    poolRequirements: state => state.requirements.filter(item => item.status === 'POOL'),
    extractRequirements: state => state.requirements.filter(item => item.status === 'EXTRACT'),
    iterationById: state => (id: number | null) => state.iterations.find(item => item.id === id),
    filteredRequirements: state => {
      const keyword = state.query.trim().toLowerCase()
      if (!keyword) return state.requirements
      return state.requirements.filter(item =>
        item.title.toLowerCase().includes(keyword)
        || item.requirementNumber.toLowerCase().includes(keyword))
    },
  },
  actions: {
    async loadAll() {
      await Promise.all([this.loadRequirements(), this.loadIterations(), this.loadReleases(), this.loadSummary()])
    },
    async loadRequirements() {
      await this.guard(async () => {
        const page = await endpoints.requirements()
        this.requirements = page.content
      })
    },
    async loadIterations() {
      await this.guard(async () => {
        const page = await endpoints.iterations()
        this.iterations = page.content
      })
    },
    async loadReleases() {
      await this.guard(async () => {
        const page = await endpoints.releases()
        this.releases = page.content
      })
    },
    async loadSummary() {
      await this.guard(async () => { this.summary = await endpoints.summary() })
    },
    async guard(action: () => Promise<void>) {
      this.loading = true
      this.error = ''
      try { await action() }
      catch (error) { this.error = readableError(error) }
      finally { this.loading = false }
    },
    setQuery(query: string) { this.query = query },
  },
})
