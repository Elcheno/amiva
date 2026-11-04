import { Trans } from '@lingui/react/macro'
import {
  createFileRoute,
  ErrorComponent,
  Link,
  Outlet,
  type ErrorComponentProps,
} from '@tanstack/react-router'
import { ApiError } from '@/lib/api'
import { orgQueryOptions, orgsQueryOptions } from '@/features/orgs/api'
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
  component: OrgLayout,
  errorComponent: OrgError,
})

function OrgLayout() {
  return <Outlet />
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
