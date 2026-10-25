export type FetchLike = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>
export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string) { super(message) }
}

type CsrfPayload = { token: string; headerName: string }
export class ApiClient {
  private csrf?: CsrfPayload
  constructor(private readonly fetcher: FetchLike = (input, init) => fetch(input, init), private readonly baseUrl = '/api') {}
  private async request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const method = (init.method ?? 'GET').toUpperCase()
    const headers = new Headers(init.headers)
    if (method !== 'GET') {
      if (!this.csrf) await this.csrfToken()
      headers.set(this.csrf!.headerName, this.csrf!.token)
    }
    if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
    const response = await this.fetcher(`${this.baseUrl}${path}`, { ...init, credentials: 'include', headers })
    if (!response.ok) {
      let body: { code?: string; message?: string } = {}
      try { body = await response.json() } catch { /* empty response */ }
      throw new ApiError(response.status, body.code ?? `HTTP_${response.status}`, body.message ?? this.defaultMessage(response.status))
    }
    if (response.status === 204) return undefined as T
    return response.json() as Promise<T>
  }
  private defaultMessage(status: number) { return ({ 400: '请求参数无效', 401: '请先登录', 403: '权限不足或令牌无效', 409: '请求冲突', 503: '服务暂不可用' } as Record<number, string>)[status] ?? '请求失败' }
  async csrfToken() { this.csrf = await this.request<CsrfPayload>('/auth/csrf'); return this.csrf }
  async login(username: string, password: string) {
    await this.csrfToken()
    const body = new URLSearchParams({ username, password })
    await this.request<void>('/auth/login', { method: 'POST', body, headers: { 'Content-Type': 'application/x-www-form-urlencoded' } })
    // Spring Security rotates the CSRF token after authentication.
    await this.csrfToken()
  }
  logout() { return this.request<void>('/auth/logout', { method: 'POST' }) }
  get<T>(path: string) { return this.request<T>(path) }
  post<T>(path: string, body: unknown) { return this.request<T>(path, { method: 'POST', body: JSON.stringify(body) }) }
  put<T>(path: string, body: unknown) { return this.request<T>(path, { method: 'PUT', body: JSON.stringify(body) }) }
  delete<T>(path: string) { return this.request<T>(path, { method: 'DELETE' }) }
}
export const api = new ApiClient()
