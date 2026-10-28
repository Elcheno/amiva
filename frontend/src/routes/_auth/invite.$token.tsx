import { Trans } from '@lingui/react/macro'
import { createFileRoute } from '@tanstack/react-router'
import { InvitationByToken } from '@/features/orgs/invitation-by-token'
import { OrgHeader } from '@/features/orgs/org-header'

// Enlace del email (/invite/<token>). Privada: sin sesión, _auth lleva a /login y vuelve aquí
export const Route = createFileRoute('/_auth/invite/$token')({
  component: InvitePage,
})

function InvitePage() {
  const { me } = Route.useRouteContext()
  const { token } = Route.useParams()
  return (
    <>
      <OrgHeader userName={me.displayName} username={me.username} />
      <main className="mx-auto flex w-full max-w-2xl flex-col gap-6 p-4">
        <h1 className="text-2xl font-semibold">
          <Trans>Invitación</Trans>
        </h1>
        <InvitationByToken token={token} />
      </main>
    </>
  )
}
