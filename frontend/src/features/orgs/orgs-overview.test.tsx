import { screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { renderWithRouter } from '@/test/render'
import type { OrgSummary } from './api'
import { OrgsOverview } from './orgs-overview'

const OWN: OrgSummary = { orgId: 'o1', name: 'Org de Ana', role: 'OWNER', own: true }
const CLUB: OrgSummary = { orgId: 'o2', name: 'Club', role: 'ADMIN', own: false }
const TEAM: OrgSummary = { orgId: 'o3', name: 'Equipo', role: 'MEMBER', own: false }

function mockFetch(response: Response) {
  const fetchMock = vi.fn().mockResolvedValue(response)
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('OrgsOverview', () => {
  it('separa mi organización de donde participo, con su rol y enlace', async () => {
    const fetchMock = mockFetch(Response.json([OWN, CLUB, TEAM]))
    await renderWithRouter(<OrgsOverview />, { initialPath: '/orgs' })

    const own = await screen.findByRole('region', { name: 'Mi organización' })
    expect(within(own).getByRole('link', { name: /Org de Ana/ })).toHaveAttribute(
      'href',
      '/orgs/o1',
    )
    expect(within(own).getByText('Propietario')).toBeInTheDocument()

    const participating = screen.getByRole('region', { name: 'Donde participo' })
    const links = within(participating).getAllByRole('link')
    expect(links.map((l) => l.getAttribute('href'))).toEqual(['/orgs/o2', '/orgs/o3'])
    expect(within(participating).getByText('Admin')).toBeInTheDocument()
    expect(within(participating).getByText('Miembro')).toBeInTheDocument()

    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/orgs')
  })

  it('avisa si aún no participo en ninguna', async () => {
    mockFetch(Response.json([OWN]))
    await renderWithRouter(<OrgsOverview />, { initialPath: '/orgs' })

    const participating = await screen.findByRole('region', { name: 'Donde participo' })
    expect(
      within(participating).getByText('Aún no participas en ninguna organización'),
    ).toBeInTheDocument()
    expect(within(participating).queryByRole('link')).not.toBeInTheDocument()
  })
})
