import { afterEach, describe, expect, it, vi } from 'vitest'
import { AdminError, createAdminScope, queryPath, request } from './client'
afterEach(() => vi.unstubAllGlobals())
describe('admin boundary', () => {
  it('rejects late responses after A B A and aborts previous work', () => {
    const scope = createAdminScope(); const a = scope.next(); const b = scope.next(); const again = scope.next()
    expect(a.signal.aborted).toBe(true); expect(a.current()).toBe(false); expect(b.current()).toBe(false); expect(again.current()).toBe(true)
    scope.clear(); expect(again.current()).toBe(false)
  })
  it('uses same origin cookie and existing csrf without product workspace header', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'fixture' })))
      .mockResolvedValueOnce(new Response(JSON.stringify({ userId: 'new' }), { status: 201 }))
    vi.stubGlobal('fetch', fetcher)
    await request('/api/admin/users', { method: 'POST', body: { email: 'a@example.com' } })
    const options = fetcher.mock.calls[1]![1]
    expect(options.credentials).toBe('same-origin'); expect(options.headers['X-CSRF-TOKEN']).toBe('fixture'); expect(options.headers['X-Workspace-ID']).toBeUndefined()
  })
  it('only retains stable errors and request id', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 'STATE_VERSION_CONFLICT', requestId: 'id', message: 'SECRET' }), { status: 409 })))
    await expect(request('/api/admin/users')).rejects.toEqual(new AdminError(409, 'STATE_VERSION_CONFLICT', 'id'))
  })
  it('encodes explicit scope and omits empty filters', () => {
    expect(queryPath('/api/admin/knowledge/documents', { workspaceId: 'target', q: '', cursor: '' })).toBe('/api/admin/knowledge/documents?workspaceId=target')
  })
})
