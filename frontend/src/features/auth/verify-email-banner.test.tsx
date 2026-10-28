import { screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { renderWithProviders } from '@/test/render'
import { meQueryOptions, type CurrentUser } from './api'
import { VerifyEmailBanner } from './verify-email-banner'

const USER: CurrentUser = {
  userId: 'u1',
  email: 'ana@example.com',
  displayName: 'Ana',
  orgId: 'o1',
  orgName: 'Org de Ana',
  emailVerified: false,
}

// Respuesta según la URL; cada llamada crea su Response (el cuerpo solo se lee una vez)
function mockApi(routes: Record<string, () => Response>) {
  const fetchMock = vi.fn<(url: string, init?: RequestInit) => Promise<Response>>(
    async (url) => routes[url]?.() ?? new Response(null, { status: 404 }),
  )
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=token; path=/'
})

afterEach(() => {
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
})

describe('VerifyEmailBanner', () => {
  it('no se muestra si el email ya está verificado', async () => {
    mockApi({ '/api/auth/me': () => Response.json({ ...USER, emailVerified: true }) })
    const { queryClient } = renderWithProviders(<VerifyEmailBanner />)

    await waitFor(() => expect(queryClient.getQueryData(meQueryOptions.queryKey)).toBeTruthy())
    expect(screen.queryByText(/Verifica tu email/)).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reenviar' })).not.toBeInTheDocument()
  })

  it('reenvía el enlace y lo confirma', async () => {
    const fetchMock = mockApi({
      '/api/auth/me': () => Response.json(USER),
      '/api/auth/verify-email/resend': () => new Response(null, { status: 204 }),
    })
    const { user } = renderWithProviders(<VerifyEmailBanner />)

    expect(
      await screen.findByText('Verifica tu email: te hemos enviado un enlace a ana@example.com'),
    ).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Reenviar' }))

    expect(await screen.findByText('Te hemos enviado otro enlace')).toBeInTheDocument()
    const call = fetchMock.mock.calls.find(([url]) => url === '/api/auth/verify-email/resend')
    expect(call?.[1]?.method).toBe('POST')
  })

  it('pide esperar si se reenvía antes de un minuto (429)', async () => {
    mockApi({
      '/api/auth/me': () => Response.json(USER),
      '/api/auth/verify-email/resend': () =>
        Response.json({ status: 429 }, { status: 429, headers: { 'Retry-After': '42' } }),
    })
    const { user } = renderWithProviders(<VerifyEmailBanner />)

    await user.click(await screen.findByRole('button', { name: 'Reenviar' }))

    expect(await screen.findByText('Espera un minuto antes de pedir otro')).toBeInTheDocument()
    expect(screen.queryByText('Te hemos enviado otro enlace')).not.toBeInTheDocument()
  })
})
