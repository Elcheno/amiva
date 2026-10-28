import { Trans } from '@lingui/react/macro'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from '@tanstack/react-router'
import { Button } from '@/components/ui/button'
import { meQueryOptions } from '@/features/auth/api'
import { ApiError } from '@/lib/api'
import { useAcceptInvitation, useDeclineInvitation, type ReceivedInvitation } from './api'

// Aceptar o rechazar una invitación recibida. Sin email verificado los botones se desactivan.
// Al aceptar se navega a la organización; onDeclined, si se pasa, se llama tras rechazar.
// mutateAsync y no callbacks de mutate: al invalidar la lista la tarjeta puede desmontarse antes
export function InvitationActions({
  invitation,
  onDeclined,
}: {
  invitation: ReceivedInvitation
  onDeclined?: () => void
}) {
  const { data: me } = useQuery(meQueryOptions)
  const navigate = useNavigate()
  const accept = useAcceptInvitation()
  const decline = useDeclineInvitation()

  const verified = me?.emailVerified === true
  const busy = accept.isPending || decline.isPending
  const error = accept.error ?? decline.error

  return (
    <div className="flex flex-col gap-2">
      <div className="flex gap-2">
        <Button
          size="sm"
          disabled={!verified || busy}
          onClick={() =>
            accept
              .mutateAsync(invitation.id)
              .then((org) => navigate({ to: '/orgs/$orgId', params: { orgId: org.orgId } }))
              // El error ya lo pinta InvitationError
              .catch(() => {})
          }
        >
          <Trans>Aceptar</Trans>
        </Button>
        <Button
          size="sm"
          variant="outline"
          disabled={!verified || busy}
          onClick={() =>
            decline
              .mutateAsync(invitation.id)
              .then(() => onDeclined?.())
              .catch(() => {})
          }
        >
          <Trans>Rechazar</Trans>
        </Button>
      </div>
      {!verified && (
        <p className="text-sm text-muted-foreground">
          <Trans>Verifica tu email para responder a la invitación</Trans>
        </p>
      )}
      {error && <InvitationError error={error} />}
    </div>
  )
}

function InvitationError({ error }: { error: Error }) {
  const status = error instanceof ApiError ? error.status : undefined
  return (
    <p role="alert" className="text-sm text-destructive">
      {status === 404 && (
        <Trans>Esta invitación no es para tu cuenta o ya no está disponible</Trans>
      )}
      {status === 409 && <Trans>Ya eres miembro de esta organización</Trans>}
      {status === 403 && <Trans>Verifica tu email para responder a la invitación</Trans>}
      {status !== 404 && status !== 409 && status !== 403 && (
        <Trans>No se pudo responder a la invitación. Inténtalo de nuevo.</Trans>
      )}
    </p>
  )
}
