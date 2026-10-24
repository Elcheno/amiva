import { Trans } from '@lingui/react/macro'
import { useQuery } from '@tanstack/react-query'
import { Link } from '@tanstack/react-router'
import { Card, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { orgsQueryOptions, type OrgSummary } from './api'
import { useRoleLabel } from './role-label'
import { groupOrgs } from './select-org'

// Dos apartados: la organización propia y las demás donde soy miembro
export function OrgsOverview() {
  const { data: orgs, isPending, isError } = useQuery(orgsQueryOptions)

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
        <Trans>No se pudieron cargar las organizaciones</Trans>
      </p>
    )
  }

  const { own, participating } = groupOrgs(orgs)

  return (
    <div className="flex flex-col gap-8">
      <section aria-labelledby="own-org" className="flex flex-col gap-3">
        <h2 id="own-org" className="text-lg font-semibold">
          <Trans>Mi organización</Trans>
        </h2>
        {own ? (
          <OrgCard org={own} />
        ) : (
          <p className="text-muted-foreground">
            <Trans>No tienes organización propia</Trans>
          </p>
        )}
      </section>
      <section aria-labelledby="participating-orgs" className="flex flex-col gap-3">
        <h2 id="participating-orgs" className="text-lg font-semibold">
          <Trans>Donde participo</Trans>
        </h2>
        {participating.length > 0 ? (
          <ul className="flex flex-col gap-3">
            {participating.map((org) => (
              <li key={org.orgId}>
                <OrgCard org={org} />
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-muted-foreground">
            <Trans>Aún no participas en ninguna organización</Trans>
          </p>
        )}
      </section>
    </div>
  )
}

function OrgCard({ org }: { org: OrgSummary }) {
  const roleLabel = useRoleLabel()
  return (
    <Link to="/orgs/$orgId" params={{ orgId: org.orgId }} className="block">
      <Card size="sm" className="hover:bg-muted">
        <CardHeader>
          <CardTitle>{org.name}</CardTitle>
          <CardDescription>{roleLabel(org.role)}</CardDescription>
        </CardHeader>
      </Card>
    </Link>
  )
}
