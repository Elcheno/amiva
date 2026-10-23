import type { ReactElement } from 'react'
import { I18nProvider } from '@lingui/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import {
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
  RouterProvider,
} from '@tanstack/react-router'
import { render } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { i18n } from '@/lib/i18n'

function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
}

// Render con los providers de la app. QueryClient nuevo por test, sin reintentos
export function renderWithProviders(ui: ReactElement) {
  const queryClient = createTestQueryClient()
  return {
    user: userEvent.setup(),
    queryClient,
    ...render(
      <I18nProvider i18n={i18n}>
        <QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>
      </I18nProvider>,
    ),
  }
}

// Como renderWithProviders, pero dentro de un router en memoria para componentes con
// Link o useNavigate. El componente se monta en initialPath; cualquier otra ruta queda vacía
export async function renderWithRouter(ui: ReactElement, { initialPath = '/' } = {}) {
  const queryClient = createTestQueryClient()
  const rootRoute = createRootRoute()
  const path = new URL(initialPath, 'http://localhost').pathname
  const routeTree = rootRoute.addChildren([
    createRoute({ getParentRoute: () => rootRoute, path, component: () => ui }),
    createRoute({ getParentRoute: () => rootRoute, path: '$', component: () => null }),
  ])
  const router = createRouter({
    routeTree,
    history: createMemoryHistory({ initialEntries: [initialPath] }),
  })
  await router.load()
  return {
    user: userEvent.setup(),
    queryClient,
    router,
    ...render(
      <I18nProvider i18n={i18n}>
        <QueryClientProvider client={queryClient}>
          <RouterProvider router={router} />
        </QueryClientProvider>
      </I18nProvider>,
    ),
  }
}
