import { Trans } from '@lingui/react/macro'
import { useQuery } from '@tanstack/react-query'
import { membersQueryOptions } from './api'
import { useRoleLabel } from './role-label'

export function MembersList({ orgId }: { orgId: string }) {
  const roleLabel = useRoleLabel()
  const { data: members, isPending, isError } = useQuery(membersQueryOptions(orgId))

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
        <Trans>No se pudieron cargar los miembros</Trans>
      </p>
    )
  }

  return (
    <table className="w-full text-left text-sm">
      <thead className="text-muted-foreground">
        <tr>
          <th className="py-2 font-medium">
            <Trans>Nombre</Trans>
          </th>
          <th className="py-2 font-medium">
            <Trans>Email</Trans>
          </th>
          <th className="py-2 font-medium">
            <Trans>Rol</Trans>
          </th>
        </tr>
      </thead>
      <tbody>
        {members.map((member) => (
          <tr key={member.userId} className="border-t">
            <td className="py-2">
              <div>{member.displayName}</div>
              <div className="text-xs text-muted-foreground">@{member.username}</div>
            </td>
            <td className="py-2">{member.email}</td>
            <td className="py-2">{roleLabel(member.role)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
