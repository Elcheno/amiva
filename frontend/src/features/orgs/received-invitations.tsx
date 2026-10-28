import { Trans } from '@lingui/react/macro'
import { useQuery } from '@tanstack/react-query'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { myInvitationsQueryOptions, type ReceivedInvitation } from './api'
import { InvitationActions } from './invitation-actions'
import { useRoleLabel } from './role-label'

// Apartado "Invitaciones" de /orgs: solo aparece si tengo alguna pendiente
export function ReceivedInvitations() {
  const { data: invitations } = useQuery(myInvitationsQueryOptions)

  if (!invitations || invitations.length === 0) return null

  return (
    <section aria-labelledby="received-invitations" className="flex flex-col gap-3">
      <h2 id="received-invitations" className="text-lg font-semibold">
        <Trans>Invitaciones</Trans>
      </h2>
      <ul className="flex flex-col gap-3">
        {invitations.map((invitation) => (
          <li key={invitation.id}>
            <ReceivedInvitationCard invitation={invitation} />
          </li>
        ))}
      </ul>
    </section>
  )
}

export function ReceivedInvitationCard({
  invitation,
  onDeclined,
}: {
  invitation: ReceivedInvitation
  onDeclined?: () => void
}) {
  const roleLabel = useRoleLabel()
  const role = roleLabel(invitation.role)
  const invitedBy = invitation.invitedByName

  return (
    <Card size="sm">
      <CardHeader>
        <CardTitle>{invitation.organizationName}</CardTitle>
        <CardDescription>
          {invitedBy ? (
            <Trans>
              {invitedBy} te invita como {role}
            </Trans>
          ) : (
            <Trans>Te invitan como {role}</Trans>
          )}
        </CardDescription>
      </CardHeader>
      <CardContent>
        <InvitationActions invitation={invitation} onDeclined={onDeclined} />
      </CardContent>
    </Card>
  )
}
