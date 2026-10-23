import { useMutation } from '@tanstack/react-query'
import { api } from '@/lib/api'

export interface RegisterRequest {
  email: string
  password: string
  displayName: string
  timezone?: string
}

export interface RegisterResponse {
  userId: string
  email: string
  displayName: string
  orgId: string
  orgName: string
}

export function register(body: RegisterRequest) {
  return api<RegisterResponse>('/auth/register', { method: 'POST', body })
}

export function useRegister() {
  return useMutation({ mutationFn: register })
}
