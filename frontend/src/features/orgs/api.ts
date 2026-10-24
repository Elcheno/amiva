import { queryOptions } from '@tanstack/react-query'
import { api } from '@/lib/api'

export type OrgRole = 'OWNER' | 'ADMIN' | 'MEMBER'

// Organización donde el usuario es miembro; own = es la suya propia (rol OWNER)
export interface OrgSummary {
  orgId: string
  name: string
  role: OrgRole
  own: boolean
}

export interface Member {
  userId: string
  displayName: string
  email: string
  role: OrgRole
  createdAt: string
}

// La propia primero, luego por nombre (lo ordena el backend)
export const orgsQueryOptions = queryOptions({
  queryKey: ['orgs'],
  queryFn: () => api<OrgSummary[]>('/orgs'),
})

// 404 si no existe o no eres miembro
export function orgQueryOptions(orgId: string) {
  return queryOptions({
    queryKey: ['orgs', orgId],
    queryFn: () => api<OrgSummary>(`/orgs/${orgId}`),
  })
}

export function membersQueryOptions(orgId: string) {
  return queryOptions({
    queryKey: ['orgs', orgId, 'members'],
    queryFn: () => api<Member[]>(`/orgs/${orgId}/members`),
  })
}
