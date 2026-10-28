import { Trans } from '@lingui/react/macro'
import { createFileRoute, redirect } from '@tanstack/react-router'
import { OrgHeader } from '@/features/orgs/org-header'
import { orgsQueryOptions } from '@/features/orgs/api'
import { pickTargetOrg, readLastOrgId } from '@/features/orgs/select-org'

// / no tiene contenido propio: lleva a la última org usada o a la propia
export const Route = createFileRoute('/_auth/')({
  beforeLoad: async ({ context }) => {
    const orgs = await context.queryClient.ensureQueryData(orgsQueryOptions)
    const orgId = pickTargetOrg(orgs, readLastOrgId())
    if (orgId) throw redirect({ to: '/orgs/$orgId', params: { orgId }, replace: true })
  },
  component: NoOrgs,
})

function NoOrgs() {
  const { me } = Route.useRouteContext()
  return (
    <>
      <OrgHeader userName={me.displayName} username={me.username} />
      <main className="p-4">
        <p className="text-muted-foreground">
          <Trans>No perteneces a ninguna organización</Trans>
        </p>
      </main>
    </>
  )
}
