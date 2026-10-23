import { useState, type FormEvent, type ReactNode } from 'react'
import { msg } from '@lingui/core/macro'
import { useLingui } from '@lingui/react'
import { Trans } from '@lingui/react/macro'
import { Link } from '@tanstack/react-router'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { ApiError } from '@/lib/api'
import { useRegister } from './api'
import {
  validateRegister,
  type RegisterField,
  type RegisterValues,
  LIMITS,
} from './register-validation'

const EMPTY: RegisterValues = { displayName: '', email: '', password: '' }
const DUPLICATE_EMAIL = msg`Ese email ya está registrado`

function browserTimezone(): string | undefined {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone
  } catch {
    return undefined
  }
}

// Errores por campo que devuelve el backend en el ProblemDetail (400)
function serverFieldErrors(error: unknown): Partial<Record<RegisterField, string>> {
  if (!(error instanceof ApiError) || error.status !== 400) return {}
  const errors = error.problem?.errors
  return typeof errors === 'object' && errors !== null
    ? (errors as Partial<Record<RegisterField, string>>)
    : {}
}

export function RegisterForm() {
  const { i18n } = useLingui()
  const mutation = useRegister()
  const [values, setValues] = useState<RegisterValues>(EMPTY)
  const [touched, setTouched] = useState<Partial<Record<RegisterField, boolean>>>({})
  const [submitted, setSubmitted] = useState(false)
  // Campos editados desde el último envío: ya no se muestra su error del servidor
  const [editedSinceSubmit, setEditedSinceSubmit] = useState<
    Partial<Record<RegisterField, boolean>>
  >({})

  const clientErrors = validateRegister(values)
  const serverErrors = serverFieldErrors(mutation.error)
  const isDuplicate = mutation.error instanceof ApiError && mutation.error.status === 409

  function errorFor(field: RegisterField): string | undefined {
    const client = clientErrors[field]
    if (client && (touched[field] || submitted)) return i18n._(client)
    if (editedSinceSubmit[field]) return undefined
    if (field === 'email' && isDuplicate) return i18n._(DUPLICATE_EMAIL)
    return serverErrors[field]
  }

  function update(field: RegisterField, value: string) {
    setValues((v) => ({ ...v, [field]: value }))
    setEditedSinceSubmit((e) => ({ ...e, [field]: true }))
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitted(true)
    if (Object.keys(clientErrors).length > 0) return
    setEditedSinceSubmit({})
    mutation.mutate({
      displayName: values.displayName.trim(),
      email: values.email.trim(),
      password: values.password,
      timezone: browserTimezone(),
    })
  }

  if (mutation.isSuccess) {
    const { email, orgName } = mutation.data
    return (
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle>
            <Trans>Cuenta creada</Trans>
          </CardTitle>
          <CardDescription>
            <Trans>
              Hemos creado tu cuenta ({email}) y tu organización «{orgName}».
            </Trans>
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Link to="/login" className={buttonVariants({ size: 'lg', className: 'w-full' })}>
            <Trans>Iniciar sesión</Trans>
          </Link>
        </CardContent>
      </Card>
    )
  }

  const unexpectedError = mutation.isError && !isDuplicate && Object.keys(serverErrors).length === 0

  const fields: {
    name: RegisterField
    label: ReactNode
    type: string
    autoComplete: string
    maxLength: number
  }[] = [
    {
      name: 'displayName',
      label: <Trans>Nombre</Trans>,
      type: 'text',
      autoComplete: 'name',
      maxLength: LIMITS.displayName,
    },
    {
      name: 'email',
      label: <Trans>Email</Trans>,
      type: 'email',
      autoComplete: 'email',
      maxLength: LIMITS.email,
    },
    {
      name: 'password',
      label: <Trans>Contraseña</Trans>,
      type: 'password',
      autoComplete: 'new-password',
      maxLength: LIMITS.passwordMax,
    },
  ]

  return (
    <Card className="w-full max-w-sm">
      <CardHeader>
        <CardTitle>
          <Trans>Crear cuenta</Trans>
        </CardTitle>
        <CardDescription>
          <Trans>Regístrate con tu email y una contraseña.</Trans>
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form noValidate onSubmit={handleSubmit}>
          <FieldGroup>
            {fields.map((field) => {
              const error = errorFor(field.name)
              const id = `register-${field.name}`
              return (
                <Field key={field.name} data-invalid={error ? true : undefined}>
                  <FieldLabel htmlFor={id}>{field.label}</FieldLabel>
                  <Input
                    id={id}
                    name={field.name}
                    type={field.type}
                    autoComplete={field.autoComplete}
                    maxLength={field.maxLength}
                    value={values[field.name]}
                    aria-invalid={error ? true : undefined}
                    aria-describedby={error ? `${id}-error` : undefined}
                    onChange={(e) => update(field.name, e.target.value)}
                    onBlur={() => setTouched((t) => ({ ...t, [field.name]: true }))}
                  />
                  <FieldError id={`${id}-error`}>{error}</FieldError>
                </Field>
              )
            })}
            {unexpectedError && (
              <p role="alert" className="text-sm text-destructive">
                <Trans>No se pudo crear la cuenta. Inténtalo de nuevo.</Trans>
              </p>
            )}
            <Button type="submit" size="lg" className="w-full" disabled={mutation.isPending}>
              {mutation.isPending ? <Trans>Creando cuenta…</Trans> : <Trans>Crear cuenta</Trans>}
            </Button>
            <FieldDescription className="text-center">
              <Trans>
                ¿Ya tienes cuenta? <Link to="/login">Inicia sesión</Link>
              </Trans>
            </FieldDescription>
          </FieldGroup>
        </form>
      </CardContent>
    </Card>
  )
}
