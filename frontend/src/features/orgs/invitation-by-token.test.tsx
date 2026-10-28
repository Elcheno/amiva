import { screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { renderWithRouter } from '@/test/render'
import type { CurrentUser } from '@/features/auth/api'
import type { ReceivedInvitation } from './api'
import { InvitationByToken } from './invitation-by-token'

const ME: CurrentUser = {
  userId: 'u4',
  email: 'dani@example.com',
  username: 'zorro-veloz-7',
  displayName: 'Dani',
  orgId: 'o4',
  orgName: 'Org de Dani',
  emailVerified: true,
}

const CLUB: ReceivedInvitation = {
  id: 'i1',
  organizationId: 'o2',
  organizationName: 'Club',
  role: 'MEMBER',
  invitedByName: null,
  createdAt: '2026-10-27T10:00:00Z',
  updatedAt: '2026-10-28T10:00:00Z',
}

// Respuestas por "MÉTODO ruta"; cada llamada recibe una Response nueva
function mockApi(routes: Record<string, () => Response>) {
  const fetchMock = vi.fn((url: string, init?: RequestInit) => {
    const handler = routes[`${init?.method ?? 'GET'} ${url}`]
    return Promise.resolve(handler ? handler() : new Response(null, { status: 404 }))
  })
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

describe('InvitationByToken', () => {
  it('localiza la invitación con el token en el body y permite responder', async () => {
    const fetchMock = mockApi({
      'GET /api/auth/me': () => Response.json(ME),
      'POST /api/invitations/resolve': () => Response.json(CLUB),
    })
    await renderWithRouter(<InvitationByToken token="abc_123-x" />, {
      initialPath: '/invite/abc_123-x',
    })

    expect(await screen.findByText('Club')).toBeInTheDocument()
    expect(screen.getByText('Te invitan como Miembro')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByRole('button', { name: 'Aceptar' })).toBeEnabled())
    const [, init] = fetchMock.mock.calls.find(([url]) => url === '/api/invitations/resolve')!
    expect(JSON.parse(init!.body as string)).toEqual({ token: 'abc_123-x' })
  })

  it('avisa si no es para mi cuenta o ya no está disponible', async () => {
    mockApi({
      'GET /api/auth/me': () => Response.json(ME),
      'POST /api/invitations/resolve': () => Response.json({ status: 404 }, { status: 404 }),
    })
    await renderWithRouter(<InvitationByToken token="viejo" />, { initialPath: '/invite/viejo' })

    expect(
      await screen.findByText('Esta invitación no es para tu cuenta o ya no está disponible'),
    ).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Ir a mis organizaciones' })).toHaveAttribute(
      'href',
      '/orgs',
    )
    expect(screen.queryByRole('button', { name: 'Aceptar' })).not.toBeInTheDocument()
  })

  it('sin email verificado desactiva los botones y avisa', async () => {
    mockApi({
      'GET /api/auth/me': () => Response.json({ ...ME, emailVerified: false }),
      'POST /api/invitations/resolve': () => Response.json(CLUB),
    })
    await renderWithRouter(<InvitationByToken token="abc" />, { initialPath: '/invite/abc' })

    expect(await screen.findByText('Club')).toBeInTheDocument()
    expect(
      await screen.findByText('Verifica tu email para responder a la invitación'),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Aceptar' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Rechazar' })).toBeDisabled()
  })

  it('al rechazar confirma y enlaza a mis organizaciones', async () => {
    mockApi({
      'GET /api/auth/me': () => Response.json(ME),
      'POST /api/invitations/resolve': () => Response.json(CLUB),
      'POST /api/invitations/i1/decline': () => new Response(null, { status: 204 }),
      'GET /api/me/invitations': () => Response.json([]),
    })
    const { user } = await renderWithRouter(<InvitationByToken token="abc" />, {
      initialPath: '/invite/abc',
    })

    const decline = await screen.findByRole('button', { name: 'Rechazar' })
    await waitFor(() => expect(decline).toBeEnabled())
    await user.click(decline)

    expect(await screen.findByText('Invitación rechazada')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Ir a mis organizaciones' })).toHaveAttribute(
      'href',
      '/orgs',
    )
  })
})
