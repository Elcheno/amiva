import { useEffect, useState, type FormEvent } from 'react'
import { Trans, useLingui } from '@lingui/react/macro'
import { useQuery } from '@tanstack/react-query'
import { CheckIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import { Field, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { ApiError } from '@/lib/api'
import {
  INVITE_SEARCH_MIN_LENGTH,
  invitableUsersQueryOptions,
  useInviteMember,
  type InvitableUser,
  type InviteRole,
  type OrgRole,
} from './api'
import { canInvite, canInviteAsAdmin } from './permissions'
import { useRoleLabel } from './role-label'

const SEARCH_DEBOUNCE_MS = 300

// Botón "Invitar" con el diálogo de búsqueda. Solo para OWNER y ADMIN
export function InviteMember({ orgId, myRole }: { orgId: string; myRole: OrgRole }) {
  if (!canInvite(myRole)) return null
  return (
    <Dialog>
      <DialogTrigger render={<Button />}>
        <Trans>Invitar</Trans>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>
            <Trans>Invitar a la organización</Trans>
          </DialogTitle>
          <DialogDescription>
            <Trans>Busca a un usuario registrado por su nombre o su @username.</Trans>
          </DialogDescription>
        </DialogHeader>
        {/* Se desmonta al cerrar: cada apertura empieza limpia */}
        <InviteMemberForm orgId={orgId} myRole={myRole} />
      </DialogContent>
    </Dialog>
  )
}

function useDebouncedValue(value: string, delayMs: number) {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs)
    return () => clearTimeout(timer)
  }, [value, delayMs])
  return debounced
}

function InviteMemberForm({ orgId, myRole }: { orgId: string; myRole: OrgRole }) {
  const { t } = useLingui()
  const roleLabel = useRoleLabel()
  const [query, setQuery] = useState('')
  const [selected, setSelected] = useState<InvitableUser | null>(null)
  const [role, setRole] = useState<InviteRole>('MEMBER')
  const [invitedName, setInvitedName] = useState<string | null>(null)
  const debouncedQuery = useDebouncedValue(query, SEARCH_DEBOUNCE_MS)
  const search = useQuery(invitableUsersQueryOptions(orgId, debouncedQuery))
  const invite = useInviteMember(orgId)

  const tooShort = [...debouncedQuery.trim()].length < INVITE_SEARCH_MIN_LENGTH
  const selectedName = selected?.displayName
  const roles: InviteRole[] = canInviteAsAdmin(myRole) ? ['MEMBER', 'ADMIN'] : ['MEMBER']

  function select(user: InvitableUser) {
    setSelected(user)
    setRole('MEMBER')
    setInvitedName(null)
    invite.reset()
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!selected) return
    const user = selected
    invite.mutate(
      { userId: user.userId, role },
      {
        onSuccess: () => {
          setInvitedName(user.displayName)
          setSelected(null)
        },
      },
    )
  }

  return (
    <div className="flex flex-col gap-4">
      <Field>
        <FieldLabel htmlFor="invite-search">
          <Trans>Buscar usuario</Trans>
        </FieldLabel>
        <Input
          id="invite-search"
          type="search"
          autoComplete="off"
          placeholder={t`Nombre o @username`}
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
      </Field>

      <SearchResults
        tooShort={tooShort}
        isPending={search.isPending}
        isError={search.isError}
        users={search.data}
        selectedId={selected?.userId}
        onSelect={select}
      />

      {selected && (
        <form onSubmit={handleSubmit} className="flex flex-col gap-3 border-t pt-4">
          <p>
            <Trans>
              Invitar a <strong>{selectedName}</strong>
            </Trans>
          </p>
          <Field>
            <FieldLabel htmlFor="invite-role">
              <Trans>Rol</Trans>
            </FieldLabel>
            <select
              id="invite-role"
              className="h-8 rounded-lg border bg-background px-2 text-sm"
              value={role}
              onChange={(e) => setRole(e.target.value as InviteRole)}
            >
              {roles.map((r) => (
                <option key={r} value={r}>
                  {roleLabel(r)}
                </option>
              ))}
            </select>
          </Field>
          {invite.isError && <InviteError error={invite.error} />}
          <Button type="submit" disabled={invite.isPending}>
            {invite.isPending ? <Trans>Enviando…</Trans> : <Trans>Enviar invitación</Trans>}
          </Button>
        </form>
      )}

      <p role="status" className="flex items-center gap-1 text-sm">
        {invitedName && (
          <>
            <CheckIcon aria-hidden className="size-4" />
            <Trans>Invitación enviada a {invitedName}</Trans>
          </>
        )}
      </p>
    </div>
  )
}

interface SearchResultsProps {
  tooShort: boolean
  isPending: boolean
  isError: boolean
  users: InvitableUser[] | undefined
  selectedId: string | undefined
  onSelect: (user: InvitableUser) => void
}

function SearchResults({
  tooShort,
  isPending,
  isError,
  users,
  selectedId,
  onSelect,
}: SearchResultsProps) {
  const { t } = useLingui()
  const min = INVITE_SEARCH_MIN_LENGTH
  if (tooShort) {
    return (
      <p className="text-sm text-muted-foreground">
        <Trans>Escribe al menos {min} caracteres</Trans>
      </p>
    )
  }
  if (isPending) {
    return (
      <p className="text-sm text-muted-foreground">
        <Trans>Buscando…</Trans>
      </p>
    )
  }
  if (isError || !users) {
    return (
      <p className="text-sm text-destructive">
        <Trans>No se pudo buscar. Inténtalo de nuevo.</Trans>
      </p>
    )
  }
  if (users.length === 0) {
    return (
      <p className="text-sm text-muted-foreground">
        <Trans>No hay usuarios que coincidan</Trans>
      </p>
    )
  }
  return (
    <ul className="flex flex-col gap-1" aria-label={t`Resultados`}>
      {users.map((user) => (
        <li key={user.userId}>
          <button
            type="button"
            aria-pressed={user.userId === selectedId}
            className="flex w-full flex-col items-start rounded-lg px-2 py-1.5 text-left hover:bg-muted aria-pressed:bg-muted"
            onClick={() => onSelect(user)}
          >
            <span>{user.displayName}</span>
            <span className="text-xs text-muted-foreground">@{user.username}</span>
            {user.pendingInvitation && (
              <span className="text-xs text-muted-foreground">
                <Trans>Invitación pendiente</Trans>
              </span>
            )}
          </button>
        </li>
      ))}
    </ul>
  )
}

function InviteError({ error }: { error: Error }) {
  const status = error instanceof ApiError ? error.status : undefined
  return (
    <p role="alert" className="text-sm text-destructive">
      {status === 403 ? (
        <Trans>No tienes permiso para invitar con ese rol</Trans>
      ) : status === 409 ? (
        <Trans>Ese usuario ya es miembro de la organización</Trans>
      ) : status === 503 ? (
        <Trans>No se pudo enviar el email de invitación. Inténtalo de nuevo.</Trans>
      ) : (
        <Trans>No se pudo enviar la invitación. Inténtalo de nuevo.</Trans>
      )}
    </p>
  )
}
