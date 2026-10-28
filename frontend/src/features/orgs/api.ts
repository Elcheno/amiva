import { queryOptions, useMutation, useQueryClient } from '@tanstack/react-query'
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
  username: string
  displayName: string
  email: string
  role: OrgRole
  createdAt: string
}

// Rol con el que se puede invitar (OWNER no se asigna)
export type InviteRole = Exclude<OrgRole, 'OWNER'>

// Invitación pendiente. invitedByName: null si quien invitó ya no existe
export interface Invitation {
  invitationId: string
  userId: string
  username: string
  displayName: string
  role: InviteRole
  invitedByName: string | null
  createdAt: string
  updatedAt: string
}

// Resultado de la búsqueda para invitar (sin email)
export interface InvitableUser {
  userId: string
  username: string
  displayName: string
  pendingInvitation: boolean
}

export interface InviteRequest {
  userId: string
  role: InviteRole
}

// Mínimo de caracteres para buscar (con menos, el backend devuelve [])
export const INVITE_SEARCH_MIN_LENGTH = 3

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

// Pendientes, más recientes primero. Solo OWNER y ADMIN (403 al resto)
export function invitationsQueryOptions(orgId: string) {
  return queryOptions({
    queryKey: ['orgs', orgId, 'invitations'],
    queryFn: () => api<Invitation[]>(`/orgs/${orgId}/invitations`),
  })
}

// Solo consulta con q de al menos INVITE_SEARCH_MIN_LENGTH caracteres
export function invitableUsersQueryOptions(orgId: string, q: string) {
  const query = q.trim()
  return queryOptions({
    queryKey: ['orgs', orgId, 'invitable-users', query],
    queryFn: () =>
      api<InvitableUser[]>(`/orgs/${orgId}/invitable-users?q=${encodeURIComponent(query)}`),
    // Por caracteres, no unidades UTF-16, igual que el backend
    enabled: [...query].length >= INVITE_SEARCH_MIN_LENGTH,
  })
}

// 403 sin permiso (o ADMIN invitando como Admin), 409 ya es miembro, 503 no se pudo enviar el email
export function useInviteMember(orgId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: InviteRequest) =>
      api<Invitation>(`/orgs/${orgId}/invitations`, { method: 'POST', body }),
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: ['orgs', orgId, 'invitations'] }),
        queryClient.invalidateQueries({ queryKey: ['orgs', orgId, 'invitable-users'] }),
      ]),
  })
}

// Invitación pendiente recibida por el usuario de la sesión. invitedByName: null si quien invitó ya no existe
export interface ReceivedInvitation {
  id: string
  organizationId: string
  organizationName: string
  role: InviteRole
  invitedByName: string | null
  createdAt: string
  updatedAt: string
}

// Mis invitaciones pendientes, más recientes primero
export const myInvitationsQueryOptions = queryOptions({
  queryKey: ['me', 'invitations'],
  queryFn: () => api<ReceivedInvitation[]>('/me/invitations'),
})

// Token del enlace del email (va en el body, nunca en la URL de la API).
// 404 si no es para mi cuenta, ya no está pendiente o el token se reemplazó con un reenvío
export function invitationByTokenQueryOptions(token: string) {
  return queryOptions({
    queryKey: ['invitations', 'resolve', token],
    queryFn: () =>
      api<ReceivedInvitation>('/invitations/resolve', { method: 'POST', body: { token } }),
  })
}

// Devuelve la organización a la que se entra. 403 con reason EMAIL_NOT_VERIFIED,
// 404 no disponible, 409 ya es miembro
export function useAcceptInvitation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (invitationId: string) =>
      api<OrgSummary>(`/invitations/${invitationId}/accept`, { method: 'POST' }),
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: myInvitationsQueryOptions.queryKey }),
        queryClient.invalidateQueries({ queryKey: orgsQueryOptions.queryKey }),
      ]),
  })
}

// 403 con reason EMAIL_NOT_VERIFIED, 404 no disponible
export function useDeclineInvitation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (invitationId: string) =>
      api<void>(`/invitations/${invitationId}/decline`, { method: 'POST' }),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: myInvitationsQueryOptions.queryKey }),
  })
}
