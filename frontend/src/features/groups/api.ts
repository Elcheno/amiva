import { queryOptions, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type { OrgRole } from '@/features/orgs/api'

export interface GroupSummary {
  id: string
  name: string
  memberCount: number
}

export interface GroupMember {
  userId: string
  displayName: string
  username: string
  role: OrgRole
  addedAt: string
}

export interface GroupDetail {
  id: string
  name: string
  members: GroupMember[]
}

// Por nombre sin distinguir mayúsculas (lo ordena el backend). Cualquier miembro puede verlos
export function groupsQueryOptions(orgId: string) {
  return queryOptions({
    queryKey: ['orgs', orgId, 'groups'],
    queryFn: () => api<GroupSummary[]>(`/orgs/${orgId}/groups`),
  })
}

// Miembros por nombre. 404 si el grupo no existe o es de otra organización
export function groupQueryOptions(orgId: string, groupId: string) {
  return queryOptions({
    queryKey: ['orgs', orgId, 'groups', groupId],
    queryFn: () => api<GroupDetail>(`/orgs/${orgId}/groups/${groupId}`),
  })
}

// La lista y el detalle comparten prefijo de clave: invalidarlo refresca ambos
function useInvalidateGroups(orgId: string) {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: ['orgs', orgId, 'groups'] })
}

// 400 nombre vacío o de más de 60 caracteres, 403 sin permiso, 409 nombre repetido
export function useCreateGroup(orgId: string) {
  const invalidate = useInvalidateGroups(orgId)
  return useMutation({
    mutationFn: (name: string) =>
      api<GroupSummary>(`/orgs/${orgId}/groups`, { method: 'POST', body: { name } }),
    onSuccess: invalidate,
  })
}

// Mismos errores que al crear
export function useRenameGroup(orgId: string, groupId: string) {
  const invalidate = useInvalidateGroups(orgId)
  return useMutation({
    mutationFn: (name: string) =>
      api<GroupSummary>(`/orgs/${orgId}/groups/${groupId}`, { method: 'PATCH', body: { name } }),
    onSuccess: invalidate,
  })
}

// Se borra aunque tenga miembros. El detalle se descarta (volvería a dar 404), la lista se refresca
export function useDeleteGroup(orgId: string, groupId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => api<void>(`/orgs/${orgId}/groups/${groupId}`, { method: 'DELETE' }),
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: groupQueryOptions(orgId, groupId).queryKey })
      return queryClient.invalidateQueries({ queryKey: groupsQueryOptions(orgId).queryKey })
    },
  })
}

// 400 no es miembro de la organización, 409 ya está en el grupo
export function useAddGroupMember(orgId: string, groupId: string) {
  const invalidate = useInvalidateGroups(orgId)
  return useMutation({
    mutationFn: (userId: string) =>
      api<GroupMember>(`/orgs/${orgId}/groups/${groupId}/members`, {
        method: 'POST',
        body: { userId },
      }),
    onSuccess: invalidate,
  })
}

// 404 si ya no estaba en el grupo
export function useRemoveGroupMember(orgId: string, groupId: string) {
  const invalidate = useInvalidateGroups(orgId)
  return useMutation({
    mutationFn: (userId: string) =>
      api<void>(`/orgs/${orgId}/groups/${groupId}/members/${userId}`, { method: 'DELETE' }),
    onSuccess: invalidate,
  })
}
