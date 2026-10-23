import { Trans } from '@lingui/react/macro'
import { screen } from '@testing-library/react'
import { expect, it, vi } from 'vitest'
import { Button } from '@/components/ui/button'
import { renderWithProviders } from '@/test/render'

// Comprueba que macros de Lingui, providers y jest-dom funcionan en los tests
it('renderiza texto traducido y responde al click', async () => {
  const onClick = vi.fn()
  const { user } = renderWithProviders(
    <Button onClick={onClick}>
      <Trans>Empezar</Trans>
    </Button>,
  )

  await user.click(screen.getByRole('button', { name: 'Empezar' }))

  expect(onClick).toHaveBeenCalledOnce()
})
