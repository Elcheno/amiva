import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { renderWithRouter } from '@/test/render'
import { OrgHeader } from './org-header'

describe('OrgHeader', () => {
  it('muestra el nombre y el @username del usuario', async () => {
    await renderWithRouter(<OrgHeader userName="Ana" username="nutria-dormilona-42" />, {
      initialPath: '/orgs',
    })

    expect(await screen.findByText('@nutria-dormilona-42')).toBeInTheDocument()
    expect(screen.getByText(/Ana/)).toBeInTheDocument()
  })
})
