# amiva · frontend

SPA en React + Vite + TypeScript estricto. Gestor de paquetes: bun.

## Stack

- Router: TanStack Router (rutas por ficheros en `src/routes`)
- Datos: TanStack Query + `src/lib/api.ts` (todo bajo `/api`)
- UI: Tailwind v4 + shadcn/ui (`src/components/ui`)
- i18n: Lingui (catálogos `.po` en `src/locales`)
- Tests: Vitest + Testing Library (jsdom)
- Calidad: ESLint + Prettier

## Comandos

```bash
bun install
bun run dev            # http://localhost:5173 (proxy /api → localhost:8080)
bun run build
bun run lint
bun run format         # format:check para solo comprobar
bun run test           # modo watch; test:run para una pasada
bun run i18n:extract   # actualiza los .po con los textos nuevos
```

Usa `bun run test`, no `bun test` (este último lanza el runner de bun, no Vitest).

## Estructura

```
src/
  routes/       una ruta por fichero (routeTree.gen.ts se genera solo)
  features/     lógica por dominio (forms, answers, widgets…)
  components/   compartidos; ui/ es de shadcn
  lib/          api, query-client, i18n, utils
  locales/      catálogos de traducción
  test/         setup y helpers (renderWithProviders)
```
