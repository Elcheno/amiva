import { screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { renderWithProviders } from '@/test/render'
import type { Invitation } from './api'
import { PendingInvitations } from './pending-invitations'

const DANI: Invitation = {
  invitationId: 'i1',
  userId: 'u4',
  username: 'zorro-veloz-7',
  displayName: 'Dani',
  role: 'ADMIN',
  invitedByName: 'Ana',
  createdAt: '2026-10-27T10:00:00Z',
  updatedAt: '2026-10-28T10:00:00Z',
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('PendingInvitations', () => {
  it('muestra nombre, @username, rol, quién invitó y fecha', async () => {
    const fetchMock = vi.fn().mockResolvedValue(Response.json([DANI]))
    vi.stubGlobal('fetch', fetchMock)
    renderWithProviders(<PendingInvitations orgId="o1" />)

    const row = (await screen.findByText('Dani')).closest('tr')!
    expect(within(row).getByText('@zorro-veloz-7')).toBeInTheDocument()
    expect(within(row).getByText('Admin')).toBeInTheDocument()
    expect(within(row).getByText('Ana')).toBeInTheDocument()
    expect(within(row).getByText(/2026/)).toBeInTheDocument()
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/orgs/o1/invitations')
  })

  it('avisa si no hay invitaciones pendientes', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(Response.json([])))
    renderWithProviders(<PendingInvitations orgId="o1" />)

    expect(await screen.findByText('No hay invitaciones pendientes')).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })
})
