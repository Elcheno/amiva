import { useLingui } from '@lingui/react/macro'
import type { OrgRole } from './api'

// Texto del rol como string (sirve también dentro de <option>)
export function useRoleLabel() {
  const { t } = useLingui()
  return (role: OrgRole) => {
    switch (role) {
      case 'OWNER':
        return t`Propietario`
      case 'ADMIN':
        return t`Admin`
      case 'MEMBER':
        return t`Miembro`
    }
  }
}
