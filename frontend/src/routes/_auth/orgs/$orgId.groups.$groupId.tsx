import { useSuspenseQuery } from '@tanstack/react-query'
import { createFileRoute } from '@tanstack/react-router'
import { GroupDetail } from '@/features/groups/group-detail'
import { orgQueryOptions } from '@/features/orgs/api'
import { OrgHeader } from '@/features/orgs/org-header'

export const Route = createFileRoute('/_auth/orgs/$orgId/groups/$groupId')({
  component: GroupPage,
})

function GroupPage() {
  const { me } = Route.useRouteContext()
  const { orgId, groupId } = Route.useParams()
  // La org ya está en caché: la carga el loader de /orgs/$orgId
  const { data: org } = useSuspenseQuery(orgQueryOptions(orgId))

  return (
    <>
      <OrgHeader userName={me.displayName} username={me.username} currentOrgId={orgId} />
      <main className="mx-auto flex w-full max-w-3xl flex-col gap-6 p-4">
        <GroupDetail orgId={orgId} groupId={groupId} myRole={org.role} />
      </main>
    </>
  )
}
