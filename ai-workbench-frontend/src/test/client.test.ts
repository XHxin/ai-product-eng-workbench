import { describe, expect, it, vi } from 'vitest'
import { ApiClient, ApiError } from '../api/client'

describe('ApiClient', () => {
  it('gets CSRF token before login and sends the configured header on writes', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ token: 'csrf-token', headerName: 'X-XSRF-TOKEN' }), { status: 200 }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ token: 'rotated-token', headerName: 'X-XSRF-TOKEN' }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 1 }), { status: 201 }))
    const client = new ApiClient(fetchMock)
    await client.login('pm', 'secret')
    await client.post('/requirements', { title: '新需求' })
    expect(fetchMock.mock.calls[0][0]).toBe('/api/auth/csrf')
    expect(fetchMock.mock.calls[2][0]).toBe('/api/auth/csrf')
    expect(new Headers(fetchMock.mock.calls[1][1].headers).get('X-XSRF-TOKEN')).toBe('csrf-token')
    expect(new Headers(fetchMock.mock.calls[3][1].headers).get('X-XSRF-TOKEN')).toBe('rotated-token')
  })

  it('fetches a CSRF token lazily for writes after a restored session', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ token: 'restored-token', headerName: 'X-XSRF-TOKEN' }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 1 }), { status: 200 }))
    const client = new ApiClient(fetchMock)
    await client.post('/iterations/1/lock', {})
    expect(fetchMock.mock.calls[0][0]).toBe('/api/auth/csrf')
    expect(new Headers(fetchMock.mock.calls[1][1].headers).get('X-XSRF-TOKEN')).toBe('restored-token')
  })

  it('maps server errors without pretending a request succeeded', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 'CONFLICT', message: '重复请求' }), { status: 409 }))
    const client = new ApiClient(fetchMock)
    await expect(client.get('/requirements')).rejects.toEqual(new ApiError(409, 'CONFLICT', '重复请求'))
  })

  it('calls the browser fetch without an illegal receiver', async () => {
    const originalFetch = globalThis.fetch
    let observedThis: unknown = 'not-called'
    globalThis.fetch = function (this: unknown) {
      observedThis = this
      return Promise.resolve(new Response(JSON.stringify({ token: 'token', headerName: 'X-CSRF-TOKEN' }), { status: 200 }))
    } as typeof fetch
    try {
      const client = new ApiClient()
      await client.csrfToken()
      expect(observedThis).toBeUndefined()
    } finally {
      globalThis.fetch = originalFetch
    }
  })
})
