import { Trans } from '@lingui/react/macro'
import { createFileRoute } from '@tanstack/react-router'
import { Button } from '@/components/ui/button'

export const Route = createFileRoute('/')({
  component: Home,
})

function Home() {
  return (
    <main className="flex min-h-svh flex-col items-center justify-center gap-4">
      <h1 className="text-2xl font-semibold">
        <Trans>Bienvenido a amiva</Trans>
      </h1>
      <Button>
        <Trans>Empezar</Trans>
      </Button>
    </main>
  )
}
