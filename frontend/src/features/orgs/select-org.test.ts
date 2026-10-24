import { afterEach, describe, expect, it } from 'vitest'
import type { OrgSummary } from './api'
import { groupOrgs, LAST_ORG_KEY, pickTargetOrg, readLastOrgId, saveLastOrgId } from './select-org'

const OWN: OrgSummary = { orgId: 'o1', name: 'Org de Ana', role: 'OWNER', own: true }
const CLUB: OrgSummary = { orgId: 'o2', name: 'Club', role: 'ADMIN', own: false }
const TEAM: OrgSummary = { orgId: 'o3', name: 'Equipo', role: 'MEMBER', own: false }

afterEach(() => {
  localStorage.clear()
})

describe('pickTargetOrg', () => {
  it('vuelve a la última org si sigo siendo miembro', () => {
    expect(pickTargetOrg([OWN, CLUB], 'o2')).toBe('o2')
  })

  it('va a la propia si la última ya no está en mi lista', () => {
    expect(pickTargetOrg([OWN, CLUB], 'otra')).toBe('o1')
  })

  it('va a la propia si no hay última org', () => {
    expect(pickTargetOrg([CLUB, OWN])).toBe('o1')
  })

  it('sin propia usa la primera, y sin orgs no hay destino', () => {
    expect(pickTargetOrg([CLUB, TEAM])).toBe('o2')
    expect(pickTargetOrg([], 'o1')).toBeUndefined()
  })
})

describe('groupOrgs', () => {
  it('separa la propia de las demás conservando el orden', () => {
    expect(groupOrgs([OWN, CLUB, TEAM])).toEqual({ own: OWN, participating: [CLUB, TEAM] })
  })

  it('sin otras orgs, participo en ninguna', () => {
    expect(groupOrgs([OWN])).toEqual({ own: OWN, participating: [] })
  })
})

describe('lastOrgId', () => {
  it('guarda y lee la última org en localStorage', () => {
    expect(readLastOrgId()).toBeUndefined()
    saveLastOrgId('o2')
    expect(localStorage.getItem(LAST_ORG_KEY)).toBe('o2')
    expect(readLastOrgId()).toBe('o2')
  })
})
