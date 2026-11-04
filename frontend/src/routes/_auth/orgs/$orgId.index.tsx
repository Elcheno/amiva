import { Trans } from '@lingui/react/macro'
import { useSuspenseQuery } from '@tanstack/react-query'
import { createFileRoute } from '@tanstack/react-router'
import { GroupsList } from '@/features/groups/groups-list'
import { orgQueryOptions } from '@/features/orgs/api'
import { InviteMember } from '@/features/orgs/invite-member'
import { MembersList } from '@/features/orgs/members-list'
import { OrgHeader } from '@/features/orgs/org-header'
import { PendingInvitations } from '@/features/orgs/pending-invitations'
import { canInvite } from '@/features/orgs/permissions'
import { useRoleLabel } from '@/features/orgs/role-label'

export const Route = createFileRoute('/_auth/orgs/$orgId/')({
  component: OrgPage,
})

function OrgPage() {
  const { me } = Route.useRouteContext()
  const { orgId } = Route.useParams()
  const { data: org } = useSuspenseQuery(orgQueryOptions(orgId))
  const roleLabel = useRoleLabel()
  const role = roleLabel(org.role)

  return (
    <>
      <OrgHeader userName={me.displayName} username={me.username} currentOrgId={orgId} />
      <main className="mx-auto flex w-full max-w-3xl flex-col gap-6 p-4">
        <div className="flex flex-col gap-1">
          <h1 className="text-2xl font-semibold">{org.name}</h1>
          <p className="text-muted-foreground">
            <Trans>Tu rol: {role}</Trans>
          </p>
        </div>
        <section aria-labelledby="members" className="flex flex-col gap-3">
          <div className="flex items-center justify-between gap-3">
            <h2 id="members" className="text-lg font-semibold">
              <Trans>Miembros</Trans>
            </h2>
            <InviteMember orgId={orgId} myRole={org.role} />
          </div>
          <MembersList orgId={orgId} />
        </section>
        <section aria-labelledby="groups" className="flex flex-col gap-3">
          <h2 id="groups" className="text-lg font-semibold">
            <Trans>Grupos</Trans>
          </h2>
          <GroupsList orgId={orgId} myRole={org.role} />
        </section>
        {canInvite(org.role) && (
          <section aria-labelledby="pending-invitations" className="flex flex-col gap-3">
            <h2 id="pending-invitations" className="text-lg font-semibold">
              <Trans>Invitaciones pendientes</Trans>
            </h2>
            <PendingInvitations orgId={orgId} />
          </section>
        )}
      </main>
    </>
  )
}
