import { screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { renderWithProviders } from '@/test/render'
import type { InvitableUser, Invitation } from './api'
import { InviteMember } from './invite-member'

const DANI: InvitableUser = {
  userId: 'u4',
  username: 'zorro-veloz-7',
  displayName: 'Dani',
  pendingInvitation: false,
}
const ELENA: InvitableUser = {
  userId: 'u5',
  username: 'elena-g',
  displayName: 'Elena',
  pendingInvitation: true,
}
const INVITATION: Invitation = {
  invitationId: 'i1',
  userId: 'u4',
  username: 'zorro-veloz-7',
  displayName: 'Dani',
  role: 'MEMBER',
  invitedByName: 'Ana',
  createdAt: '2026-10-28T10:00:00Z',
  updatedAt: '2026-10-28T10:00:00Z',
}

const SEARCH_URL = '/api/orgs/o1/invitable-users'
const INVITE_URL = '/api/orgs/o1/invitations'

// Respuesta según la ruta (sin query string); cada llamada crea su Response
function mockApi(routes: Record<string, () => Response>) {
  const fetchMock = vi.fn<(url: string, init?: RequestInit) => Promise<Response>>(
    async (url) => routes[url.split('?')[0]!]?.() ?? new Response(null, { status: 404 }),
  )
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function searchCalls(fetchMock: ReturnType<typeof mockApi>) {
  return fetchMock.mock.calls.filter(([url]) => url.startsWith(SEARCH_URL))
}

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=token; path=/'
})

afterEach(() => {
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
})

describe('InviteMember', () => {
  it('un MEMBER no ve el botón de invitar', () => {
    mockApi({})
    renderWithProviders(<InviteMember orgId="o1" myRole="MEMBER" />)

    expect(screen.queryByRole('button', { name: 'Invitar' })).not.toBeInTheDocument()
  })

  it('no busca con menos de 3 caracteres', async () => {
    const fetchMock = mockApi({ [SEARCH_URL]: () => Response.json([DANI]) })
    const { user } = renderWithProviders(<InviteMember orgId="o1" myRole="ADMIN" />)

    await user.click(screen.getByRole('button', { name: 'Invitar' }))
    await user.type(await screen.findByLabelText('Buscar usuario'), 'da ')
    // Pasado el debounce sigue sin consultar
    await new Promise((resolve) => setTimeout(resolve, 400))

    expect(screen.getByText('Escribe al menos 3 caracteres')).toBeInTheDocument()
    expect(searchCalls(fetchMock)).toHaveLength(0)
  })

  it('muestra los resultados con @username y si tienen invitación pendiente', async () => {
    const fetchMock = mockApi({ [SEARCH_URL]: () => Response.json([DANI, ELENA]) })
    const { user } = renderWithProviders(<InviteMember orgId="o1" myRole="ADMIN" />)

    await user.click(screen.getByRole('button', { name: 'Invitar' }))
    await user.type(await screen.findByLabelText('Buscar usuario'), 'dan')

    const dani = await screen.findByRole('button', { name: /Dani/ })
    expect(dani).toHaveTextContent('@zorro-veloz-7')
    expect(dani).not.toHaveTextContent('Invitación pendiente')
    expect(screen.getByRole('button', { name: /Elena/ })).toHaveTextContent('Invitación pendiente')
    expect(searchCalls(fetchMock).map(([url]) => url)).toEqual([`${SEARCH_URL}?q=dan`])
  })

  it('un ADMIN solo puede invitar como Miembro', async () => {
    mockApi({ [SEARCH_URL]: () => Response.json([DANI]) })
    const { user } = renderWithProviders(<InviteMember orgId="o1" myRole="ADMIN" />)

    await user.click(screen.getByRole('button', { name: 'Invitar' }))
    await user.type(await screen.findByLabelText('Buscar usuario'), 'dan')
    await user.click(await screen.findByRole('button', { name: /Dani/ }))

    const role = screen.getByLabelText('Rol')
    expect(role).toHaveValue('MEMBER')
    expect(screen.queryByRole('option', { name: 'Admin' })).not.toBeInTheDocument()
  })

  it('el OWNER envía {userId, role} y ve la confirmación', async () => {
    const fetchMock = mockApi({
      [SEARCH_URL]: () => Response.json([DANI]),
      [INVITE_URL]: () => Response.json({ ...INVITATION, role: 'ADMIN' }, { status: 201 }),
    })
    const { user } = renderWithProviders(<InviteMember orgId="o1" myRole="OWNER" />)

    await user.click(screen.getByRole('button', { name: 'Invitar' }))
    await user.type(await screen.findByLabelText('Buscar usuario'), 'dan')
    await user.click(await screen.findByRole('button', { name: /Dani/ }))
    await user.selectOptions(screen.getByLabelText('Rol'), 'ADMIN')
    await user.click(screen.getByRole('button', { name: 'Enviar invitación' }))

    expect(await screen.findByText('Invitación enviada a Dani')).toBeInTheDocument()
    const call = fetchMock.mock.calls.find(([url]) => url === INVITE_URL)
    expect(call?.[1]?.method).toBe('POST')
    expect(JSON.parse(call?.[1]?.body as string)).toEqual({ userId: 'u4', role: 'ADMIN' })
  })

  it('avisa si el usuario ya es miembro (409)', async () => {
    mockApi({
      [SEARCH_URL]: () => Response.json([DANI]),
      [INVITE_URL]: () => Response.json({ status: 409 }, { status: 409 }),
    })
    const { user } = renderWithProviders(<InviteMember orgId="o1" myRole="ADMIN" />)

    await user.click(screen.getByRole('button', { name: 'Invitar' }))
    await user.type(await screen.findByLabelText('Buscar usuario'), 'dan')
    await user.click(await screen.findByRole('button', { name: /Dani/ }))
    await user.click(screen.getByRole('button', { name: 'Enviar invitación' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Ese usuario ya es miembro de la organización',
    )
    await waitFor(() =>
      expect(screen.queryByText('Invitación enviada a Dani')).not.toBeInTheDocument(),
    )
  })

  it('avisa si no se pudo enviar el email (503)', async () => {
    mockApi({
      [SEARCH_URL]: () => Response.json([DANI]),
      [INVITE_URL]: () => Response.json({ status: 503 }, { status: 503 }),
    })
    const { user } = renderWithProviders(<InviteMember orgId="o1" myRole="ADMIN" />)

    await user.click(screen.getByRole('button', { name: 'Invitar' }))
    await user.type(await screen.findByLabelText('Buscar usuario'), 'dan')
    await user.click(await screen.findByRole('button', { name: /Dani/ }))
    await user.click(screen.getByRole('button', { name: 'Enviar invitación' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'No se pudo enviar el email de invitación. Inténtalo de nuevo.',
    )
  })
})
