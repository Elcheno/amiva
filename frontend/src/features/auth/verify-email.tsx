import { useEffect, useRef } from 'react'
import { Trans } from '@lingui/react/macro'
import { Link } from '@tanstack/react-router'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { ApiError } from '@/lib/api'
import { useVerifyEmail } from './api'

type Outcome = 'loading' | 'verified' | 'expired' | 'used' | 'invalid' | 'error'

function outcomeOf(mutation: ReturnType<typeof useVerifyEmail>, token?: string): Outcome {
  if (!token) return 'invalid'
  if (mutation.isSuccess) return 'verified'
  if (!mutation.isError) return 'loading'
  const error = mutation.error
  if (!(error instanceof ApiError) || error.status !== 400) return 'error'
  if (error.problem?.reason === 'EXPIRED') return 'expired'
  if (error.problem?.reason === 'USED') return 'used'
  return 'invalid'
}

// Abre el enlace del email: hace el POST una sola vez al montar (con o sin sesión)
export function VerifyEmail({ token }: { token?: string }) {
  const mutation = useVerifyEmail()
  const { mutate } = mutation
  // StrictMode monta dos veces en dev: un segundo POST daría USED
  const sent = useRef(false)

  useEffect(() => {
    if (!token || sent.current) return
    sent.current = true
    mutate(token)
  }, [token, mutate])

  const outcome = outcomeOf(mutation, token)

  return (
    <Card className="w-full max-w-sm">
      <CardHeader>
        <CardTitle>
          {outcome === 'loading' && <Trans>Verificando tu email…</Trans>}
          {outcome === 'verified' && <Trans>Email verificado</Trans>}
          {outcome === 'expired' && <Trans>El enlace ha caducado</Trans>}
          {outcome === 'used' && <Trans>Este enlace ya se ha usado</Trans>}
          {outcome === 'invalid' && <Trans>El enlace no es válido</Trans>}
          {outcome === 'error' && <Trans>No se pudo verificar tu email</Trans>}
        </CardTitle>
        <CardDescription role={outcome === 'loading' ? 'status' : undefined}>
          {outcome === 'loading' && <Trans>Un momento, por favor.</Trans>}
          {outcome === 'verified' && <Trans>Gracias. Tu email ya está confirmado.</Trans>}
          {outcome === 'expired' && (
            <Trans>Inicia sesión y pide otro enlace desde el aviso de la parte superior.</Trans>
          )}
          {outcome === 'used' && (
            <Trans>Si ya verificaste tu email, no tienes que hacer nada más.</Trans>
          )}
          {outcome === 'invalid' && (
            <Trans>
              Comprueba que has copiado el enlace completo o pide otro desde la aplicación.
            </Trans>
          )}
          {outcome === 'error' && <Trans>Inténtalo de nuevo más tarde.</Trans>}
        </CardDescription>
      </CardHeader>
      {outcome !== 'loading' && (
        <CardContent>
          <Link to="/" className="text-sm underline-offset-4 hover:underline">
            <Trans>Ir al inicio</Trans>
          </Link>
        </CardContent>
      )}
    </Card>
  )
}
