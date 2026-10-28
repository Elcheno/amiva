import { Trans } from '@lingui/react/macro'
import { createFileRoute } from '@tanstack/react-router'
import { OrgHeader } from '@/features/orgs/org-header'
import { OrgsOverview } from '@/features/orgs/orgs-overview'

export const Route = createFileRoute('/_auth/orgs/')({
  component: OrgsPage,
})

function OrgsPage() {
  const { me } = Route.useRouteContext()
  return (
    <>
      <OrgHeader userName={me.displayName} username={me.username} />
      <main className="mx-auto flex w-full max-w-2xl flex-col gap-6 p-4">
        <h1 className="text-2xl font-semibold">
          <Trans>Organizaciones</Trans>
        </h1>
        <OrgsOverview />
      </main>
    </>
  )
}
