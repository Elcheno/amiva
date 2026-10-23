import { createFileRoute, Outlet, redirect } from '@tanstack/react-router'
import { meQueryOptions } from '@/features/auth/api'

// Layout de las rutas privadas: sin sesión, a /login recordando a dónde se iba
export const Route = createFileRoute('/_auth')({
  beforeLoad: async ({ context, location }) => {
    const me = await context.queryClient.ensureQueryData(meQueryOptions)
    if (!me) throw redirect({ to: '/login', search: { redirect: location.href } })
    return { me }
  },
  component: Outlet,
})
