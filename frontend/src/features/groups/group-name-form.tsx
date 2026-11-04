import { useState, type FormEvent } from 'react'
import { Trans } from '@lingui/react/macro'
import { Button } from '@/components/ui/button'
import { Field, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { ApiError } from '@/lib/api'

interface GroupNameFormProps {
  id: string
  label: React.ReactNode
  submitLabel: React.ReactNode
  initialName?: string
  isPending: boolean
  error: Error | null
  // Resuelve al guardar bien; el formulario se vacía solo si clearOnSuccess
  onSubmit: (name: string) => Promise<unknown>
  clearOnSuccess?: boolean
}

// Campo de nombre de grupo para crear y renombrar, con los errores del backend en texto claro
export function GroupNameForm({
  id,
  label,
  submitLabel,
  initialName = '',
  isPending,
  error,
  onSubmit,
  clearOnSuccess = false,
}: GroupNameFormProps) {
  const [name, setName] = useState(initialName)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    try {
      await onSubmit(name)
      if (clearOnSuccess) setName('')
    } catch {
      // El error lo muestra la mutación
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-2">
      <Field>
        <FieldLabel htmlFor={id}>{label}</FieldLabel>
        <div className="flex gap-2">
          <Input
            id={id}
            autoComplete="off"
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
          <Button type="submit" disabled={isPending || name.trim() === ''}>
            {submitLabel}
          </Button>
        </div>
      </Field>
      {error && <GroupNameError error={error} />}
    </form>
  )
}

function GroupNameError({ error }: { error: Error }) {
  const status = error instanceof ApiError ? error.status : undefined
  return (
    <p role="alert" className="text-sm text-destructive">
      {status === 409 ? (
        <Trans>Ya hay un grupo con ese nombre</Trans>
      ) : status === 400 ? (
        <Trans>El nombre debe tener entre 1 y 60 caracteres</Trans>
      ) : status === 403 ? (
        <Trans>No tienes permiso para gestionar los grupos</Trans>
      ) : (
        <Trans>No se pudo guardar el grupo. Inténtalo de nuevo.</Trans>
      )}
    </p>
  )
}
