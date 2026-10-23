import type { MessageDescriptor } from '@lingui/core'
import { msg } from '@lingui/core/macro'
import { EMAIL_PATTERN, LIMITS } from './register-validation'

export type LoginField = 'email' | 'password'
export type LoginValues = Record<LoginField, string>
export type LoginErrors = Partial<Record<LoginField, MessageDescriptor>>

// Sin mínimo de contraseña: eso solo aplica al crearla
export function validateLogin(values: LoginValues): LoginErrors {
  const errors: LoginErrors = {}
  const { email: maxEmail } = LIMITS
  const email = values.email.trim()

  if (!email) errors.email = msg`Escribe tu email`
  else if (email.length > LIMITS.email) errors.email = msg`Máximo ${maxEmail} caracteres`
  else if (!EMAIL_PATTERN.test(email)) errors.email = msg`El email no es válido`

  if (!values.password) errors.password = msg`Escribe tu contraseña`

  return errors
}
