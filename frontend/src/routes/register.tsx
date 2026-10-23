import { createFileRoute } from '@tanstack/react-router'
import { RegisterForm } from '@/features/auth/register-form'

export const Route = createFileRoute('/register')({
  component: RegisterPage,
})

function RegisterPage() {
  return (
    <main className="flex min-h-svh items-start justify-center p-4 sm:items-center sm:p-6">
      <RegisterForm />
    </main>
  )
}
