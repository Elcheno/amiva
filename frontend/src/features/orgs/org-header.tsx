import { Trans, useLingui } from '@lingui/react/macro'
import { useQuery } from '@tanstack/react-query'
import { Link, useNavigate } from '@tanstack/react-router'
import { Button } from '@/components/ui/button'
import { useLogout } from '@/features/auth/api'
import { orgsQueryOptions } from './api'
import { groupOrgs } from './select-org'

interface OrgHeaderProps {
  userName: string
  // Org abierta: con ella se muestra el selector y el enlace a todas
  currentOrgId?: string
}

export function OrgHeader({ userName, currentOrgId }: OrgHeaderProps) {
  const navigate = useNavigate()
  const logout = useLogout()

  return (
    <header className="flex flex-wrap items-center gap-3 border-b p-4">
      {currentOrgId && <OrgSwitcher currentOrgId={currentOrgId} />}
      {currentOrgId && (
        <Link to="/orgs" className="text-sm underline-offset-4 hover:underline">
          <Trans>Todas las organizaciones</Trans>
        </Link>
      )}
      <span className="ml-auto text-sm text-muted-foreground">{userName}</span>
      <Button
        variant="outline"
        disabled={logout.isPending}
        onClick={() =>
          logout.mutate(undefined, { onSuccess: () => navigate({ to: '/login', replace: true }) })
        }
      >
        <Trans>Cerrar sesión</Trans>
      </Button>
    </header>
  )
}

// Cambiar de org = navegar a su URL
function OrgSwitcher({ currentOrgId }: { currentOrgId: string }) {
  const { t } = useLingui()
  const navigate = useNavigate()
  const { data: orgs = [] } = useQuery(orgsQueryOptions)
  const { own, participating } = groupOrgs(orgs)

  return (
    <select
      aria-label={t`Organización`}
      className="h-8 rounded-lg border bg-background px-2 text-sm"
      value={currentOrgId}
      onChange={(e) => navigate({ to: '/orgs/$orgId', params: { orgId: e.target.value } })}
    >
      {own && (
        <optgroup label={t`Mi organización`}>
          <option value={own.orgId}>{own.name}</option>
        </optgroup>
      )}
      {participating.length > 0 && (
        <optgroup label={t`Donde participo`}>
          {participating.map((org) => (
            <option key={org.orgId} value={org.orgId}>
              {org.name}
            </option>
          ))}
        </optgroup>
      )}
    </select>
  )
}
