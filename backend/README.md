# amiva · backend

API de amiva y toda la lógica de negocio. Volver al [README principal](../README.md).

## Stack

- Java 25, Spring Boot 4.1 (Web MVC, Security, Validation, Actuator, Mail)
- JPA para `amiva_core`, JdbcTemplate para `amiva_data`
- Flyway (una instancia por base)
- Postgres 18

## Requisitos

- Java 25
- Postgres y Mailpit levantados con `docker compose up -d` desde la raíz (o `docker-compose up -d`)
- `.env` en la raíz con las contraseñas (ver `../.env.example`)

## Comandos

```bash
./mvnw spring-boot:run    # arrancar (puerto 8080)
./mvnw test               # tests (necesitan los Postgres levantados; Mailpit no)
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
| `AMIVA_MAIL_HOST` | `localhost` |
| `AMIVA_MAIL_PORT` | `1025` (Mailpit) |
| `AMIVA_MAIL_FROM` | `no-reply@amiva.local` |
| `AMIVA_APP_BASE_URL` | `http://localhost:5173` (URL del front para los enlaces de los emails) |

Emails en local: Mailpit los captura sin enviarlos. Bandeja en http://localhost:8025.

## Bases de datos

| Base | Contenido | Acceso | Migraciones |
|---|---|---|---|
| `amiva_core` | Usuarios, organizaciones, formularios, informes… | JPA (`@CoreDb`, primaria) | `db/migration-core` |
| `amiva_data` | `submission` y `answer` | JdbcTemplate (`@DataDb`) | `db/migration-data` |

- Sin claves foráneas ni joins entre bases: la integridad la garantiza el backend.
- `submission` y `answer` son inmutables: los triggers bloquean UPDATE, DELETE y TRUNCATE.
- `activity` (core) también es inmutable con los mismos triggers. Una organización con actividad no se puede borrar (FK RESTRICT). En tests, `TestCleanup` desactiva el trigger solo dentro de su transacción.
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
- `POST /api/auth/login`: `{email, password}`. Abre sesión (cookie `JSESSIONID`, sesión HTTP en memoria) y devuelve lo mismo que `/me` (200; 400 con `errors`; 401 genérico si el email o la contraseña no coinciden). Se puede entrar sin haber verificado el email.
- `POST /api/auth/verify-email`: `{token}` del enlace del email. Marca el email como verificado (204; 400 con `reason`: `INVALID`, `EXPIRED` o `USED`). Exige CSRF.

## Sesión

- `GET /api/auth/me`: usuario y organización de la sesión (`userId`, `email`, `displayName`, `orgId`, `orgName`, `emailVerified`).
- `POST /api/auth/verify-email/resend`: nuevo email de verificación; los enlaces anteriores sin usar dejan de valer (204; 409 si ya está verificado; 429 con `Retry-After` si no ha pasado 1 minuto desde el último).
- `POST /api/auth/logout`: invalida la sesión y borra la cookie (204). Exige CSRF como cualquier POST.
- Sin sesión, cualquier ruta protegida responde 401 con ProblemDetail (nunca redirige).

## Organización

Roles por organización: `OWNER` (uno, no transferible), `ADMIN`, `MEMBER`. Si la organización no existe o no eres miembro: 404.

Permisos: cada caso de uso llama a `OrgPermissionService.require(actor, org, OrgPermission.X)` al empezar. Qué rol tiene cada permiso se decide solo en `OrgRole.has`. Sin permiso: 403 ProblemDetail con `permission` (p. ej. `"MANAGE_ROLES"`). Acción nueva = valor nuevo en `OrgPermission` + su caso en `OrgRole.has` + test 403.

- `GET /api/orgs`: organizaciones donde soy miembro (`orgId`, `name`, `role`, `own`), la propia primero y luego por nombre. Sin permiso específico: solo devuelve las tuyas.
- `GET /api/orgs/{orgId}`: lo mismo para una organización (200; 404 si no existe o no eres miembro). Cualquier miembro.
- `GET /api/orgs/{orgId}/members`: miembros (`userId`, `displayName`, `email`, `role`, `createdAt`), OWNER primero y luego por nombre. Cualquier miembro.
- `PATCH /api/orgs/{orgId}/members/{userId}`: `{"role": "ADMIN" | "MEMBER"}`, devuelve el miembro (200; 400 rol no válido u OWNER; 403 si no eres el OWNER; 404 si no es miembro; 409 si es el OWNER). Solo el OWNER.

## Invitaciones

Solo a usuarios ya registrados. Invitan el OWNER y los ADMIN (`INVITE_MEMBERS`); invitar con rol `ADMIN` exige además `MANAGE_ROLES` (solo el OWNER). Mismos 404/403 que el resto de la organización.

- `GET /api/orgs/{orgId}/invitable-users?q=…`: usuarios que aún no son miembros cuyo `username` empieza por `q` o cuyo nombre contiene `q` (sin distinguir mayúsculas; `%`, `_` y `\` se buscan como texto). `q` con menos de 3 caracteres: `[]`. Máximo 10, por nombre y luego username. Devuelve `userId`, `username`, `displayName` y `pendingInvitation`; nunca el email. Sin índice por ahora.
- `POST /api/orgs/{orgId}/invitations`: `{userId, role: "ADMIN" | "MEMBER"}`. 201 con la invitación (`invitationId`, `userId`, `username`, `displayName`, `role`, `invitedByName`, `createdAt`, `updatedAt`); 400 rol no válido u OWNER o sin `userId`; 403 con `permission`; 404 si el usuario no existe; 409 si ya es miembro (también uno mismo); 503 si no se pudo enviar el email.
- `GET /api/orgs/{orgId}/invitations`: pendientes, de más reciente a más antigua (`updatedAt`).

Reglas:

- Token como el de verificación (32 bytes base64url, en BD solo el SHA-256 en hex). Las pendientes no caducan.
- Una sola pendiente por usuario y organización. Volver a invitar reutiliza la fila: token, rol e invitador nuevos, `updated_at` actualizado y otro email. El enlace anterior deja de valer.
- El email se envía dentro de la transacción (no `AFTER_COMMIT`, a diferencia del de verificación): si falla el SMTP, no queda invitación ni actividad y la API responde 503.
- Enlace: `{AMIVA_APP_BASE_URL}/invite/<token>`.
- Actividad `MEMBER_INVITED` en cada envío (también al reenviar), con `memberName` y `role`.

## Actividad

Registro append-only por organización (`activity`): autor (`actor_id` + copia de `actor_name`, `anonymous` si es superadmin), `action`, `target_type` + `target_id`, `details` (JSONB con nombres copiados y valores) y `occurred_at`. El texto lo monta el front a partir de `action` + `details`.

Los casos de uso publican un evento de dominio (`OrganizationCreated`, `MemberRoleChanged`…) con `ApplicationEventPublisher`; `ActivityRecorder` lo escucha con `@EventListener` dentro de la misma transacción (`MANDATORY`): si falla el registro, falla la acción. Acción nueva = evento + valor en `ActivityAction` + método en `ActivityRecorder` + test.

Acciones actuales: `ORG_CREATED` (registro), `MEMBER_ROLE_CHANGED` (solo si el rol cambia de verdad), `MEMBER_INVITED` (invitación enviada o reenviada).

## Verificación del email

Al registrarse, `RegistrationService` publica `UserRegistered`. `VerificationEmailListener` lo escucha tras el commit (`@TransactionalEventListener(AFTER_COMMIT)`, síncrono): `EmailVerificationService.issue` crea el token en su propia transacción (`REQUIRES_NEW`) y `VerificationMailer` (SMTP con `JavaMailSender`) envía el email. Si falla el envío solo se registra el error: el registro no falla y el usuario puede pedir otro.

- Token: 32 bytes aleatorios en base64url. En BD (`email_verification_token`) solo su SHA-256 en hex. Un solo uso, caduca a las 24 h.
- El enlace apunta al front (`{AMIVA_APP_BASE_URL}/verify-email?token=…`), que hace el POST. Ningún GET tiene efectos.
- Reenvío limitado a 1 por minuto por usuario según el `created_at` del último token en BD.
- La hora sale del bean `Clock` (`ClockConfig`) para poder fijarla en tests.
- En tests no se usa Mailpit: `CapturingMailSender` sustituye a `JavaMailSender` y guarda los mensajes en memoria.
