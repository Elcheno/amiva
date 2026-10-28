import { screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { renderWithProviders } from '@/test/render'
import type { Member } from './api'
import { MembersList } from './members-list'

const ANA: Member = {
  userId: 'u1',
  username: 'nutria-dormilona-42',
  displayName: 'Ana',
  email: 'ana@example.com',
  role: 'OWNER',
  createdAt: '2026-10-01T10:00:00Z',
}
const BEA: Member = {
  userId: 'u2',
  username: 'pinguino-despistado-17',
  displayName: 'Bea',
  email: 'bea@example.com',
  role: 'MEMBER',
  createdAt: '2026-10-02T10:00:00Z',
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('MembersList', () => {
  it('muestra nombre, @username, email y rol de cada miembro', async () => {
    const fetchMock = vi.fn().mockResolvedValue(Response.json([ANA, BEA]))
    vi.stubGlobal('fetch', fetchMock)
    renderWithProviders(<MembersList orgId="o1" />)

    const anaRow = (await screen.findByText('Ana')).closest('tr')!
    expect(within(anaRow).getByText('@nutria-dormilona-42')).toBeInTheDocument()
    expect(within(anaRow).getByText('ana@example.com')).toBeInTheDocument()
    expect(within(anaRow).getByText('Propietario')).toBeInTheDocument()

    const beaRow = screen.getByText('Bea').closest('tr')!
    expect(within(beaRow).getByText('@pinguino-despistado-17')).toBeInTheDocument()
    expect(within(beaRow).getByText('bea@example.com')).toBeInTheDocument()

    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/orgs/o1/members')
  })
})
