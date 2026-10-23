import { describe, expect, it } from 'vitest'
import { safeRedirect } from './redirect'

describe('safeRedirect', () => {
  it('acepta rutas internas con search', () => {
    expect(safeRedirect('/forms/1?tab=a')).toBe('/forms/1?tab=a')
  })

  it.each([undefined, 42, '', 'forms', 'https://evil.com', '//evil.com', '/\\evil.com'])(
    'manda a / si el destino no es interno (%s)',
    (target) => {
      expect(safeRedirect(target)).toBe('/')
    },
  )
})
