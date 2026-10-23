import { Trans } from '@lingui/react/macro'
import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { Button } from '@/components/ui/button'
import { useLogout } from '@/features/auth/api'

export const Route = createFileRoute('/_auth/')({
  component: Home,
})

function Home() {
  const { me } = Route.useRouteContext()
  const name = me.displayName
  const navigate = useNavigate()
  const logout = useLogout()

  return (
    <main className="flex min-h-svh flex-col items-center justify-center gap-4 p-4">
      <h1 className="text-2xl font-semibold">
        <Trans>Hola, {name}</Trans>
      </h1>
      <p className="text-muted-foreground">{me.orgName}</p>
      <Button
        variant="outline"
        disabled={logout.isPending}
        onClick={() =>
          logout.mutate(undefined, { onSuccess: () => navigate({ to: '/login', replace: true }) })
        }
      >
        <Trans>Cerrar sesión</Trans>
      </Button>
    </main>
  )
}
