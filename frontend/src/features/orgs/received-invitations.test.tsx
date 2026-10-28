import { screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { renderWithRouter } from '@/test/render'
import type { CurrentUser } from '@/features/auth/api'
import type { OrgSummary, ReceivedInvitation } from './api'
import { ReceivedInvitations } from './received-invitations'

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
  role: 'ADMIN',
  invitedByName: 'Ana',
  createdAt: '2026-10-27T10:00:00Z',
  updatedAt: '2026-10-28T10:00:00Z',
}

const JOINED: OrgSummary = { orgId: 'o2', name: 'Club', role: 'ADMIN', own: false }

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

describe('ReceivedInvitations', () => {
  it('muestra organización, rol y quién invita', async () => {
    mockApi({
      'GET /api/auth/me': () => Response.json(ME),
      'GET /api/me/invitations': () => Response.json([CLUB]),
    })
    await renderWithRouter(<ReceivedInvitations />, { initialPath: '/orgs' })

    const section = await screen.findByRole('region', { name: 'Invitaciones' })
    expect(within(section).getByText('Club')).toBeInTheDocument()
    expect(within(section).getByText('Ana te invita como Admin')).toBeInTheDocument()
    await waitFor(() =>
      expect(within(section).getByRole('button', { name: 'Aceptar' })).toBeEnabled(),
    )
    expect(within(section).getByRole('button', { name: 'Rechazar' })).toBeEnabled()
  })

  it('no aparece si no hay pendientes', async () => {
    const fetchMock = mockApi({ 'GET /api/me/invitations': () => Response.json([]) })
    await renderWithRouter(<ReceivedInvitations />, { initialPath: '/orgs' })

    await waitFor(() => expect(fetchMock).toHaveBeenCalled())
    expect(screen.queryByRole('region', { name: 'Invitaciones' })).not.toBeInTheDocument()
  })

  it('aceptar lleva a la organización', async () => {
    let pending = [CLUB]
    const fetchMock = mockApi({
      'GET /api/auth/me': () => Response.json(ME),
      'GET /api/me/invitations': () => Response.json(pending),
      'POST /api/invitations/i1/accept': () => {
        pending = []
        return Response.json(JOINED)
      },
    })
    const { user, router } = await renderWithRouter(<ReceivedInvitations />, {
      initialPath: '/orgs',
    })

    const accept = await screen.findByRole('button', { name: 'Aceptar' })
    await waitFor(() => expect(accept).toBeEnabled())
    await user.click(accept)

    await waitFor(() => expect(router.state.location.pathname).toBe('/orgs/o2'))
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/invitations/i1/accept',
      expect.objectContaining({ method: 'POST' }),
    )
  })

  it('rechazar la quita de la lista', async () => {
    let pending = [CLUB]
    mockApi({
      'GET /api/auth/me': () => Response.json(ME),
      'GET /api/me/invitations': () => Response.json(pending),
      'POST /api/invitations/i1/decline': () => {
        pending = []
        return new Response(null, { status: 204 })
      },
    })
    const { user } = await renderWithRouter(<ReceivedInvitations />, { initialPath: '/orgs' })

    const decline = await screen.findByRole('button', { name: 'Rechazar' })
    await waitFor(() => expect(decline).toBeEnabled())
    await user.click(decline)

    await waitFor(() =>
      expect(screen.queryByRole('region', { name: 'Invitaciones' })).not.toBeInTheDocument(),
    )
  })
})
