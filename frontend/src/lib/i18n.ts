import { i18n } from '@lingui/core'

export const locales = ['es'] as const
export type Locale = (typeof locales)[number]
export const defaultLocale: Locale = 'es'

// Carga el catálogo del idioma bajo demanda (un chunk por idioma)
export async function activateLocale(locale: Locale) {
  const { messages } = await import(`../locales/${locale}/messages.po`)
  i18n.load(locale, messages)
  i18n.activate(locale)
  document.documentElement.lang = locale
}

export { i18n }
