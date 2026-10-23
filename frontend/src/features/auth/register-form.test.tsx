import { screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { renderWithRouter } from '@/test/render'
import { RegisterForm } from './register-form'

function mockFetch(response: Response) {
  const fetchMock = vi.fn().mockResolvedValue(response)
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

async function fillForm(user: Awaited<ReturnType<typeof renderWithRouter>>['user']) {
  await user.type(await screen.findByLabelText('Nombre'), '  Ana  ')
  await user.type(screen.getByLabelText('Email'), 'ana@example.com')
  await user.type(screen.getByLabelText('Contraseña'), 'secreta123')
  await user.click(screen.getByRole('button', { name: 'Crear cuenta' }))
}

beforeEach(() => {
  // Con la cookie ya presente no hace falta pedirla a /api/auth/csrf
  document.cookie = 'XSRF-TOKEN=token; path=/'
})

afterEach(() => {
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
})

describe('RegisterForm', () => {
  it('valida en vivo y no envía si hay errores', async () => {
    const fetchMock = mockFetch(Response.json({}))
    const { user } = await renderWithRouter(<RegisterForm />, { initialPath: '/register' })

    await user.type(await screen.findByLabelText('Email'), 'no-es-email')
    await user.type(screen.getByLabelText('Contraseña'), 'corta')
    await user.tab()

    expect(screen.getByText('El email no es válido')).toBeInTheDocument()
    expect(screen.getByText('Mínimo 8 caracteres')).toBeInTheDocument()
    expect(screen.queryByText('Escribe tu nombre')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Crear cuenta' }))

    expect(screen.getByText('Escribe tu nombre')).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('envía el registro con la zona horaria y muestra la confirmación', async () => {
    const fetchMock = mockFetch(
      Response.json(
        {
          userId: 'u1',
          email: 'ana@example.com',
          displayName: 'Ana',
          orgId: 'o1',
          orgName: 'Org de Ana',
        },
        { status: 201 },
      ),
    )
    const { user } = await renderWithRouter(<RegisterForm />, { initialPath: '/register' })

    await fillForm(user)

    expect(await screen.findByText('Cuenta creada')).toBeInTheDocument()
    expect(screen.getByText(/Org de Ana/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Iniciar sesión' })).toHaveAttribute('href', '/login')
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/auth/register')
    expect(JSON.parse(init.body as string)).toEqual({
      displayName: 'Ana',
      email: 'ana@example.com',
      password: 'secreta123',
      timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
    })
  })

  it('muestra el aviso de email duplicado en 409', async () => {
    mockFetch(
      Response.json({ status: 409, detail: 'Ese email ya está registrado' }, { status: 409 }),
    )
    const { user } = await renderWithRouter(<RegisterForm />, { initialPath: '/register' })

    await fillForm(user)

    expect(await screen.findByText('Ese email ya está registrado')).toBeInTheDocument()
    expect(screen.getByLabelText('Email')).toHaveAttribute('aria-invalid', 'true')

    await user.type(screen.getByLabelText('Email'), 'x')
    expect(screen.queryByText('Ese email ya está registrado')).not.toBeInTheDocument()
  })

  it('muestra los errores por campo del servidor en 400', async () => {
    mockFetch(
      Response.json(
        { status: 400, errors: { email: 'debe ser una dirección de correo bien formada' } },
        { status: 400 },
      ),
    )
    const { user } = await renderWithRouter(<RegisterForm />, { initialPath: '/register' })

    await fillForm(user)

    expect(
      await screen.findByText('debe ser una dirección de correo bien formada'),
    ).toBeInTheDocument()
  })
})
