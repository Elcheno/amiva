import { screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { renderWithRouter } from '@/test/render'
import type { Member } from '@/features/orgs/api'
import type { GroupDetail as GroupDetailData } from './api'
import { GroupDetail } from './group-detail'

const GROUP_URL = '/api/orgs/o1/groups/g1'
const MEMBERS_URL = '/api/orgs/o1/members'

const GROUP: GroupDetailData = {
  id: 'g1',
  name: 'Ventas',
  members: [
    {
      userId: 'u2',
      displayName: 'Bea',
      username: 'bea-b',
      role: 'ADMIN',
      addedAt: '2026-10-02T10:00:00Z',
    },
  ],
}
const ORG_MEMBERS: Member[] = [
  ['u1', 'Ana', 'ana-a', 'OWNER'],
  ['u2', 'Bea', 'bea-b', 'ADMIN'],
  ['u3', 'Carlos', 'carlos-c', 'MEMBER'],
].map(([userId, displayName, username, role]) => ({
  userId: userId!,
  displayName: displayName!,
  username: username!,
  email: `${username}@example.com`,
  role: role as Member['role'],
  createdAt: '2026-10-01T10:00:00Z',
}))

// Respuesta según método y ruta; cada llamada crea su Response
function mockApi(routes: Record<string, () => Response>) {
  const fetchMock = vi.fn<(url: string, init?: RequestInit) => Promise<Response>>(
    async (url, init) =>
      routes[`${init?.method ?? 'GET'} ${url}`]?.() ?? new Response(null, { status: 404 }),
  )
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function callsTo(fetchMock: ReturnType<typeof mockApi>, method: string) {
  return fetchMock.mock.calls.filter(([, init]) => (init?.method ?? 'GET') === method)
}

const baseRoutes = {
  [`GET ${GROUP_URL}`]: () => Response.json(GROUP),
  [`GET ${MEMBERS_URL}`]: () => Response.json(ORG_MEMBERS),
}

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=token; path=/'
})

afterEach(() => {
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
})

describe('GroupDetail', () => {
  it('muestra el nombre, los miembros con @username y fecha de alta, y el enlace de vuelta', async () => {
    mockApi(baseRoutes)
    await renderWithRouter(<GroupDetail orgId="o1" groupId="g1" myRole="MEMBER" />, {
      initialPath: '/orgs/o1/groups/g1',
    })

    expect(await screen.findByRole('heading', { name: 'Ventas' })).toBeInTheDocument()
    const row = screen.getByText('Bea').closest('tr')!
    expect(within(row).getByText('@bea-b')).toBeInTheDocument()
    expect(within(row).getByText(/2026/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Volver a la organización' })).toHaveAttribute(
      'href',
      '/orgs/o1',
    )
  })

  it('un MEMBER no ve controles de gestión ni pide la lista de miembros', async () => {
    const fetchMock = mockApi(baseRoutes)
    await renderWithRouter(<GroupDetail orgId="o1" groupId="g1" myRole="MEMBER" />, {
      initialPath: '/orgs/o1/groups/g1',
    })

    await screen.findByText('Bea')
    expect(screen.queryByLabelText('Nombre del grupo')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Renombrar' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Borrar grupo' })).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Añadir miembro')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Quitar a Bea' })).not.toBeInTheDocument()
    expect(fetchMock.mock.calls.map(([url]) => url)).not.toContain(MEMBERS_URL)
  })

  it('el select de añadir solo ofrece a los miembros que no están en el grupo', async () => {
    mockApi(baseRoutes)
    await renderWithRouter(<GroupDetail orgId="o1" groupId="g1" myRole="ADMIN" />, {
      initialPath: '/orgs/o1/groups/g1',
    })

    const select = await screen.findByLabelText('Añadir miembro')
    await screen.findByRole('option', { name: 'Carlos (@carlos-c)' })
    const options = within(select)
      .getAllByRole('option')
      .map((o) => o.textContent)
    expect(options).toEqual(['', 'Ana (@ana-a)', 'Carlos (@carlos-c)'])
  })

  it('añade un miembro y refresca el grupo', async () => {
    let group = GROUP
    const fetchMock = mockApi({
      ...baseRoutes,
      [`GET ${GROUP_URL}`]: () => Response.json(group),
      [`POST ${GROUP_URL}/members`]: () => {
        group = {
          ...GROUP,
          members: [
            ...GROUP.members,
            {
              userId: 'u3',
              displayName: 'Carlos',
              username: 'carlos-c',
              role: 'MEMBER',
              addedAt: '2026-11-04T10:00:00Z',
            },
          ],
        }
        return Response.json(group.members[1], { status: 201 })
      },
    })
    const { user } = await renderWithRouter(
      <GroupDetail orgId="o1" groupId="g1" myRole="OWNER" />,
      { initialPath: '/orgs/o1/groups/g1' },
    )

    await screen.findByRole('option', { name: 'Carlos (@carlos-c)' })
    await user.selectOptions(screen.getByLabelText('Añadir miembro'), 'u3')
    await user.click(screen.getByRole('button', { name: 'Añadir' }))

    expect(await screen.findByText('@carlos-c', { selector: 'div' })).toBeInTheDocument()
    expect(JSON.parse(callsTo(fetchMock, 'POST')[0]![1]!.body as string)).toEqual({ userId: 'u3' })
  })

  it('muestra el error si el usuario ya está en el grupo (409)', async () => {
    mockApi({
      ...baseRoutes,
      [`POST ${GROUP_URL}/members`]: () => Response.json({ status: 409 }, { status: 409 }),
    })
    const { user } = await renderWithRouter(
      <GroupDetail orgId="o1" groupId="g1" myRole="OWNER" />,
      { initialPath: '/orgs/o1/groups/g1' },
    )

    await screen.findByRole('option', { name: 'Carlos (@carlos-c)' })
    await user.selectOptions(screen.getByLabelText('Añadir miembro'), 'u3')
    await user.click(screen.getByRole('button', { name: 'Añadir' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Ese usuario ya está en el grupo')
  })

  it('quita un miembro y refresca el grupo', async () => {
    let group = GROUP
    const fetchMock = mockApi({
      ...baseRoutes,
      [`GET ${GROUP_URL}`]: () => Response.json(group),
      [`DELETE ${GROUP_URL}/members/u2`]: () => {
        group = { ...GROUP, members: [] }
        return new Response(null, { status: 204 })
      },
    })
    const { user } = await renderWithRouter(
      <GroupDetail orgId="o1" groupId="g1" myRole="ADMIN" />,
      { initialPath: '/orgs/o1/groups/g1' },
    )

    await user.click(await screen.findByRole('button', { name: 'Quitar a Bea' }))

    expect(await screen.findByText('El grupo no tiene miembros')).toBeInTheDocument()
    expect(callsTo(fetchMock, 'DELETE')).toHaveLength(1)
  })

  it('renombra el grupo', async () => {
    let group = GROUP
    const fetchMock = mockApi({
      ...baseRoutes,
      [`GET ${GROUP_URL}`]: () => Response.json(group),
      [`PATCH ${GROUP_URL}`]: () => {
        group = { ...GROUP, name: 'Comercial' }
        return Response.json({ id: 'g1', name: 'Comercial', memberCount: 1 })
      },
    })
    const { user } = await renderWithRouter(
      <GroupDetail orgId="o1" groupId="g1" myRole="OWNER" />,
      { initialPath: '/orgs/o1/groups/g1' },
    )

    const input = await screen.findByLabelText('Nombre del grupo')
    expect(input).toHaveValue('Ventas')
    await user.clear(input)
    await user.type(input, 'Comercial')
    await user.click(screen.getByRole('button', { name: 'Renombrar' }))

    expect(await screen.findByRole('heading', { name: 'Comercial' })).toBeInTheDocument()
    expect(JSON.parse(callsTo(fetchMock, 'PATCH')[0]![1]!.body as string)).toEqual({
      name: 'Comercial',
    })
  })

  it('muestra el error si el nuevo nombre ya existe (409)', async () => {
    mockApi({
      ...baseRoutes,
      [`PATCH ${GROUP_URL}`]: () => Response.json({ status: 409 }, { status: 409 }),
    })
    const { user } = await renderWithRouter(
      <GroupDetail orgId="o1" groupId="g1" myRole="OWNER" />,
      { initialPath: '/orgs/o1/groups/g1' },
    )

    const input = await screen.findByLabelText('Nombre del grupo')
    await user.clear(input)
    await user.type(input, 'Soporte')
    await user.click(screen.getByRole('button', { name: 'Renombrar' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Ya hay un grupo con ese nombre')
  })

  it('borrar pide confirmación y vuelve a la organización', async () => {
    const fetchMock = mockApi({
      ...baseRoutes,
      [`DELETE ${GROUP_URL}`]: () => new Response(null, { status: 204 }),
    })
    const { user, router } = await renderWithRouter(
      <GroupDetail orgId="o1" groupId="g1" myRole="OWNER" />,
      { initialPath: '/orgs/o1/groups/g1' },
    )

    await user.click(await screen.findByRole('button', { name: 'Borrar grupo' }))
    // Abrir el diálogo no borra nada
    expect(callsTo(fetchMock, 'DELETE')).toHaveLength(0)
    await user.click(await screen.findByRole('button', { name: 'Confirmar borrado' }))

    await vi.waitFor(() => expect(router.state.location.pathname).toBe('/orgs/o1'))
    expect(callsTo(fetchMock, 'DELETE')).toHaveLength(1)
  })
})
