import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { I18nProvider } from '@lingui/react'
import { QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider, createRouter } from '@tanstack/react-router'
import { activateLocale, defaultLocale, i18n } from '@/lib/i18n'
import { queryClient } from '@/lib/query-client'
import { routeTree } from './routeTree.gen'
import './index.css'

const router = createRouter({
  routeTree,
  context: { queryClient },
  defaultPreload: 'intent', // precarga al pasar el ratón por un Link
  defaultPreloadStaleTime: 0, // la caché la gestiona TanStack Query
})

// Registra el router para que rutas, params y search tengan tipos en toda la app
declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router
  }
}

await activateLocale(defaultLocale)

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <I18nProvider i18n={i18n}>
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </I18nProvider>
  </StrictMode>,
)
