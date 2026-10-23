import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'
import { activateLocale, defaultLocale } from '@/lib/i18n'

await activateLocale(defaultLocale)

afterEach(() => {
  cleanup()
})
