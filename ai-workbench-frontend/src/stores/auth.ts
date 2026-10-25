import { defineStore } from 'pinia'
import { api, ApiError } from '../api/client'
export type User = { username: string; roles: string[] }
export const useAuthStore = defineStore('auth', {
  state: () => ({ user: null as User | null, loading: false, error: '' }),
  getters: { roles: state => state.user?.roles ?? [], loggedIn: state => Boolean(state.user) },
  actions: {
    async login(username: string, password: string) {
      this.loading = true; this.error = ''
      try {
        await api.login(username, password)
        this.user = await api.get<User>('/auth/me')
      } catch (error) {
        this.error = error instanceof ApiError ? error.message : '登录失败'
        this.user = null
        throw error
      } finally { this.loading = false }
    },
    async restore() {
      try { this.user = await api.get<User>('/auth/me') }
      catch (error) {
        if (!(error instanceof ApiError) || error.status !== 401) throw error
        this.user = null
      }
    },
    async logout() {
      try { await api.logout() } finally { this.user = null }
    }
  }
})
