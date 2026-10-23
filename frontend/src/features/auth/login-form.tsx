import { useState, type FormEvent, type ReactNode } from 'react'
import { useLingui } from '@lingui/react'
import { Trans } from '@lingui/react/macro'
import { Link, useNavigate } from '@tanstack/react-router'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { ApiError } from '@/lib/api'
import { useLogin } from './api'
import { validateLogin, type LoginField, type LoginValues } from './login-validation'
import { safeRedirect } from './redirect'
import { LIMITS } from './register-validation'

const EMPTY: LoginValues = { email: '', password: '' }

// Errores por campo que devuelve el backend en el ProblemDetail (400)
function serverFieldErrors(error: unknown): Partial<Record<LoginField, string>> {
  if (!(error instanceof ApiError) || error.status !== 400) return {}
  const errors = error.problem?.errors
  return typeof errors === 'object' && errors !== null
    ? (errors as Partial<Record<LoginField, string>>)
    : {}
}

// redirectTo: ruta interna a la que volver tras iniciar sesión (se valida otra vez aquí)
export function LoginForm({ redirectTo }: { redirectTo?: string }) {
  const { i18n } = useLingui()
  const navigate = useNavigate()
  const mutation = useLogin()
  const [values, setValues] = useState<LoginValues>(EMPTY)
  const [touched, setTouched] = useState<Partial<Record<LoginField, boolean>>>({})
  const [submitted, setSubmitted] = useState(false)
  // Campos editados desde el último envío: ya no se muestra su error del servidor
  const [editedSinceSubmit, setEditedSinceSubmit] = useState<Partial<Record<LoginField, boolean>>>(
    {},
  )

  const clientErrors = validateLogin(values)
  const serverErrors = serverFieldErrors(mutation.error)
  const invalidCredentials = mutation.error instanceof ApiError && mutation.error.status === 401

  function errorFor(field: LoginField): string | undefined {
    const client = clientErrors[field]
    if (client && (touched[field] || submitted)) return i18n._(client)
    if (editedSinceSubmit[field]) return undefined
    return serverErrors[field]
  }

  function update(field: LoginField, value: string) {
    setValues((v) => ({ ...v, [field]: value }))
    setEditedSinceSubmit((e) => ({ ...e, [field]: true }))
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitted(true)
    if (Object.keys(clientErrors).length > 0) return
    setEditedSinceSubmit({})
    mutation.mutate(
      { email: values.email.trim(), password: values.password },
      { onSuccess: () => navigate({ href: safeRedirect(redirectTo), replace: true }) },
    )
  }

  const unexpectedError =
    mutation.isError && !invalidCredentials && Object.keys(serverErrors).length === 0

  const fields: {
    name: LoginField
    label: ReactNode
    type: string
    autoComplete: string
    maxLength: number
  }[] = [
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
      autoComplete: 'current-password',
      maxLength: LIMITS.passwordMax,
    },
  ]

  return (
    <Card className="w-full max-w-sm">
      <CardHeader>
        <CardTitle>
          <Trans>Iniciar sesión</Trans>
        </CardTitle>
        <CardDescription>
          <Trans>Entra con tu email y tu contraseña.</Trans>
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form noValidate onSubmit={handleSubmit}>
          <FieldGroup>
            {fields.map((field) => {
              const error = errorFor(field.name)
              const id = `login-${field.name}`
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
            {invalidCredentials && (
              <p role="alert" className="text-sm text-destructive">
                <Trans>Email o contraseña incorrectos</Trans>
              </p>
            )}
            {unexpectedError && (
              <p role="alert" className="text-sm text-destructive">
                <Trans>No se pudo iniciar sesión. Inténtalo de nuevo.</Trans>
              </p>
            )}
            <Button type="submit" size="lg" className="w-full" disabled={mutation.isPending}>
              {mutation.isPending ? <Trans>Entrando…</Trans> : <Trans>Iniciar sesión</Trans>}
            </Button>
            <FieldDescription className="text-center">
              <Trans>
                ¿No tienes cuenta? <Link to="/register">Regístrate</Link>
              </Trans>
            </FieldDescription>
          </FieldGroup>
        </form>
      </CardContent>
    </Card>
  )
}
