// Cliente HTTP mínimo para el backend. Todas las rutas cuelgan de /api
// (en dev, el proxy de Vite las manda a Spring en :8080).

const BASE = '/api'

// Error con el cuerpo ProblemDetail (RFC 9457) que devuelve Spring, si lo hay
export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
  [key: string]: unknown
}

export class ApiError extends Error {
  readonly status: number
  readonly problem: ProblemDetail | undefined

  constructor(status: number, problem?: ProblemDetail) {
    super(problem?.detail ?? problem?.title ?? `HTTP ${status}`)
    this.name = 'ApiError'
    this.status = status
    this.problem = problem
  }
}

type ApiInit = Omit<RequestInit, 'body'> & { body?: unknown }

// CSRF (Spring Security, modo SPA): el token llega en la cookie XSRF-TOKEN y se
// reenvía en la cabecera X-XSRF-TOKEN en toda petición que modifica datos
const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'
const SAFE_METHODS = ['GET', 'HEAD', 'OPTIONS', 'TRACE']

function readCookie(name: string): string | undefined {
  const prefix = `${name}=`
  const cookie = document.cookie.split('; ').find((c) => c.startsWith(prefix))
  return cookie ? decodeURIComponent(cookie.slice(prefix.length)) : undefined
}

// Si aún no hay cookie, la pide al backend (GET /api/auth/csrf la emite)
async function csrfToken(): Promise<string | undefined> {
  const token = readCookie(CSRF_COOKIE)
  if (token) return token
  await fetch(`${BASE}/auth/csrf`, { credentials: 'same-origin' })
  return readCookie(CSRF_COOKIE)
}

export async function api<T>(path: string, init: ApiInit = {}): Promise<T> {
  const { body, headers, ...rest } = init
  const isJson = body !== undefined && !(body instanceof FormData)
  const method = (rest.method ?? 'GET').toUpperCase()
  const token = SAFE_METHODS.includes(method) ? undefined : await csrfToken()

  const res = await fetch(BASE + path, {
    credentials: 'same-origin', // sesión en cookie
    ...rest,
    headers: {
      Accept: 'application/json',
      ...(isJson ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { [CSRF_HEADER]: token } : {}),
      ...headers,
    },
    body: isJson ? JSON.stringify(body) : (body as BodyInit | undefined),
  })

  if (!res.ok) {
    const problem = await res.json().catch(() => undefined)
    throw new ApiError(res.status, problem as ProblemDetail | undefined)
  }
  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}
