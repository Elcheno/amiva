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

export async function api<T>(path: string, init: ApiInit = {}): Promise<T> {
  const { body, headers, ...rest } = init
  const isJson = body !== undefined && !(body instanceof FormData)

  const res = await fetch(BASE + path, {
    credentials: 'same-origin', // sesión en cookie
    ...rest,
    headers: {
      Accept: 'application/json',
      ...(isJson ? { 'Content-Type': 'application/json' } : {}),
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
