import { createFileRoute, redirect } from '@tanstack/react-router'
import { meQueryOptions } from '@/features/auth/api'
import { LoginForm } from '@/features/auth/login-form'

interface LoginSearch {
  redirect?: string
}

export const Route = createFileRoute('/login')({
  // El destino se valida en LoginForm (safeRedirect): aquí solo se exige que sea texto
  validateSearch: (search): LoginSearch =>
    typeof search.redirect === 'string' ? { redirect: search.redirect } : {},
  // Con sesión ya iniciada no tiene sentido el login
  beforeLoad: async ({ context }) => {
    if (await context.queryClient.ensureQueryData(meQueryOptions)) throw redirect({ to: '/' })
  },
  component: LoginPage,
})

function LoginPage() {
  const { redirect: redirectTo } = Route.useSearch()
  return (
    <main className="flex min-h-svh items-start justify-center p-4 sm:items-center sm:p-6">
      <LoginForm redirectTo={redirectTo} />
    </main>
  )
}
