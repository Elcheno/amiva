import { defineConfig } from '@lingui/cli'

export default defineConfig({
  sourceLocale: 'es',
  locales: ['es'],
  catalogs: [
    {
      path: '<rootDir>/src/locales/{locale}/messages',
      include: ['src'],
      exclude: ['**/*.test.*', 'src/test/**'],
    },
  ],
})
