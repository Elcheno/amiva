import { screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { renderWithRouter } from '@/test/render'
import { meQueryOptions } from './api'
import { LoginForm } from './login-form'

const USER = {
  userId: 'u1',
  email: 'ana@example.com',
  displayName: 'Ana',
  orgId: 'o1',
  orgName: 'Org de Ana',
}

function mockFetch(response: Response) {
  const fetchMock = vi.fn().mockResolvedValue(response)
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

async function fillForm(user: Awaited<ReturnType<typeof renderWithRouter>>['user']) {
  await user.type(await screen.findByLabelText('Email'), ' ana@example.com ')
  await user.type(screen.getByLabelText('Contraseña'), 'secreta123')
  await user.click(screen.getByRole('button', { name: 'Iniciar sesión' }))
}

beforeEach(() => {
  // Con la cookie ya presente no hace falta pedirla a /api/auth/csrf
  document.cookie = 'XSRF-TOKEN=token; path=/'
})

afterEach(() => {
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
})

describe('LoginForm', () => {
  it('valida en vivo y no envía si hay errores', async () => {
    const fetchMock = mockFetch(Response.json({}))
    const { user } = await renderWithRouter(<LoginForm />, { initialPath: '/login' })

    await user.type(await screen.findByLabelText('Email'), 'no-es-email')
    await user.tab()

    expect(screen.getByText('El email no es válido')).toBeInTheDocument()
    expect(screen.queryByText('Escribe tu contraseña')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Iniciar sesión' }))

    expect(screen.getByText('Escribe tu contraseña')).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('inicia sesión, guarda el usuario en caché y vuelve a la ruta pedida', async () => {
    const fetchMock = mockFetch(Response.json(USER))
    const { user, router, queryClient } = await renderWithRouter(
      <LoginForm redirectTo="/forms/1?tab=a" />,
      { initialPath: '/login' },
    )

    await fillForm(user)

    await waitFor(() => expect(router.state.location.href).toBe('/forms/1?tab=a'))
    expect(queryClient.getQueryData(meQueryOptions.queryKey)).toEqual(USER)
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/auth/login')
    expect(JSON.parse(init.body as string)).toEqual({
      email: 'ana@example.com',
      password: 'secreta123',
    })
  })

  it('ignora un redirect externo y va a /', async () => {
    mockFetch(Response.json(USER))
    const { user, router } = await renderWithRouter(<LoginForm redirectTo="//evil.com" />, {
      initialPath: '/login',
    })

    await fillForm(user)

    await waitFor(() => expect(router.state.location.href).toBe('/'))
  })

  it('muestra un aviso genérico en 401 y no navega', async () => {
    mockFetch(
      Response.json({ status: 401, detail: 'Email o contraseña incorrectos' }, { status: 401 }),
    )
    const { user, router } = await renderWithRouter(<LoginForm />, { initialPath: '/login' })

    await fillForm(user)

    expect(await screen.findByText('Email o contraseña incorrectos')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/login')
  })

  it('muestra los errores por campo del servidor en 400', async () => {
    mockFetch(
      Response.json(
        { status: 400, errors: { password: 'no puede ocupar más de 72 bytes' } },
        { status: 400 },
      ),
    )
    const { user } = await renderWithRouter(<LoginForm />, { initialPath: '/login' })

    await fillForm(user)

    expect(await screen.findByText('no puede ocupar más de 72 bytes')).toBeInTheDocument()
  })

  it('enlaza con el registro', async () => {
    await renderWithRouter(<LoginForm />, { initialPath: '/login' })

    expect(await screen.findByRole('link', { name: 'Regístrate' })).toHaveAttribute(
      'href',
      '/register',
    )
  })
})
