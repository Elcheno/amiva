import { createFileRoute } from '@tanstack/react-router'
import { VerifyEmail } from '@/features/auth/verify-email'

interface VerifyEmailSearch {
  token?: string
}

// Pública: el enlace del email se abre con o sin sesión
export const Route = createFileRoute('/verify-email')({
  validateSearch: (search): VerifyEmailSearch =>
    typeof search.token === 'string' && search.token !== '' ? { token: search.token } : {},
  component: VerifyEmailPage,
})

function VerifyEmailPage() {
  const { token } = Route.useSearch()
  return (
    <main className="flex min-h-svh items-start justify-center p-4 sm:items-center sm:p-6">
      <VerifyEmail token={token} />
    </main>
  )
}
