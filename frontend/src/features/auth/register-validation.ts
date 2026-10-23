import type { MessageDescriptor } from '@lingui/core'
import { msg } from '@lingui/core/macro'

// Mismos límites que RegisterRequest en el backend
export const LIMITS = {
  displayName: 150,
  email: 320,
  passwordMin: 8,
  passwordMax: 72, // BCrypt: 72 bytes como máximo
} as const

export type RegisterField = 'displayName' | 'email' | 'password'
export type RegisterValues = Record<RegisterField, string>
export type RegisterErrors = Partial<Record<RegisterField, MessageDescriptor>>

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+$/
const utf8Length = (value: string) => new TextEncoder().encode(value).length

export function validateRegister(values: RegisterValues): RegisterErrors {
  const errors: RegisterErrors = {}
  const { displayName: maxName, email: maxEmail, passwordMin, passwordMax } = LIMITS
  const displayName = values.displayName.trim()
  const email = values.email.trim()

  if (!displayName) errors.displayName = msg`Escribe tu nombre`
  else if (displayName.length > LIMITS.displayName)
    errors.displayName = msg`Máximo ${maxName} caracteres`

  if (!email) errors.email = msg`Escribe tu email`
  else if (email.length > LIMITS.email) errors.email = msg`Máximo ${maxEmail} caracteres`
  else if (!EMAIL_PATTERN.test(email)) errors.email = msg`El email no es válido`

  if (values.password.length < passwordMin) errors.password = msg`Mínimo ${passwordMin} caracteres`
  else if (values.password.length > passwordMax || utf8Length(values.password) > passwordMax)
    errors.password = msg`La contraseña es demasiado larga`

  return errors
}
