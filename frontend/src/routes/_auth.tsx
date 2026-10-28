import { createFileRoute, Outlet, redirect } from '@tanstack/react-router'
import { meQueryOptions } from '@/features/auth/api'
import { VerifyEmailBanner } from '@/features/auth/verify-email-banner'

// Layout de las rutas privadas: sin sesión, a /login recordando a dónde se iba
export const Route = createFileRoute('/_auth')({
  beforeLoad: async ({ context, location }) => {
    const me = await context.queryClient.ensureQueryData(meQueryOptions)
    if (!me) throw redirect({ to: '/login', search: { redirect: location.href } })
    return { me }
  },
  component: AuthLayout,
})

// El aviso de verificar el email va encima de cualquier página privada
function AuthLayout() {
  return (
    <>
      <VerifyEmailBanner />
      <Outlet />
    </>
  )
}
