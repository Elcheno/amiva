# amiva · backend

API de amiva y toda la lógica de negocio. Volver al [README principal](../README.md).

## Stack

- Java 25, Spring Boot 4.1 (Web MVC, Security, Validation, Actuator)
- JPA para `amiva_core`, JdbcTemplate para `amiva_data`
- Flyway (una instancia por base)
- Postgres 18

## Requisitos

- Java 25
- Postgres levantados con `docker compose up -d` desde la raíz
- `.env` en la raíz con las contraseñas (ver `../.env.example`)

## Comandos

```bash
./mvnw spring-boot:run    # arrancar (puerto 8080)
./mvnw test               # tests (necesitan los Postgres levantados)
./mvnw package            # generar el jar
```

## Configuración

`application.properties` importa el `.env` de la raíz. No contiene contraseñas.

| Variable | Por defecto |
|---|---|
| `AMIVA_CORE_DB_URL` | `jdbc:postgresql://localhost:5432/amiva_core` |
| `AMIVA_CORE_DB_USER` | `amiva_core` |
| `AMIVA_CORE_DB_PASSWORD` | obligatoria |
| `AMIVA_DATA_DB_URL` | `jdbc:postgresql://localhost:5433/amiva_data` |
| `AMIVA_DATA_DB_USER` | `amiva_data` |
| `AMIVA_DATA_DB_PASSWORD` | obligatoria |

## Bases de datos

| Base | Contenido | Acceso | Migraciones |
|---|---|---|---|
| `amiva_core` | Usuarios, organizaciones, formularios, informes… | JPA (`@CoreDb`, primaria) | `db/migration-core` |
| `amiva_data` | `submission` y `answer` | JdbcTemplate (`@DataDb`) | `db/migration-data` |

- Sin claves foráneas ni joins entre bases: la integridad la garantiza el backend.
- `submission` y `answer` son inmutables: los triggers bloquean UPDATE, DELETE y TRUNCATE.
- IDs con `uuidv7()`.
- Las migraciones se ejecutan solas al arrancar, antes de JPA. Nunca se edita una migración ya aplicada: se crea una nueva `V<n>__descripcion.sql`.

## Estructura

```
src/main/java/com/amiva/
  AmivaApplication.java
  config/            datasources, Flyway, seguridad
  <módulo>/
    api/             controladores y DTOs (prefijo /api automático, ver WebConfig)
    application/     casos de uso y servicios
    domain/          modelo y reglas de negocio
    infrastructure/  repositorios y clientes externos
```

Módulos: `users`, `fields`, `forms`, `answers`, `analysis`, `widgets`, `reports`, `pdf`, `activity`, `notifications`, `shared`.

## Endpoints públicos

- `GET /actuator/health`: estado de la app y de ambas bases.
- `GET /api/auth/csrf`: emite la cookie `XSRF-TOKEN`. Las peticiones que modifican datos la reenvían en `X-XSRF-TOKEN`.
- `POST /api/auth/register`: crea usuario y su organización propia (201; 400 con `errors` por campo; 409 si el email existe).
- `POST /api/auth/login`: `{email, password}`. Abre sesión (cookie `JSESSIONID`, sesión HTTP en memoria) y devuelve lo mismo que `/me` (200; 400 con `errors`; 401 genérico si el email o la contraseña no coinciden).

## Sesión

- `GET /api/auth/me`: usuario y organización de la sesión (`userId`, `email`, `displayName`, `orgId`, `orgName`).
- `POST /api/auth/logout`: invalida la sesión y borra la cookie (204). Exige CSRF como cualquier POST.
- Sin sesión, cualquier ruta protegida responde 401 con ProblemDetail (nunca redirige).
