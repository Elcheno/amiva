# amiva

Plataforma para que las organizaciones creen formularios dinámicos, recojan respuestas numéricas y las analicen con fórmulas sobre series temporales, widgets e informes imprimibles.

## Partes del proyecto

| Carpeta | Descripción | Stack | README |
|---|---|---|---|
| `backend/` | API y toda la lógica de negocio | Java 25, Spring Boot 4.1 | [backend/README.md](backend/README.md) |
| `frontend/` | Aplicación web (SPA) | React, Vite, TypeScript | [frontend/README.md](frontend/README.md) |
| `engine/` | Cálculo de fórmulas, sin estado | Python 3.12 | [engine/README.md](engine/README.md) |
| `docs/` | Documentación pública y landing | Astro Starlight | Pendiente |
| `infra/` | Despliegue e infraestructura | — | Pendiente |

## Arquitectura

- El backend es el único que accede a bases de datos y servicios (engine, S3, Gotenberg, caché).
- Dos bases Postgres 18:
  - `amiva_core`: datos de negocio.
  - `amiva_data`: envíos y respuestas, inmutables.
- El engine solo recibe fórmulas con series ya preparadas y devuelve el resultado.

## Requisitos

- Docker con Compose
- Java 25 (backend)
- Bun (frontend)
- Python 3.12+ (engine)

## Arranque rápido

```bash
cp .env.example .env              # rellenar las contraseñas
docker compose up -d              # o docker-compose up -d
cd backend && ./mvnw spring-boot:run
```

Comprobación: http://localhost:8080/actuator/health

## Configuración

Todas las variables se definen en el `.env` de la raíz (ver `.env.example`). El `.env` nunca se sube al repositorio.
