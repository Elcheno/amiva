import { Trans, useLingui } from '@lingui/react/macro'
import { useQuery } from '@tanstack/react-query'
import { invitationsQueryOptions } from './api'
import { useRoleLabel } from './role-label'

// Invitaciones pendientes de la organización (solo OWNER y ADMIN pueden verlas)
export function PendingInvitations({ orgId }: { orgId: string }) {
  const { i18n } = useLingui()
  const roleLabel = useRoleLabel()
  const { data: invitations, isPending, isError } = useQuery(invitationsQueryOptions(orgId))

  if (isPending) {
    return (
      <p className="text-muted-foreground">
        <Trans>Cargando…</Trans>
      </p>
    )
  }
  if (isError) {
    return (
      <p className="text-destructive">
        <Trans>No se pudieron cargar las invitaciones</Trans>
      </p>
    )
  }
  if (invitations.length === 0) {
    return (
      <p className="text-muted-foreground">
        <Trans>No hay invitaciones pendientes</Trans>
      </p>
    )
  }

  return (
    <table className="w-full text-left text-sm">
      <thead className="text-muted-foreground">
        <tr>
          <th className="py-2 font-medium">
            <Trans>Nombre</Trans>
          </th>
          <th className="py-2 font-medium">
            <Trans>Rol</Trans>
          </th>
          <th className="py-2 font-medium">
            <Trans>Invitado por</Trans>
          </th>
          <th className="py-2 font-medium">
            <Trans>Enviada</Trans>
          </th>
        </tr>
      </thead>
      <tbody>
        {invitations.map((invitation) => (
          <tr key={invitation.invitationId} className="border-t">
            <td className="py-2">
              <div>{invitation.displayName}</div>
              <div className="text-xs text-muted-foreground">@{invitation.username}</div>
            </td>
            <td className="py-2">{roleLabel(invitation.role)}</td>
            <td className="py-2">{invitation.invitedByName ?? '—'}</td>
            {/* updatedAt: la fecha del último envío (los reenvíos la actualizan) */}
            <td className="py-2">{i18n.date(invitation.updatedAt, { dateStyle: 'medium' })}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
