// Catálogos .po compilados por @lingui/vite-plugin
declare module '*.po' {
  import type { Messages } from '@lingui/core'
  export const messages: Messages
}
