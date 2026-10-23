import { afterEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError } from '@/lib/api'

function mockFetch(response: Response) {
  const fetchMock = vi.fn().mockResolvedValue(response)
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

afterEach(() => {
  vi.unstubAllGlobals()
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
})
