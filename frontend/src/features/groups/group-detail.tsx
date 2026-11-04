import { useState, type FormEvent } from 'react'
import { Trans, useLingui } from '@lingui/react/macro'
import { useQuery } from '@tanstack/react-query'
import { Link, useNavigate } from '@tanstack/react-router'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import { Field, FieldLabel } from '@/components/ui/field'
import { ApiError } from '@/lib/api'
import { membersQueryOptions, type OrgRole } from '@/features/orgs/api'
import { canManageGroups } from '@/features/orgs/permissions'
import {
  groupQueryOptions,
  useAddGroupMember,
  useDeleteGroup,
  useRemoveGroupMember,
  useRenameGroup,
  type GroupDetail as GroupDetailData,
} from './api'
import { GroupNameForm } from './group-name-form'

interface GroupDetailProps {
  orgId: string
  groupId: string
  myRole: OrgRole
}

// Nombre y miembros actuales del grupo. Renombrar, borrar, añadir y quitar: solo OWNER y ADMIN
export function GroupDetail({ orgId, groupId, myRole }: GroupDetailProps) {
  const { data: group, isPending, isError } = useQuery(groupQueryOptions(orgId, groupId))

  return (
    <div className="flex flex-col gap-4">
      <Link
        to="/orgs/$orgId"
        params={{ orgId }}
        className="text-sm underline-offset-4 hover:underline"
      >
        <Trans>Volver a la organización</Trans>
      </Link>
      {isPending ? (
        <p className="text-muted-foreground">
          <Trans>Cargando…</Trans>
        </p>
      ) : isError ? (
        <p className="text-destructive">
          <Trans>No se pudo cargar el grupo</Trans>
        </p>
      ) : (
        <GroupDetailBody orgId={orgId} group={group} myRole={myRole} />
      )}
    </div>
  )
}

function GroupDetailBody({
  orgId,
  group,
  myRole,
}: {
  orgId: string
  group: GroupDetailData
  myRole: OrgRole
}) {
  const manage = canManageGroups(myRole)
  return (
    <>
      <div className="flex items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold">{group.name}</h1>
        {manage && <DeleteGroup orgId={orgId} group={group} />}
      </div>
      {manage && <RenameGroup orgId={orgId} group={group} />}
      <section aria-labelledby="group-members" className="flex flex-col gap-3">
        <h2 id="group-members" className="text-lg font-semibold">
          <Trans>Miembros del grupo</Trans>
        </h2>
        {manage && <AddMember orgId={orgId} group={group} />}
        <GroupMembers orgId={orgId} group={group} manage={manage} />
      </section>
    </>
  )
}

function RenameGroup({ orgId, group }: { orgId: string; group: GroupDetailData }) {
  const rename = useRenameGroup(orgId, group.id)
  return (
    // key: si el nombre cambia desde fuera, el campo se reinicia con el valor nuevo
    <GroupNameForm
      key={group.name}
      id="rename-group-name"
      label={<Trans>Nombre del grupo</Trans>}
      submitLabel={<Trans>Renombrar</Trans>}
      initialName={group.name}
      isPending={rename.isPending}
      error={rename.error}
      onSubmit={(name) => rename.mutateAsync(name)}
    />
  )
}

function DeleteGroup({ orgId, group }: { orgId: string; group: GroupDetailData }) {
  const navigate = useNavigate()
  const remove = useDeleteGroup(orgId, group.id)
  const name = group.name

  function confirm() {
    remove.mutate(undefined, {
      onSuccess: () => navigate({ to: '/orgs/$orgId', params: { orgId } }),
    })
  }

  return (
    <Dialog>
      <DialogTrigger render={<Button variant="destructive" />}>
        <Trans>Borrar grupo</Trans>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>
            <Trans>Borrar grupo</Trans>
          </DialogTitle>
          <DialogDescription>
            <Trans>
              Se borrará el grupo {name} con sus miembros. Las respuestas de los usuarios no se ven
              afectadas.
            </Trans>
          </DialogDescription>
        </DialogHeader>
        {remove.isError && (
          <p role="alert" className="text-sm text-destructive">
            <Trans>No se pudo borrar el grupo. Inténtalo de nuevo.</Trans>
          </p>
        )}
        <DialogFooter>
          <Button variant="destructive" disabled={remove.isPending} onClick={confirm}>
            <Trans>Confirmar borrado</Trans>
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

// Select con los miembros de la organización que aún no están en el grupo
function AddMember({ orgId, group }: { orgId: string; group: GroupDetailData }) {
  const { data: orgMembers } = useQuery(membersQueryOptions(orgId))
  const add = useAddGroupMember(orgId, group.id)
  const [userId, setUserId] = useState('')

  const inGroup = new Set(group.members.map((m) => m.userId))
  const candidates = (orgMembers ?? []).filter((m) => !inGroup.has(m.userId))

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!userId) return
    add.mutate(userId, { onSuccess: () => setUserId('') })
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-2">
      <Field>
        <FieldLabel htmlFor="add-group-member">
          <Trans>Añadir miembro</Trans>
        </FieldLabel>
        <div className="flex gap-2">
          <select
            id="add-group-member"
            className="h-8 flex-1 rounded-lg border bg-background px-2 text-sm"
            value={userId}
            onChange={(e) => setUserId(e.target.value)}
          >
            <option value="" />
            {candidates.map((m) => (
              <option key={m.userId} value={m.userId}>
                {m.displayName} (@{m.username})
              </option>
            ))}
          </select>
          <Button type="submit" disabled={add.isPending || !userId}>
            <Trans>Añadir</Trans>
          </Button>
        </div>
      </Field>
      {add.isError && <AddMemberError error={add.error} />}
    </form>
  )
}

function AddMemberError({ error }: { error: Error }) {
  const status = error instanceof ApiError ? error.status : undefined
  return (
    <p role="alert" className="text-sm text-destructive">
      {status === 409 ? (
        <Trans>Ese usuario ya está en el grupo</Trans>
      ) : status === 400 ? (
        <Trans>Ese usuario no es miembro de la organización</Trans>
      ) : (
        <Trans>No se pudo añadir al grupo. Inténtalo de nuevo.</Trans>
      )}
    </p>
  )
}

function GroupMembers({
  orgId,
  group,
  manage,
}: {
  orgId: string
  group: GroupDetailData
  manage: boolean
}) {
  const { t, i18n } = useLingui()
  const removeMember = useRemoveGroupMember(orgId, group.id)
  const removeLabel = (displayName: string) => t`Quitar a ${displayName}`

  if (group.members.length === 0) {
    return (
      <p className="text-muted-foreground">
        <Trans>El grupo no tiene miembros</Trans>
      </p>
    )
  }

  return (
    <>
      <table className="w-full text-left text-sm">
        <thead className="text-muted-foreground">
          <tr>
            <th className="py-2 font-medium">
              <Trans>Nombre</Trans>
            </th>
            <th className="py-2 font-medium">
              <Trans>Añadido</Trans>
            </th>
            {manage && <th className="py-2" />}
          </tr>
        </thead>
        <tbody>
          {group.members.map((member) => (
            <tr key={member.userId} className="border-t">
              <td className="py-2">
                <div>{member.displayName}</div>
                <div className="text-xs text-muted-foreground">@{member.username}</div>
              </td>
              <td className="py-2">{i18n.date(member.addedAt, { dateStyle: 'medium' })}</td>
              {manage && (
                <td className="py-2 text-right">
                  <Button
                    variant="outline"
                    size="sm"
                    aria-label={removeLabel(member.displayName)}
                    disabled={removeMember.isPending}
                    onClick={() => removeMember.mutate(member.userId)}
                  >
                    <Trans>Quitar</Trans>
                  </Button>
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
      {removeMember.isError && (
        <p role="alert" className="text-sm text-destructive">
          <Trans>No se pudo quitar del grupo. Inténtalo de nuevo.</Trans>
        </p>
      )}
    </>
  )
}
