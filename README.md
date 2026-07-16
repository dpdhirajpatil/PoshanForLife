# Poshan for Life — Admin/Doctor Portal

Rebuild of the Poshan for Life health platform's staff portal as a two-tier app:

- **backend/** — Java 21, Spring Boot 3.4, PostgreSQL, Flyway, JWT auth, MapStruct, Swagger UI
- **frontend/** — Angular 18, standalone components, Angular Material

Patients use a separate mobile app; this portal serves ADMIN and DOCTOR roles only
(the PATIENT role still exists in the data model).

## Running locally

Backend (needs a local PostgreSQL `poshan` database, or point `DB_URL` at Supabase):

```bash
cd backend
./mvnw spring-boot:run          # local profile by default; see .env.example
```

- API base: `http://localhost:8080/api/v1`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Liveness: `GET /api/v1/health` (unauthenticated)

Frontend:

```bash
cd frontend
npm install
npm start                       # http://localhost:4200, proxies nothing — calls :8080 directly
```

## Conventions (established in prompt 0)

### API envelope

Every endpoint returns the envelope the original Next.js app used:

```jsonc
// success
{ "success": true, "data": { }, "meta": { "total": 42, "page": 2, "limit": 10 } } // meta only on lists
// error
{ "success": false, "error": "human message", "code": "RESOURCE_NOT_FOUND", "details": { } }
```

Error codes: `AUTH_REQUIRED` 401 · `INSUFFICIENT_ROLE` 403 · `VALIDATION_ERROR` 400/422 ·
`RESOURCE_NOT_FOUND` 404 · `EMAIL_CONFLICT` 409 · `RATE_LIMIT_EXCEEDED` 429 ·
`OCR_FAILED` 422 · `STORAGE_ERROR` 500 · `INTERNAL_ERROR` 500 (fallback, new).

Backend: controllers return `ApiResponse.ok(...)`; services throw `ApiException` subclasses;
`GlobalExceptionHandler` + the security handlers produce the error envelope.
Frontend: `ApiService` unwraps `data` (`getPaged` keeps `meta`) and rethrows normalized `ApiError`s.

### Backend layout

`com.poshanforlife.api` → `controller / service / repository / dto / mapper / entity / security / config / exception`.
Entities extend `BaseEntity` (UUID id + audited createdAt/updatedAt). Schema changes go in
`src/main/resources/db/migration/V<n>__*.sql` (Flyway; `ddl-auto` stays `validate` in all profiles).
MapStruct mappers default to the Spring component model. Profiles: `local` (defaults baked in),
`dev`/`prod` (all secrets from env; Swagger disabled in prod).

### Frontend layout

- `core/` — envelope models, `ApiService`, `AuthService` (signal-based session), `ToastService`,
  `authInterceptor` (bearer token + global 401 redirect / 403 toast), guards
  (`authGuard`, `adminOnlyGuard`, `adminOrDoctorGuard`)
- `layout/ShellComponent` — sidenav + topbar; Users nav item is admin-only
- `features/<domain>/` — one folder per domain, lazy-loaded via `loadChildren` +
  a default-exported routes file; replace the placeholder page component per feature prompt

## Feature prompts still to come

users · patients · catalogue · orders · transactions · reports · leads · dashboard · notifications
