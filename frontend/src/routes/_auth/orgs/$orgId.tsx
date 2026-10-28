import { Trans } from '@lingui/react/macro'
import { useSuspenseQuery } from '@tanstack/react-query'
import {
  createFileRoute,
  ErrorComponent,
  Link,
  type ErrorComponentProps,
} from '@tanstack/react-router'
import { ApiError } from '@/lib/api'
import { orgQueryOptions, orgsQueryOptions } from '@/features/orgs/api'
import { MembersList } from '@/features/orgs/members-list'
import { OrgHeader } from '@/features/orgs/org-header'
import { useRoleLabel } from '@/features/orgs/role-label'
import { saveLastOrgId } from '@/features/orgs/select-org'

// La org activa vive en la URL: cambiar de org es navegar a otra /orgs/$orgId
export const Route = createFileRoute('/_auth/orgs/$orgId')({
  loader: async ({ context, params }) => {
    const [org] = await Promise.all([
      context.queryClient.ensureQueryData(orgQueryOptions(params.orgId)),
      context.queryClient.ensureQueryData(orgsQueryOptions),
    ])
    // Solo se recuerda si tengo acceso (la petición anterior no dio 404)
    saveLastOrgId(org.orgId)
  },
  component: OrgPage,
  errorComponent: OrgError,
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
          <h2 id="members" className="text-lg font-semibold">
            <Trans>Miembros</Trans>
          </h2>
          <MembersList orgId={orgId} />
        </section>
      </main>
    </>
  )
}

function OrgError({ error }: ErrorComponentProps) {
  if (!(error instanceof ApiError) || error.status !== 404) return <ErrorComponent error={error} />
  return (
    <main className="flex flex-col items-start gap-3 p-4">
      <p>
        <Trans>No tienes acceso a esta organización</Trans>
      </p>
      <Link to="/orgs" className="text-sm underline-offset-4 hover:underline">
        <Trans>Ver mis organizaciones</Trans>
      </Link>
    </main>
  )
}
