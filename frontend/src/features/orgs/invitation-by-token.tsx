import { useState } from 'react'
import { Trans } from '@lingui/react/macro'
import { useQuery } from '@tanstack/react-query'
import { Link } from '@tanstack/react-router'
import { ApiError } from '@/lib/api'
import { invitationByTokenQueryOptions } from './api'
import { ReceivedInvitationCard } from './received-invitations'

// Página del enlace del email: localiza la invitación por el token y permite responderla
export function InvitationByToken({ token }: { token: string }) {
  const { data: invitation, isPending, error } = useQuery(invitationByTokenQueryOptions(token))
  const [declined, setDeclined] = useState(false)

  if (declined) {
    return (
      <div className="flex flex-col gap-2">
        <p role="status">
          <Trans>Invitación rechazada</Trans>
        </p>
        <OrgsLink />
      </div>
    )
  }
  if (isPending) {
    return (
      <p className="text-muted-foreground">
        <Trans>Cargando…</Trans>
      </p>
    )
  }
  if (error) {
    return (
      <div className="flex flex-col gap-2">
        <p className="text-destructive">
          {error instanceof ApiError && error.status === 404 ? (
            <Trans>Esta invitación no es para tu cuenta o ya no está disponible</Trans>
          ) : (
            <Trans>No se pudo cargar la invitación</Trans>
          )}
        </p>
        <OrgsLink />
      </div>
    )
  }

  return <ReceivedInvitationCard invitation={invitation} onDeclined={() => setDeclined(true)} />
}

function OrgsLink() {
  return (
    <Link to="/orgs" className="text-sm underline-offset-4 hover:underline">
      <Trans>Ir a mis organizaciones</Trans>
    </Link>
  )
}
