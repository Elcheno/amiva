import { Trans } from '@lingui/react/macro'
import { useQuery } from '@tanstack/react-query'
import { CheckIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { ApiError } from '@/lib/api'
import { meQueryOptions, useResendVerification } from './api'

// Aviso en la zona privada mientras el email no esté verificado
export function VerifyEmailBanner() {
  const { data: me } = useQuery(meQueryOptions)
  const resend = useResendVerification()

  if (!me || me.emailVerified) return null

  const email = me.email
  const tooSoon = resend.error instanceof ApiError && resend.error.status === 429

  return (
    <div className="flex flex-wrap items-center gap-3 border-b bg-muted px-4 py-2 text-sm">
      <p>
        <Trans>Verifica tu email: te hemos enviado un enlace a {email}</Trans>
      </p>
      <Button
        variant="outline"
        size="sm"
        disabled={resend.isPending}
        onClick={() => resend.mutate()}
      >
        <Trans>Reenviar</Trans>
      </Button>
      <span role="status" className="flex items-center gap-1">
        {resend.isSuccess && (
          <>
            <CheckIcon aria-hidden className="size-4" />
            <Trans>Te hemos enviado otro enlace</Trans>
          </>
        )}
        {tooSoon && (
          <span className="text-destructive">
            <Trans>Espera un minuto antes de pedir otro</Trans>
          </span>
        )}
        {resend.isError && !tooSoon && (
          <span className="text-destructive">
            <Trans>No se pudo enviar el email. Inténtalo de nuevo.</Trans>
          </span>
        )}
      </span>
    </div>
  )
}
