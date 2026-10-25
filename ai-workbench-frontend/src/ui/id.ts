export function newIdempotencyKey() {
  const cryptoApi = globalThis.crypto
  if (typeof cryptoApi?.randomUUID === 'function') return cryptoApi.randomUUID()
  if (typeof cryptoApi?.getRandomValues === 'function') {
    const bytes = cryptoApi.getRandomValues(new Uint8Array(16))
    const hex = Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('')
    return `id-${Date.now()}-${hex}`
  }
  return `id-${Date.now()}-${Math.random().toString(36).slice(2)}`
}
