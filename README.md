# Poshan for Life — Admin/Doctor Portal

Rebuild of the Poshan for Life health platform's staff portal as a two-tier app:

- **backend/** — Java 21, Spring Boot 3.4, PostgreSQL, Flyway, JWT auth, MapStruct, Swagger UI
- **frontend/** — Angular 18, standalone components, Angular Material

Patients use a separate mobile app; this portal serves ADMIN and DOCTOR roles only
(the PATIENT role still exists in the data model).

## Running locally

Backend (needs a local PostgreSQL `poshan_api` database — dedicated to this rebuild; the plain
`poshan` DB holds the original Next.js app's Prisma schema, keep Flyway out of it — or point
`DB_URL` at Supabase):

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

## Database

Normal case (a fresh local Postgres, or any database Flyway has managed itself since V1): no
extra config needed, `spring.flyway.*` defaults in `application.yml` apply as-is.

Pointing this backend at a database where the schema was pre-applied *outside* Flyway — e.g. via
a manual SQL script run in Supabase's SQL Editor — needs a one-time baseline so Flyway doesn't
try to replay migrations against tables that already exist. Set these two env vars for the
**first startup only**:

```bash
SPRING_FLYWAY_BASELINE_ON_MIGRATE=true
SPRING_FLYWAY_BASELINE_VERSION=16   # highest migration version already applied by hand
```

Then **unset both** — they're a one-time bootstrap step, not a permanent setting, and leaving
`baseline-on-migrate` on masks real future migration-mismatch errors.

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

### Authentication (feature: auth — done)

- `POST /api/v1/auth/login` `{ email, password }` → `{ accessToken, refreshToken, user }`
  (bad credentials → 401 `AUTH_REQUIRED`, same message for unknown email vs wrong password;
  rate-limited 10/min/IP)
- `POST /api/v1/auth/refresh` — rotates: presented refresh token is revoked, a new pair is returned;
  replaying a rotated/revoked token → 401
- `POST /api/v1/auth/logout` — revokes the refresh token server-side (idempotent)
- Access token: HS256 JWT, 15 min (claims `sub`=user id, `email`, `role`). Refresh token: opaque
  random 256-bit value, 14 days, only its SHA-256 hash stored (`refresh_tokens` table)
- Role enforcement: `@AdminOnly` / `@AdminOrDoctor` meta-annotations (wrap `@PreAuthorize`);
  DOCTOR row-level scoping happens in services via the `AuthenticatedUser` principal
  (`@AuthenticationPrincipal AuthenticatedUser user`)
- Rate limiting: annotate any controller method with `@RateLimit(requests, windowSeconds)` —
  per-IP bucket4j bucket, 429 `RATE_LIMIT_EXCEEDED` (in-memory; swap for Redis ProxyManager when scaling out)
- Local/dev seed user (never prod): `admin@poshanforlife.com` / `Admin@123` via `DevUserSeeder`
- Frontend token strategy: access token + user in memory only; refresh token in localStorage,
  rotated on every use and revocable server-side. Silent refresh on app init (`APP_INITIALIZER`)
  keeps sessions across reloads; interceptor retries a 401 once after a silent refresh, then
  forces logout with a "session expired" toast. Hardening option later: backend-set httpOnly
  cookie for the refresh token (only `AuthService` changes).
- Wrong-role navigation lands on `/forbidden` (403 page); unauthenticated → `/login?returnUrl=…`

### User management (feature: users — done)

- `GET/POST /api/v1/users`, `GET/PATCH/DELETE /api/v1/users/{id}`, plus
  `/{id}/password`, `/{id}/assign-patients`, `/{id}/patients` (read counterpart added so the
  UI can pre-select current assignments), `/{id}/notification-prefs`
- List (ADMIN): `role`, `search` (name/email contains), `page` (1-based), `limit`; meta in envelope
- Create (ADMIN): name ≥ 2, valid unique email (409 `EMAIL_CONFLICT`), password ≥ 8 chars with a
  digit, role, phone? → 201
- Update: ADMIN may change name/phone/role/isActive/dateOfBirth; a non-admin self-update may only
  change name/phone — sending any other field is **rejected with 422 VALIDATION_ERROR**
  (documented choice, not silently ignored)
- DELETE = soft delete (isActive=false) + refresh-token revocation; deactivated users can't log
  in or refresh (same opaque 401 as bad credentials); reactivate via `PATCH { isActive: true }`
- Password change: self needs correct `currentPassword`; ADMIN acting on another user doesn't;
  confirmPassword must match; all refresh tokens revoked afterwards
- assign-patients (ADMIN): replaces ALL of a doctor's `doctor_patients` rows in one transaction;
  target must be a DOCTOR, ids must be existing PATIENTs
- notification-prefs: jsonb column, partial-merge semantics (null = keep)
- Password hash never leaves the API (`UserDetailDto` has no password field)
- Frontend `/users` (admin-only route guard + nav item hidden): server-paginated table with
  debounced search + role filter, add/edit dialog (shared form, 409 surfaced on the email field,
  VALIDATION_ERROR details mapped to field errors via `core/utils/form-errors.ts`),
  change/reset-password dialog, assign-patients dialog (pre-selected, filterable multi-select),
  deactivate confirm dialog + reactivate action

### Patient management (feature: patients — done)

- `GET/POST /api/v1/patients`, `GET/PATCH /api/v1/patients/{id}`, `DELETE` (ADMIN, soft),
  `GET /api/v1/patients/stats`
- Data model: patient = User(role=PATIENT) + 1:1 `patient_profiles` (gender, bloodGroup,
  heightCm, emergencyContact, medicalHistory, doctorNotes) — dateOfBirth stays on users (V3);
  minimal `health_records` table added now so stats/overview are real (extended later by the
  health-records prompt); profile is created lazily for patients that predate the feature
- DOCTOR scoping enforced on EVERY endpoint (list ignores foreign doctorId, get/patch check the
  DoctorPatient link, stats aggregate only assigned patients); doctorId list filter is ADMIN-only
- Create: password optional → temp password generated (returned once as `tempPassword`; email
  delivery stubbed for the notifications prompt); DOCTOR callers are auto-assigned and cannot
  assign to another doctor; heightCm must be positive
- Stats: totalPatients, activeThisMonth (created or with a health record this calendar month),
  avg BMI + avg body-fat from each patient's latest record (BMI = weight / height²)
- Frontend `/patients`: stats cards, server-paginated table (assigned-doctor column is
  admin-only), debounced search, add/edit dialog (Account + Medical sections, temp-password
  one-time dialog), deactivate = confirm-dialog soft delete (admin-only); detail page
  `/patients/:id` with profile header + Overview / Programmes (stub, prompt 06) / Reports (stub)
  / Health Records tabs
- Gotcha fixed along the way: `(:param is null or lower(x) like ...)` JPQL breaks on Postgres
  (null String binds as bytea → "lower(bytea) does not exist"); repo search params are now
  non-null with `''` = no filter — applies to all future search queries

### Doctor–patient assignments (feature: assignments — done)

- `GET/POST /api/v1/assignments` (+ `?doctorId=` / `?patientId=` filters),
  `DELETE /api/v1/assignments/{id}` — all ADMIN-only; fine-grained single-pair CRUD
  complementing the bulk replace-all flow at `POST /users/{id}/assign-patients`
- Duplicate pair → 409 with new code `ASSIGNMENT_CONFLICT`; role mismatches → 422; delete
  removes only the link (patient + history kept)
- Creating an assignment records an in-app Notification for the doctor ("You've been assigned
  patient X") in the new minimal `notifications` table (V5 — extended later by the
  notifications prompt, same pattern as `health_records`)
- Frontend: "Assigned doctors" panel on the patient detail Overview (admins: searchable
  autocomplete add + confirm-dialog remove, header refreshes via `changed` output; doctors:
  read-only list); "Manage patients" dialog on DOCTOR rows in `/users` for individual
  add/remove (bulk dialog relabeled "Replace patient list"); success toasts state the doctor
  was notified

## Feature status

Done: auth · users · patients · assignments · catalogue · products · orders · transactions ·
reports · leads · dashboard · notifications · appointments · documents (invoices/estimates) ·
health records · gamification & badges.

A native Kotlin/Compose Android app (`android/`) also exists for the PATIENT role and mirrors
much of the practitioner/admin surface — see the AN-XX prompt series.
