import { queryOptions, useMutation, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '@/lib/api'

export interface RegisterRequest {
  email: string
  password: string
  displayName: string
  timezone?: string
}

// Usuario con sesión iniciada (mismo cuerpo en /me, /login y /register)
export interface CurrentUser {
  userId: string
  email: string
  username: string
  displayName: string
  orgId: string
  orgName: string
  emailVerified: boolean
}

export type RegisterResponse = CurrentUser

export interface LoginRequest {
  email: string
  password: string
}

export function register(body: RegisterRequest) {
  return api<RegisterResponse>('/auth/register', { method: 'POST', body })
}

export function useRegister() {
  return useMutation({ mutationFn: register })
}

export function login(body: LoginRequest) {
  return api<CurrentUser>('/auth/login', { method: 'POST', body })
}

export function logout() {
  return api<void>('/auth/logout', { method: 'POST' })
}

// Sin sesión (401) devuelve null en lugar de lanzar error
export async function fetchMe(): Promise<CurrentUser | null> {
  try {
    return await api<CurrentUser>('/auth/me')
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return null
    throw error
  }
}

// Reutilizable en beforeLoad con queryClient.ensureQueryData
export const meQueryOptions = queryOptions({
  queryKey: ['auth', 'me'],
  queryFn: fetchMe,
})

export function useLogin() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: login,
    // Las rutas privadas leen el usuario de la caché sin volver a pedir /me
    onSuccess: (user) => queryClient.setQueryData(meQueryOptions.queryKey, user),
  })
}

export function useLogout() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: logout,
    // Nada de la sesión anterior debe quedar en caché
    onSuccess: () => queryClient.clear(),
  })
}

// Token del enlace del email. 400 con problem.reason: INVALID, EXPIRED o USED
export function verifyEmail(token: string) {
  return api<void>('/auth/verify-email', { method: 'POST', body: { token } })
}

export function useVerifyEmail() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: verifyEmail,
    // Si hay sesión, el aviso de verificar el email debe desaparecer
    onSuccess: () => queryClient.invalidateQueries({ queryKey: meQueryOptions.queryKey }),
  })
}

// 429 si no ha pasado un minuto desde el último; 409 si ya está verificado
export function resendVerification() {
  return api<void>('/auth/verify-email/resend', { method: 'POST' })
}

export function useResendVerification() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: resendVerification,
    onError: (error) => {
      // Ya verificado (p. ej. en otra pestaña): se refresca /me y el aviso se va
      if (error instanceof ApiError && error.status === 409) {
        return queryClient.invalidateQueries({ queryKey: meQueryOptions.queryKey })
      }
    },
  })
}
