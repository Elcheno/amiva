import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError } from '@/lib/api'

function mockFetch(response: Response) {
  const fetchMock = vi.fn().mockResolvedValue(response)
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function setCsrfCookie(value: string) {
  document.cookie = `XSRF-TOKEN=${value}; path=/`
}

beforeEach(() => {
  setCsrfCookie('token-1')
})

afterEach(() => {
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
})

describe('api', () => {
  it('prefija /api y serializa el body como JSON', async () => {
    const fetchMock = mockFetch(Response.json({ id: 1 }))

    const data = await api<{ id: number }>('/forms', { method: 'POST', body: { name: 'x' } })

    expect(data).toEqual({ id: 1 })
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/forms')
    expect(init.body).toBe('{"name":"x"}')
    expect(init.headers).toMatchObject({ 'Content-Type': 'application/json' })
  })

  it('devuelve undefined en 204', async () => {
    mockFetch(new Response(null, { status: 204 }))
    await expect(api('/forms/1', { method: 'DELETE' })).resolves.toBeUndefined()
  })

  it('lanza ApiError con el ProblemDetail', async () => {
    mockFetch(Response.json({ title: 'Not Found', detail: 'No existe' }, { status: 404 }))

    const error = await api('/forms/9').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 404, message: 'No existe' })
  })

  it('manda el token CSRF de la cookie en peticiones que modifican datos', async () => {
    const fetchMock = mockFetch(Response.json({}))

    await api('/forms', { method: 'POST', body: {} })

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(init.headers).toMatchObject({ 'X-XSRF-TOKEN': 'token-1' })
  })

  it('no manda el token CSRF en GET', async () => {
    const fetchMock = mockFetch(Response.json({}))

    await api('/forms')

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(init.headers).not.toHaveProperty('X-XSRF-TOKEN')
  })

  it('pide la cookie CSRF al backend si aún no existe', async () => {
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
    const fetchMock = vi.fn().mockImplementation(async (url: string) => {
      if (url === '/api/auth/csrf') setCsrfCookie('token-2')
      return Response.json({})
    })
    vi.stubGlobal('fetch', fetchMock)

    await api('/forms', { method: 'POST', body: {} })

    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual(['/api/auth/csrf', '/api/forms'])
    const [, init] = fetchMock.mock.calls[1] as [string, RequestInit]
    expect(init.headers).toMatchObject({ 'X-XSRF-TOKEN': 'token-2' })
  })
})
