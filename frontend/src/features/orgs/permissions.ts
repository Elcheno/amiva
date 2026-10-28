import type { OrgRole } from './api'

// Reflejo en el front de OrgRole.has del backend, solo para mostrar u ocultar acciones.
// El backend vuelve a comprobarlo siempre (403)
export function canInvite(role: OrgRole) {
  return role === 'OWNER' || role === 'ADMIN'
}

// Invitar como Admin exige poder cambiar roles: solo el OWNER
export function canInviteAsAdmin(role: OrgRole) {
  return role === 'OWNER'
}
