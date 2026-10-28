import { screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { renderWithRouter } from '@/test/render'
import { VerifyEmail } from './verify-email'

function mockFetch(response: Response) {
  const fetchMock = vi.fn().mockResolvedValue(response)
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function problem(reason: string) {
  return Response.json({ status: 400, reason }, { status: 400 })
}

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=token; path=/'
})

afterEach(() => {
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
})

describe('VerifyEmail', () => {
  it('envía el token una sola vez y confirma la verificación', async () => {
    const fetchMock = mockFetch(new Response(null, { status: 204 }))
    await renderWithRouter(<VerifyEmail token="abc_123-x" />, { initialPath: '/verify-email' })

    expect(await screen.findByText('Email verificado')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Ir al inicio' })).toHaveAttribute('href', '/')
    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/auth/verify-email')
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body as string)).toEqual({ token: 'abc_123-x' })
  })

  it('avisa si el enlace ha caducado', async () => {
    mockFetch(problem('EXPIRED'))
    await renderWithRouter(<VerifyEmail token="viejo" />, { initialPath: '/verify-email' })

    expect(await screen.findByText('El enlace ha caducado')).toBeInTheDocument()
    expect(screen.getByText(/pide otro enlace/)).toBeInTheDocument()
  })

  it('avisa si el enlace no es válido', async () => {
    mockFetch(problem('INVALID'))
    await renderWithRouter(<VerifyEmail token="inventado" />, { initialPath: '/verify-email' })

    expect(await screen.findByText('El enlace no es válido')).toBeInTheDocument()
  })

  it('avisa si el enlace ya se ha usado', async () => {
    mockFetch(problem('USED'))
    await renderWithRouter(<VerifyEmail token="usado" />, { initialPath: '/verify-email' })

    expect(await screen.findByText('Este enlace ya se ha usado')).toBeInTheDocument()
  })

  it('sin token no llama a la API', async () => {
    const fetchMock = mockFetch(new Response(null, { status: 204 }))
    await renderWithRouter(<VerifyEmail />, { initialPath: '/verify-email' })

    expect(await screen.findByText('El enlace no es válido')).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })
})
