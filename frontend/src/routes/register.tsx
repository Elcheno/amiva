import { createFileRoute, redirect } from '@tanstack/react-router'
import { meQueryOptions } from '@/features/auth/api'
import { RegisterForm } from '@/features/auth/register-form'

export const Route = createFileRoute('/register')({
  // Con sesión ya iniciada no tiene sentido el registro
  beforeLoad: async ({ context }) => {
    if (await context.queryClient.ensureQueryData(meQueryOptions)) throw redirect({ to: '/' })
  },
  component: RegisterPage,
})

function RegisterPage() {
  return (
    <main className="flex min-h-svh items-start justify-center p-4 sm:items-center sm:p-6">
      <RegisterForm />
    </main>
  )
}
