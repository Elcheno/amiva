import { Plural, Trans } from '@lingui/react/macro'
import { useQuery } from '@tanstack/react-query'
import { Link } from '@tanstack/react-router'
import type { OrgRole } from '@/features/orgs/api'
import { canManageGroups } from '@/features/orgs/permissions'
import { groupsQueryOptions, useCreateGroup } from './api'
import { GroupNameForm } from './group-name-form'

// Grupos de la organización: los ve cualquier miembro; crear solo OWNER y ADMIN
export function GroupsList({ orgId, myRole }: { orgId: string; myRole: OrgRole }) {
  const { data: groups, isPending, isError } = useQuery(groupsQueryOptions(orgId))
  const create = useCreateGroup(orgId)

  return (
    <div className="flex flex-col gap-3">
      {canManageGroups(myRole) && (
        <GroupNameForm
          id="create-group-name"
          label={<Trans>Crear grupo</Trans>}
          submitLabel={<Trans>Crear</Trans>}
          isPending={create.isPending}
          error={create.error}
          onSubmit={(name) => create.mutateAsync(name)}
          clearOnSuccess
        />
      )}
      {isPending ? (
        <p className="text-muted-foreground">
          <Trans>Cargando…</Trans>
        </p>
      ) : isError ? (
        <p className="text-destructive">
          <Trans>No se pudieron cargar los grupos</Trans>
        </p>
      ) : groups.length === 0 ? (
        <p className="text-muted-foreground">
          <Trans>No hay grupos</Trans>
        </p>
      ) : (
        <ul className="flex flex-col text-sm">
          {groups.map((group) => (
            <li key={group.id} className="flex items-center justify-between gap-3 border-t py-2">
              <Link
                to="/orgs/$orgId/groups/$groupId"
                params={{ orgId, groupId: group.id }}
                className="underline-offset-4 hover:underline"
              >
                {group.name}
              </Link>
              <span className="text-muted-foreground">
                <Plural value={group.memberCount} one="# miembro" other="# miembros" />
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
