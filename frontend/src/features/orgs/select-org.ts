import type { OrgSummary } from './api'

// Última organización abierta, para volver a ella al entrar en /
export const LAST_ORG_KEY = 'amiva.lastOrgId'

// localStorage puede no estar disponible (modo privado, bloqueado): se ignora
export function readLastOrgId(): string | undefined {
  try {
    return localStorage.getItem(LAST_ORG_KEY) ?? undefined
  } catch {
    return undefined
  }
}

export function saveLastOrgId(orgId: string) {
  try {
    localStorage.setItem(LAST_ORG_KEY, orgId)
  } catch {
    // Sin almacenamiento solo se pierde el recuerdo de la última org
  }
}

// Org a la que ir desde /: la última usada si sigo siendo miembro; si no, la propia
// (o la primera, por si no hubiera propia). undefined si no hay ninguna
export function pickTargetOrg(orgs: OrgSummary[], lastOrgId?: string): string | undefined {
  if (lastOrgId && orgs.some((o) => o.orgId === lastOrgId)) return lastOrgId
  return (orgs.find((o) => o.own) ?? orgs[0])?.orgId
}

// Separa "Mi organización" de "Donde participo", conservando el orden recibido
export function groupOrgs(orgs: OrgSummary[]): {
  own: OrgSummary | undefined
  participating: OrgSummary[]
} {
  return {
    own: orgs.find((o) => o.own),
    participating: orgs.filter((o) => !o.own),
  }
}
