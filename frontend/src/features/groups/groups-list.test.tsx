import { screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { renderWithRouter } from '@/test/render'
import type { GroupSummary } from './api'
import { GroupsList } from './groups-list'

const VENTAS: GroupSummary = { id: 'g1', name: 'Ventas', memberCount: 2 }
const SOPORTE: GroupSummary = { id: 'g2', name: 'Soporte', memberCount: 1 }

const GROUPS_URL = '/api/orgs/o1/groups'

// Respuesta según método y ruta; cada llamada crea su Response
function mockApi(routes: Record<string, () => Response>) {
  const fetchMock = vi.fn<(url: string, init?: RequestInit) => Promise<Response>>(
    async (url, init) =>
      routes[`${init?.method ?? 'GET'} ${url}`]?.() ?? new Response(null, { status: 404 }),
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

describe('GroupsList', () => {
  it('muestra cada grupo con su número de miembros y enlace al detalle', async () => {
    mockApi({ [`GET ${GROUPS_URL}`]: () => Response.json([VENTAS, SOPORTE]) })
    await renderWithRouter(<GroupsList orgId="o1" myRole="MEMBER" />)

    const link = await screen.findByRole('link', { name: 'Ventas' })
    expect(link).toHaveAttribute('href', '/orgs/o1/groups/g1')
    expect(screen.getByText('2 miembros')).toBeInTheDocument()
    expect(screen.getByText('1 miembro')).toBeInTheDocument()
  })

  it('avisa si no hay grupos', async () => {
    mockApi({ [`GET ${GROUPS_URL}`]: () => Response.json([]) })
    await renderWithRouter(<GroupsList orgId="o1" myRole="OWNER" />)

    expect(await screen.findByText('No hay grupos')).toBeInTheDocument()
  })

  it('un MEMBER ve los grupos pero no el formulario de crear', async () => {
    mockApi({ [`GET ${GROUPS_URL}`]: () => Response.json([VENTAS]) })
    await renderWithRouter(<GroupsList orgId="o1" myRole="MEMBER" />)

    await screen.findByText('Ventas')
    expect(screen.queryByLabelText('Crear grupo')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Crear' })).not.toBeInTheDocument()
  })

  it.each(['OWNER', 'ADMIN'] as const)('un %s ve el formulario de crear', async (role) => {
    mockApi({ [`GET ${GROUPS_URL}`]: () => Response.json([]) })
    await renderWithRouter(<GroupsList orgId="o1" myRole={role} />)

    expect(await screen.findByLabelText('Crear grupo')).toBeInTheDocument()
  })

  it('crea un grupo, vacía el campo y refresca la lista', async () => {
    let groups = [VENTAS]
    const fetchMock = mockApi({
      [`GET ${GROUPS_URL}`]: () => Response.json(groups),
      [`POST ${GROUPS_URL}`]: () => {
        groups = [...groups, SOPORTE]
        return Response.json({ ...SOPORTE, memberCount: 0 }, { status: 201 })
      },
    })
    const { user } = await renderWithRouter(<GroupsList orgId="o1" myRole="ADMIN" />)

    await user.type(await screen.findByLabelText('Crear grupo'), 'Soporte')
    await user.click(screen.getByRole('button', { name: 'Crear' }))

    expect(await screen.findByRole('link', { name: 'Soporte' })).toBeInTheDocument()
    expect(screen.getByLabelText('Crear grupo')).toHaveValue('')
    const post = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST')!
    expect(JSON.parse(post[1]!.body as string)).toEqual({ name: 'Soporte' })
  })

  it('muestra el error si el nombre ya existe (409)', async () => {
    mockApi({
      [`GET ${GROUPS_URL}`]: () => Response.json([VENTAS]),
      [`POST ${GROUPS_URL}`]: () => Response.json({ status: 409 }, { status: 409 }),
    })
    const { user } = await renderWithRouter(<GroupsList orgId="o1" myRole="OWNER" />)

    await user.type(await screen.findByLabelText('Crear grupo'), 'ventas')
    await user.click(screen.getByRole('button', { name: 'Crear' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Ya hay un grupo con ese nombre')
    // El texto se conserva para corregirlo
    await waitFor(() => expect(screen.getByLabelText('Crear grupo')).toHaveValue('ventas'))
  })

  it('muestra el error si el nombre no es válido (400)', async () => {
    mockApi({
      [`GET ${GROUPS_URL}`]: () => Response.json([]),
      [`POST ${GROUPS_URL}`]: () => Response.json({ status: 400 }, { status: 400 }),
    })
    const { user } = await renderWithRouter(<GroupsList orgId="o1" myRole="OWNER" />)

    await user.type(await screen.findByLabelText('Crear grupo'), 'x')
    await user.click(screen.getByRole('button', { name: 'Crear' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'El nombre debe tener entre 1 y 60 caracteres',
    )
  })
})
