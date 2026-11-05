# GMS Deployment Readiness Assessment

**Date:** 2026-11-03
**Assessor:** Engineering (automated audit)
**Branch baseline:** `master` at latest merge; V10 backup at `perf/v10-index-the-measured-reads` (`61c8ce1`)
**Scope:** Full-stack deployment readiness covering infrastructure, security, database, operations and rollback

---

## 1. Executive Summary

**Overall Status: NOT READY FOR PRODUCTION DEPLOYMENT**

**Go/No-Go Recommendation: NO-GO**

The GMS platform has undergone significant security remediation (Stages 1-3), tenant isolation hardening (C-1 through C-8), and infrastructure modernisation (Infra-M0). However, several critical blockers prevent production deployment:

- **V10 migration unverified on PostgreSQL 17** (the DoD-2 target)
- **Three performance target breaches** remain open at 8-wide concurrency (NFR-02)
- **No production CD pipeline** exists (deploy-backend.yml was retired; OCI deployment is unimplemented)
- **No TLS termination** material anywhere in the repository
- **No backup/restore procedure** has been executed or tested
- **Neon credential rotation** has not been performed (SEC-01)
- **17 structural skips** in live tenancy verification require owner acceptance

The application is deployment-ready at the **local development** and **CI verification** level. Production deployment requires resolving the blockers enumerated in Section 14.

---

## 2. Infrastructure Inventory

### 2.1 Docker Compose Topology

The production-like topology is defined in `docker-compose.yml` at the repository root. Three services compose the stack:

| Service | Image / Build | Internal Port | Published Port | Network | Healthcheck |
|---|---|---|---|---|---|
| `postgres` | `postgres:16-alpine` | 5432 | `127.0.0.1:5433:5432` | `gms-network` (bridge) | `pg_isready -U gms_user -d gms` (10s interval, 5s timeout, 5 retries) |
| `backend` | `./gms_backend/Dockerfile` (multi-stage: `maven:3.9-eclipse-temurin-21` build, `eclipse-temurin:21-jre-alpine` runtime) | 8080 | `8080:8080` | `gms-network` (bridge) | `wget --spider http://localhost:8080/actuator/health/readiness` (30s interval, 10s timeout, 5 retries, 60s start period) |
| `frontend` | `./gms_frontend/Dockerfile` (multi-stage: `node:20-alpine` build, `nginx:alpine` runtime) | 80 (nginx) | `3000:80` | `gms-network` (bridge) | `wget --spider http://localhost:80/` (30s interval, 5s timeout, 3 retries, 10s start period) |

**Service dependencies:**
- `backend` depends on `postgres` with `condition: service_healthy`
- `frontend` depends on `backend` with `condition: service_healthy`

**Volumes:**
- `postgres_data` (local driver) — persistent database storage

**Network:**
- `gms-network` — bridge driver, all three services attached

**Critical observations:**
- Database port is loopback-only (`127.0.0.1:5433`), correcting the prior `0.0.0.0:5432` exposure (INF-02)
- Backend port `8080` is published on all interfaces — this is required for the nginx proxy but represents a direct API exposure
- Frontend port `3000` is published on all interfaces
- No resource limits (`memory`, `cpus`) are configured on any service
- No `restart` policy is set on any service (defaults to `no`)
- No `cap_drop`, `no-new-privileges`, or `read_only` security options are applied
- Backend runtime user is `spring:spring` (non-root, created in Dockerfile)

### 2.2 CI Environment

The CI pipeline (`.github/workflows/ci.yml`) runs on `ubuntu-latest` (GitHub-hosted) with the following services:

| Component | Version | Purpose |
|---|---|---|
| PostgreSQL (sidecar) | `postgres:15` | Database for unit tests, live tenancy suites, and Playwright E2E |
| Java | `eclipse-temurin:21` | Backend compilation and test execution |
| Node.js | `22` | Frontend lint, test, and build |
| OpenTofu | `1.13.2` | Infrastructure definition validation |

**CI jobs (6 total):**
1. `backend` — `mvn -B clean verify` with ArchTest gates, uploads jar artifact
2. `frontend` — `npm ci`, lint (blocking on `no-undef` in `src/`), `npm test -- --run` (Vitest), `npm run build`
3. `verification_scripts` — syntax and executable-mode checks on all `scripts/**/*.sh`
4. `infrastructure` — OpenTofu fmt, init, validate, plan-time guard suite, forbidden port check
5. `live-tenancy` — boots the backend jar against `postgres:15`, runs 9 verification suites (626 assertions)
6. `playwright_e2e` — boots the backend jar, builds frontend, runs 4 Chromium tests (admin journey)

### 2.3 Production Target

The intended production target is a **Neon hosted PostgreSQL** database (referenced in `SECURITY_AUDIT.md` and `REPOSITORY_AUDIT.md`). No OCI deployment has been executed. The OpenTofu stack (`infra/oci/`) targets Oracle Cloud Infrastructure Always Free tier (ARM `VM.Standard.A1.Flex`) but remains unprovisioned.

---

## 3. Database Compatibility

### 3.1 PostgreSQL Versions in Use

| Environment | Version | Source |
|---|---|---|
| CI (GitHub Actions) | PostgreSQL 15 | `postgres:15` service container in `ci.yml` |
| Docker Compose (local dev) | PostgreSQL 16 | `postgres:16-alpine` in `docker-compose.yml` |
| DoD-2 production target | PostgreSQL 17 | Owner decision; Neon hosted |
| Operator local (measurement) | PostgreSQL 15.8 | Homebrew on macOS |

### 3.2 Flyway Migration Chain Status

**Migrations on `master` (V1-V9):**

| Version | File | Description | Status |
|---|---|---|---|
| V1 | `V1__baseline_current_schema.sql` | Baseline current schema | Applied and verified |
| V2 | `V2__materialise_tenant_key.sql` | Materialise tenant key | Applied and verified |
| V3 | `V3__natural_keys.sql` | Natural keys | Applied and verified |
| V4 | `V4__indexes.sql` | Indexes | Applied and verified |
| V5 | `V5__composite_tenant_keys.sql` | Composite tenant keys | Applied and verified |
| V6 | `V6__require_the_tenant_key.sql` | Require the tenant key (SET NOT NULL) | Applied and verified |
| V7 | `V7__record_who_wrote_the_pupil.sql` | Record authorship (created_by/updated_by) | Applied and verified |
| V8 | `V8__close_the_dormant_composite_foreign_keys.sql` | Close dormant composite FKs | Applied and verified |
| V9 | `V9__soft_delete_attendance_and_assignment.sql` | Soft delete attendance and assignment | Applied and verified |

**Migration on backup branch only (V10):**

| Version | File | Description | Status |
|---|---|---|---|
| V10 | `V10__index_the_measured_reads.sql` | Performance indexes (pg_trgm, covering index) | Verified on PG15 only; **NOT verified on PG17** |

### 3.3 Flyway Compatibility Warning

**CRITICAL:** V10 uses `CREATE EXTENSION pg_trgm`, which requires `CREATE` privilege on the target database. This privilege model has NOT been verified on PostgreSQL 17 (the DoD-2 target). Neon's privilege model may differ from a self-managed PostgreSQL instance.

**Flyway configuration:**
- `spring.flyway.enabled=true` (all profiles)
- `spring.flyway.locations=classpath:db/migration`
- `spring.flyway.baseline-on-migrate=false` (production)
- `spring.flyway.clean-disabled=true` (production)
- PostgreSQL driver: `org.postgresql:42.7.4`
- Flyway artifacts: `flyway-core` + `flyway-database-postgresql` (versions managed by Spring Boot parent)

**Risk:** Applying V10 to a PG17 Neon database without prior verification may fail at the `pg_trgm` extension creation, leaving the migration chain broken and the application unable to start.

---

## 4. Production Request Path

### 4.1 OPS-22 Fix: Nginx /api Proxy

**Status: VERIFIED (2026-11-03)**

The production nginx configuration (`gms_frontend/nginx.conf`) now includes an `/api/` location block that proxies requests to the backend service:

```
location /api/ {
    proxy_pass http://backend:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_http_version 1.1;
    proxy_cache_bypass $http_upgrade;
    proxy_read_timeout 90s;
    proxy_connect_timeout 90s;
}
```

**Request flow in production Docker topology:**
1. Browser sends request to `http://<host>:3000/api/v1/...`
2. Nginx (frontend container, port 80 internally, published as 3000) matches `/api/` location
3. Nginx proxies to `http://backend:8080/api/v1/...` (Docker Compose service discovery)
4. Spring Boot backend processes the request and returns the response
5. Nginx forwards the response to the browser

**Static file serving:** All non-`/api/` requests are served by `try_files $uri $uri/ /index.html`, enabling SPA routing.

### 4.2 CORS Configuration

**Backend CORS** is configured in `CorsConfig.java`:
- Allowed origins: `${CORS_ALLOWED_ORIGINS:}` (no default; empty in production unless explicitly set)
- Allowed methods: GET, POST, PUT, PATCH, DELETE, OPTIONS
- Allowed headers: `*`
- `allowCredentials: true`
- `maxAge: 3600`
- Applied to `/api/**` paths only

**Production CORS requirements** (enforced by `StartupSecurityValidator`):
- Must NOT contain `localhost` or `127.0.0.1`
- Must NOT contain `*`
- Must be set via `CORS_ALLOWED_ORIGINS` environment variable
- Origins must be exact (no trailing slash, HTTPS in production)

**Nginx proxy CORS:** The nginx proxy forwards requests same-origin (browser to port 3000, proxied to backend on the Docker network), so CORS is not a cross-origin concern in the standard Docker deployment. CORS enforcement applies when the frontend is served from a different origin than the API (e.g., separate hosting).

### 4.3 Backend Service Discovery

Within the Docker Compose network, services discover each other by service name:
- `postgres` — database hostname (resolves to the PostgreSQL container)
- `backend` — API hostname (resolves to the Spring Boot container, used by nginx `proxy_pass`)
- `frontend` — external-facing hostname (port 3000 on the host)

The backend connects to the database using: `jdbc:postgresql://postgres:5432/gms` (internal port 5432, not the published 5433).

---

## 5. Security Configuration

### 5.1 JWT Authentication

**Token provider:** `JwtTokenProvider` (HS256 signing)
**Secret:** `${SECURITY_JWT_SECRET:}` — no default; empty value causes startup failure in non-dev profiles
**Validity:** `${SECURITY_JWT_VALIDITY_SECONDS:3600}` (default 1 hour)
**Issuer:** `${SECURITY_JWT_ISSUER:gms}`
**Claims:** `sub` (username), `roles` (authorities), `schoolId` (tenant), `uid` (user id, added by C-8)

**Token lifecycle:**
- Issued on successful `POST /api/v1/auth/login`
- Validated per request by `JwtAuthenticationFilter` (parsed once, placed in `TenantContext`)
- `TenantContext` is request-scoped, cleared in a `finally` block to prevent cross-request leakage
- Authorities are re-read from the database per request (`CustomUserDetailsService`) — token roles govern tenant selection, DB governs authorization

**Password security:**
- BCrypt encoding (`PasswordEncoder` bean in `SecurityConfig`)
- First-login password rotation enforced in frontend (`SEC-P2-04`)
- Password complexity validation (`PasswordValidator`)
- Login throttling: `security.login.max-failed-attempts` (default 10), enforced via `users.login_attempts` and `users.account_locked`

### 5.2 Role-Based Access Control

**Six roles** defined in `RoleEnum`:
1. `SUPERADMIN` — platform owner, cross-tenant access
2. `ADMIN` — school administrator
3. `TEACHER` — teaching staff
4. `STUDENT` — enrolled student
5. `PARENT` — parent/guardian
6. `ACCOUNTANT` — financial operations

**Authorization architecture (two layers):**
1. **URL layer** (`SecurityConfig.java`): first-match-wins path rules, fail-closed (`anyRequest().denyAll()`)
2. **Method layer** (`@PreAuthorize`): 234 guards over 236 handler methods (2 exemptions: `AuthController.login`, `HealthController.ping`)

**URL matcher groups:**
- `PLATFORM_MANAGEMENT` — `/schools/**`, `/users/**`, `/employees/**`, `/students/**`, `/student-admissions/**`, `/academic-years/**` → ADMIN, SUPERADMIN
- `SCHOOL_SETUP` — `/classrooms/**`, `/sections/**`, `/subjects/**`, `/teacher-assignments/**`, `/grading-schemes/**`, `/communications/**` → ADMIN, SUPERADMIN, TEACHER
- `ACADEMIC_OPERATIONS` — `/exams/**`, `/attendance/**`, `/timetables/**` → ADMIN, SUPERADMIN, TEACHER
- `FINANCIAL` — `/fees/**`, `/fee-structures/**`, `/fee-payments/**`, `/invoices/**`, `/receipts/**`, `/student-fees/**`, `/financial-transactions/**`, `/financial-reports/**`, `/financial-dashboard/**`, `/payment-gateway/**` → ADMIN, SUPERADMIN, ACCOUNTANT, PARENT
- `SHARED_READS` — `/results/**`, `/student-exams/**`, `/announcements/**`, `/notifications/**`, `/school-calendar/**`, `/parent-students/**` → ADMIN, SUPERADMIN, TEACHER, STUDENT, PARENT
- `DASHBOARD` — `/dashboard/**` → ADMIN, SUPERADMIN
- `SELF_SERVICE` — `/auth/change-password`, `/users/me`, `/users/me/**` → authenticated
- Self-service student/parent/teacher paths → specific roles

**Architecture test enforcement:** `DenyByDefaultAuthorizationArchTest` (7 tests) fails the build when:
- A handler declares no `@PreAuthorize`
- A guard sits on the class instead of the method
- A guard uses `hasRole`/`hasAnyRole` (authorities carry no `ROLE_` prefix)
- A guard names an authority nobody is granted
- The anonymous allow-list grows beyond login and ping

### 5.3 Tenant Isolation

**Mechanism (three layers):**
1. **Request-scoped context:** `TenantContext` populated once per request by `JwtAuthenticationFilter`, cleared in `finally`
2. **Query-layer enforcement:** `TenantScopedRepository` (33 of 36 repositories extend it) overrides `findById` to include `e.school.id = :schoolId`; 3 exemptions: `ParentRepository` (global by D-2), `SchoolRepository` (tenant table itself), `DashboardStatisticsRepository` (counts only)
3. **Database-layer enforcement:** Composite foreign keys with `school_id` on all tenant tables (V5, V6, V8); `NOT NULL` enforced (V6); same-school FK constraints (e.g., `fk_timetable_classroom_same_school`)

**Architecture test enforcement:**
- `TenantParameterArchTest` — fails the build if a service method takes `schoolId` as a parameter
- `RepositoryTenantScopeArchTest` — fails the build if a tenant-owned repository sits outside the mechanism
- `TenantScopedReadArchTest` — verifies 33 scoped / 3 exempt split
- `EntityTenantColumnArchTest` — verifies all tenant entities map `school_id`
- `EntityLazyAccessorArchTest` — prevents lazy proxy 500s

**Remaining open items:**
- Inherited `findAll`, `findAllById`, and `delete` on `JpaRepository` are not filtered at the declaration level
- `Parent` table is global by owner decision (D-2) — no tenant isolation on parent records

### 5.4 CORS Allowed Origins

**Development:** `http://localhost:5173,http://localhost:3000,http://127.0.0.1:5173`
**Production:** Must be set via `CORS_ALLOWED_ORIGINS` environment variable; `StartupSecurityValidator` refuses any origin containing `localhost` or `127.0.0.1`

---

## 6. Environment Variables

### 6.1 Required Environment Variables

| Variable | Purpose | Default | Set in `.env` | Required in Production |
|---|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Spring profile | `dev` | Yes (`dev`) | **Yes** — must be `prod` |
| `DB_URL` | JDBC connection string | _(empty)_ | Yes (localhost:5433) | **Yes** — Neon connection string |
| `DB_USERNAME` | Database user | _(empty)_ | Yes (`gms_user`) | **Yes** — Neon credential |
| `DB_PASSWORD` | Database password | _(empty)_ | Yes (placeholder) | **Yes** — Neon credential |
| `DB_NAME` | Database name | `gms` | Yes | Yes |
| `DB_PORT` | Database port (host) | `5433` | Yes | N/A (Neon) |
| `SECURITY_JWT_SECRET` | JWT signing key (min 32 bytes) | _(empty)_ | **NO — MISSING** | **Yes — CRITICAL** |
| `SECURITY_JWT_VALIDITY_SECONDS` | Token validity | `3600` | No (uses default) | Optional |
| `SECURITY_JWT_ISSUER` | Token issuer claim | `gms` | No (uses default) | Optional |
| `SECURITY_LOGIN_MAX_FAILED_ATTEMPTS` | Lockout threshold | `10` | No (uses default) | Optional |
| `CORS_ALLOWED_ORIGINS` | Allowed browser origins | _(empty)_ | Yes (localhost list) | **Yes** — production HTTPS origins |
| `APP_SUPERADMIN_ENABLED` | Bootstrap platform admin | `false` | No (uses default) | **Yes** — `true` for initial setup |
| `APP_SUPERADMIN_USERNAME` | Platform admin username | _(empty)_ | No | **Yes** (when enabled) |
| `APP_SUPERADMIN_PASSWORD` | Platform admin password | _(empty)_ | No | **Yes** (when enabled) |
| `APP_SUPERADMIN_EMAIL` | Platform admin email | _(empty)_ | No | **Yes** (when enabled) |
| `APP_SUPERADMIN_FULL_NAME` | Platform admin display name | `Platform Administrator` | No | Optional |
| `MAIL_HOST` | SMTP server | _(empty)_ | Yes (`smtp.gmail.com`) | **Yes** — production SMTP |
| `MAIL_PORT` | SMTP port | `587` | Yes | Yes |
| `MAIL_USERNAME` | SMTP username | _(empty)_ | Yes (placeholder) | **Yes** — production credential |
| `MAIL_PASSWORD` | SMTP password | _(empty)_ | Yes (placeholder) | **Yes** — production credential |
| `MAIL_FROM` | From address | _(empty)_ | Yes (placeholder) | **Yes** |
| `SERVER_PORT` | Backend HTTP port | `8080` | Yes | No (internal) |
| `JPA_SHOW_SQL` | Log SQL statements | `false` | Yes | No (should be `false`) |
| `FLYWAY_ENABLED` | Enable Flyway migrations | `true` | Yes | Yes (`true`) |
| `LOG_LEVEL_ROOT` | Root log level | `INFO` | No (uses default) | Yes (`INFO`) |
| `LOG_LEVEL_COM_GMS` | Application log level | `INFO` | No (uses default) | Yes (`INFO`; DEBUG refused by validator) |

### 6.2 Missing Critical Variables

The following variables are **NOT SET** in the current `.env` file and **MUST** be configured before production deployment:

1. **`SECURITY_JWT_SECRET`** — No JWT signing key configured. Application will refuse to start in production profile without this. Generate with: `openssl rand -base64 48`
2. **Production `CORS_ALLOWED_ORIGINS`** — Current value contains `localhost` origins. Production must specify the actual HTTPS domain(s).
3. **Production database credentials** — Current `.env` points at localhost. Production requires Neon connection string and credentials.
4. **Production SMTP credentials** — Current `.env` has placeholder values for mail configuration.
5. **`APP_SUPERADMIN_*`** — Platform administrator account must be configured for initial deployment.

---

## 7. Health Checks

### 7.1 Backend Health Probes

**Readiness probe:** `GET /actuator/health/readiness`
- Used by Docker Compose healthcheck (`wget --spider`)
- Anonymous access permitted (`SecurityConfig.java` permits `/actuator/health/**`)
- `management.endpoint.health.show-details=never` — internals are not exposed
- `management.endpoint.health.show-components=never`
- `management.health.mail.enabled=false` — mail failure does not affect readiness

**Liveness probe:** `GET /actuator/health/liveness` (implied by `management.endpoint.health.probes.enabled=true`)
- Used by CI live-tenancy harness (`scripts/ci-live-tenancy.sh` waits for liveness before running suites)

**Actuator exposure:**
- `management.endpoints.web.exposure.include=health,info`
- All other actuator endpoints require `ADMIN` or `SUPERADMIN` authority
- `/actuator/**` (non-health) is guarded at the URL level

### 7.2 Database Connectivity

- PostgreSQL healthcheck: `pg_isready -U gms_user -d gms` (10s interval, 5s timeout, 5 retries)
- Backend depends on `postgres: service_healthy` — will not start until database is ready
- Spring datasource configuration: `spring.datasource.driver-class-name=org.postgresql.Driver`
- Connection pool: HikariCP (Spring Boot default)

### 7.3 Frontend Static File Serving

- Nginx healthcheck: `wget --spider http://localhost:80/` (30s interval, 5s timeout, 3 retries)
- Frontend depends on `backend: service_healthy` — will not start until API is reachable
- Static assets served from `/usr/share/nginx/html` (Vite build output)
- SPA routing: `try_files $uri $uri/ /index.html`
- Gzip compression enabled for text, CSS, JS, JSON, XML
- Static asset caching: 1 year, `Cache-Control: public, immutable` for `.js`, `.css`, `.png`, `.jpg`, `.gif`, `.ico`, `.svg`, `.woff`, `.woff2`, `.ttf`, `.eot`
- Security headers: `X-Frame-Options: SAMEORIGIN`, `X-Content-Type-Options: nosniff`, `X-XSS-Protection: 1; mode=block`

---

## 8. Migration Strategy

### 8.1 Current Flyway Chain

**On `master` branch (V1-V9):**
The migration chain V1 through V9 has been applied and verified on PostgreSQL 15 (CI) and PostgreSQL 16 (Docker Compose). These migrations establish:
- V1: Baseline schema (all tables, FKs, indexes from the original DDL)
- V2: Materialised `school_id` tenant key column
- V3: Natural keys (`uq_users_username`, `uq_users_school_email`, `uq_employees_school_email`, `uq_parents_email`)
- V4: Performance indexes
- V5: Composite tenant keys (same-school FK constraints)
- V6: `SET NOT NULL` on tenant key columns (with backfill)
- V7: Authorship columns (`created_by`, `updated_by`) on `students`, `classrooms`, `subjects`, `student_admissions`
- V8: Close dormant composite foreign keys
- V9: Soft delete support for attendance and assignment

**On backup branch `perf/v10-index-the-measured-reads` (V10):**
V10 adds performance indexes:
- `pg_trgm` extension (requires `CREATE` privilege)
- Trigram indexes for search
- Covering index for measured reads

### 8.2 Migration Deployment Order

1. **Pre-migration:** Take a full `pg_dump` backup of the target database
2. **Apply V1-V9:** Flyway applies these automatically on first boot against a fresh database. For an existing database, Flyway checks `flyway_schema_history` and applies only missing versions.
3. **V10 merge decision:** V10 must be merged to `master` before production deployment. It is currently on a backup branch only.
4. **Apply V10:** Once merged, Flyway applies V10 automatically on next boot.
5. **Post-migration verification:** Check `flyway_schema_history` for all versions with `success = t`.

### 8.3 Rollback Procedure for Migrations

**Flyway does not support automatic rollback.** The rollback strategy is:
1. Restore from the pre-migration `pg_dump` backup
2. Deploy the previous application version (which carries only V1-V9)
3. Flyway will detect the schema history matches the available migrations

**Critical constraint:** V1-V9 migrations are not idempotent. They cannot be re-applied to a database that already has them. A rollback must restore the database to the exact pre-migration state.

---

## 9. Backup and Restore

### 9.1 PostgreSQL Backup Procedure

**Recommended backup command:**
```bash
pg_dump -h <host> -U <username> -d gms -F c -f gms_backup_$(date +%Y%m%d_%H%M%S).dump
```

**For Docker Compose local:**
```bash
docker exec gms-postgres pg_dump -U gms_user -d gms -F c > gms_backup_$(date +%Y%m%d_%H%M%S).dump
```

**For Neon hosted:**
- Use Neon's built-in point-in-time recovery (PITR) if available
- Supplement with `pg_dump` before any migration deployment
- Neon dashboard provides branch-level backup and restore

### 9.2 Restore Procedure

**From custom format dump:**
```bash
pg_restore -h <host> -U <username> -d gms -F c --clean --if-exists gms_backup_YYYYMMDD_HHMMSS.dump
```

**For Docker Compose local:**
```bash
cat gms_backup.dump | docker exec -i gms-postgres pg_restore -U gms_user -d gms --clean --if-exists
```

### 9.3 RPO/RTO Targets

**No RPO/RTO targets have been defined or tested.** The following are recommended:

| Metric | Target | Current Status |
|---|---|---|
| RPO (Recovery Point Objective) | 24 hours (daily backup) | NOT IMPLEMENTED — no automated backup exists |
| RTO (Recovery Time Objective) | 4 hours | NOT IMPLEMENTED — no restore procedure has been tested |
| Backup frequency | Daily + pre-migration | NOT IMPLEMENTED |
| Backup retention | 30 days | NOT IMPLEMENTED |
| Off-host backup copy | Yes | NOT IMPLEMENTED |
| Restore test frequency | Monthly | NOT IMPLEMENTED |

---

## 10. Monitoring and Logging

### 10.1 Application Logs

**Framework:** Logback (Spring Boot default)
**Configuration:** Via `application.properties` and Spring profiles

| Logger | Dev Level | Prod Level | Notes |
|---|---|---|---|
| `root` | INFO | INFO | Configurable via `LOG_LEVEL_ROOT` |
| `com.gms` | DEBUG | INFO | DEBUG refused by `StartupSecurityValidator` in production |
| `com.gms.config.SuperAdminBootstrapRunner` | DEBUG | INFO | Configurable via `LOG_LEVEL_BOOTSTRAP` |
| `com.gms.service.bootstrap.SuperAdminBootstrapService` | DEBUG | INFO | Same |

**Log output:**
- Container stdout/stderr (captured by Docker logging driver)
- No external log aggregation configured
- No structured logging format (JSON) configured
- No log rotation policy configured at the application level

**Sensitive data in logs (known issues):**
- SEC-07 (closed): Password hashes no longer written to stdout
- ACD-12 (closed): Administrator passwords no longer logged at INFO
- CorsConfig logs the allowed-origin list at INFO on startup

### 10.2 Database Query Logs

- `spring.jpa.show-sql=false` in production (configurable via `JPA_SHOW_SQL`)
- No slow query log configured
- No query performance monitoring configured
- PostgreSQL `log_min_duration_statement` not configured (container default)

### 10.3 Error Tracking

- `server.error.include-message=never`
- `server.error.include-stacktrace=never`
- `server.error.include-binding-errors=never`
- `server.error.include-exception=false`
- `GlobalExceptionHandler` maps `TenantAccessDeniedException` to 403, `AuthorizationDeniedException` to 403, method mismatch to 405, and constraint violations to appropriate codes

**No external error tracking** (Sentry, Rollbar, etc.) is configured.

### 10.4 Alerting

**No alerting is configured.** The following are absent:
- No metrics endpoint (Prometheus, Micrometer)
- No alerting rules
- No uptime monitoring
- No anomaly detection

---

## 11. Rollback Procedure

### 11.1 Application Rollback

**Docker Compose deployment:**
1. Stop the current stack: `docker compose down`
2. Check out the previous known-good commit
3. Rebuild images: `docker compose build backend frontend`
4. Start the stack: `docker compose up -d`
5. Verify health: `curl http://localhost:8080/actuator/health/readiness`

**Image traceability gap:** Current Docker images are tagged `latest` only. There is no commit-to-image mapping. A rollback requires knowing which commit produced the running image. **Recommendation:** Tag images with the git commit SHA during CI build.

### 11.2 Database Migration Rollback

**Procedure:**
1. Stop the application: `docker compose stop backend`
2. Restore from the most recent pre-migration backup:
   ```bash
   pg_restore -h <host> -U <username> -d gms --clean --if-exists gms_backup_YYYYMMDD_HHMMSS.dump
   ```
3. Deploy the application version that matches the backup's migration chain
4. Start the application: `docker compose start backend`
5. Verify: `SELECT version, success FROM flyway_schema_history ORDER BY installed_rank;`

**Critical constraint:** If V10 has been applied and needs to be rolled back, the manually-created indexes and `pg_trgm` extension must be dropped before restoring the backup. The `--clean` flag on `pg_restore` handles this.

### 11.3 Frontend Rollback

The frontend is a static bundle served by nginx. Rollback is simply:
1. Rebuild from the previous commit: `docker compose build frontend`
2. Restart: `docker compose up -d frontend`

No database state is affected by frontend changes.

---

## 12. Deployment Checklist

### 12.1 Pre-Deployment

- [ ] **SECURITY_JWT_SECRET** is set to a strong random value (min 32 bytes, `openssl rand -base64 48`)
- [ ] **CORS_ALLOWED_ORIGINS** contains only production HTTPS origins (no localhost)
- [ ] **Database credentials** (DB_URL, DB_USERNAME, DB_PASSWORD) point at the production Neon instance
- [ ] **Neon credential rotation** has been performed (SEC-01)
- [ ] **APP_SUPERADMIN_ENABLED=true** with a strong, unique password (APP_SUPERADMIN_PASSWORD)
- [ ] **MAIL_HOST, MAIL_USERNAME, MAIL_PASSWORD, MAIL_FROM** are configured for production SMTP
- [ ] **SPRING_PROFILES_ACTIVE=prod** is set
- [ ] **LOG_LEVEL_COM_GMS=INFO** (not DEBUG — refused by StartupSecurityValidator)
- [ ] **V10 migration** has been merged to `master` and verified on the target PostgreSQL version
- [ ] **Full pg_dump backup** of the target database has been taken and stored off-host
- [ ] **TLS certificate** is provisioned for the production domain
- [ ] **DNS** is configured and propagated for the production domain
- [ ] **Firewall rules** allow inbound HTTPS (443) only; no direct database port exposure
- [ ] **Docker images** are tagged with the git commit SHA (not `latest`)

### 12.2 Deployment

- [ ] **Database migration** applied successfully (check `flyway_schema_history` — all versions `success = t`)
- [ ] **Backend starts** without `StartupSecurityValidator` failures
- [ ] **Backend health** probe returns `UP`: `curl https://<domain>/actuator/health/readiness`
- [ ] **Frontend serves** static assets: `curl -I https://<domain>/` returns 200
- [ ] **API proxy works**: `curl https://<domain>/api/v1/ping` returns 200
- [ ] **CORS preflight** succeeds from the production frontend origin
- [ ] **Login flow** completes: authenticate as superadmin, receive JWT
- [ ] **First-login password rotation** is enforced
- [ ] **Mail delivery** test: trigger a notification email and verify receipt

### 12.3 Post-Deployment Verification

- [ ] **MVP user journey** (11 features): school creation, admin provisioning, student registration, classroom assignment, teacher assignment, subject assignment, attendance marking, exam creation, result entry, fee structure, payment recording
- [ ] **Tenant isolation** verified: school A cannot read school B's data (404)
- [ ] **Role authorization** verified: each role can access only its permitted endpoints
- [ ] **Cross-origin refusal** verified: requests from unlisted origins are blocked
- [ ] **Performance baseline** recorded at expected user count
- [ ] **Log output** confirmed at INFO level, no sensitive data in logs
- [ ] **Error responses** confirmed to not leak stack traces or internal details
- [ ] **Backup schedule** configured and first automated backup verified
- [ ] **Monitoring** configured (at minimum: container health, database connectivity, error rate)

---

## 13. Known Issues

### 13.1 Open Security Audit Findings

| ID | Severity | Status | Description |
|---|---|---|---|
| SEC-01 | P0 | **OPEN** | Neon credential committed to git history; rotation required before any production use |
| SEC-11 | P1 | OPEN | No token revocation mechanism; compromised tokens remain valid until expiry |
| SEC-12 | P1 | OPEN | Payment gateway secrets exposed in API responses |
| SEC-14 | P0 | OPEN | No TLS termination; all traffic is plaintext HTTP |
| F-1 | MEDIUM | OPEN | ResultController allows cross-student reads within same school |
| F-2 | LOW-MEDIUM | OPEN | Notification mark-read allows cross-user mutation within same school |
| F-4 | LOW | OPEN | SUPERADMIN excluded from write operations on 10 controllers |
| F-5 | DEFERRED | OPEN | Communication stubs have no tenant isolation (stubs, no current risk) |

### 13.2 Open Performance Breaches (NFR-02)

Three of six concurrent probes breach the NFR-02 budget (p95 <= 300ms, p99 <= 800ms) at 8-wide concurrency with 50,000 records:

1. **`search?query=<matches-all>`** — p95 482.1ms: 2-character pattern yields no trigrams, falls back to sequential scan
2. **`search?page=4000`** — p95 428.6ms: OFFSET 80,000 forces external merge sort, spills 7,880 kB vs work_mem=4MB
3. **`GET /dashboard/school`** — p95 350.7ms: seven statements ~107ms warm, inflated by contention (dbpeak=14)

### 13.3 PostgreSQL 17 Verification Gaps

- V10 migration (`pg_trgm` extension) has NOT been verified on PostgreSQL 17
- DoD-2 targets PostgreSQL 17 (Neon hosted)
- Full migration chain V1-V10 has NOT been applied to a PG17 database
- Performance measurements at 50k records have NOT been taken on PG17
- Neon's privilege model for `CREATE EXTENSION` has NOT been verified

### 13.4 Infrastructure Gaps

| Finding | Status | Description |
|---|---|---|
| INF-01 | OPEN | Backend port 8080 published on all interfaces |
| INF-04 | OPEN | No TLS material in repository |
| INF-05 | OPEN | No image digest pinning (`:latest` tags) |
| INF-06 | OPEN | No graceful shutdown, no JVM memory policy |
| INF-07 | OPEN | No restart policy on any service |
| INF-08 | OPEN | Container runs as root (backend fixed; frontend/nginx unverified) |
| INF-09 | OPEN | No CD pipeline exists |
| INF-13 | OPEN | No backup/restore procedure executed or tested |
| INF-15 | OPEN | No structured logging, no metrics endpoint |
| INF-16 | OPEN | No secret scanning, no dependency vulnerability scanning |
| INF-18 | OPEN | No `.github/dependabot.yml`, no `CODEOWNERS` |
| INF-19 | OPEN | No resource limits on any container |

### 13.5 Application Gaps

- **17 structural skips** in `verify-c4-owned-reads.sh` (documented, ceiling-enforced, but not closed)
- **Inherited `findAll`, `findAllById`, `delete`** on `JpaRepository` remain unfiltered at the declaration level
- **No audit view** — `created_by`/`updated_by` columns exist but no API surfaces the trail to a school
- **Hard deletes** remove the row and its audit trail
- **No read audit** — no trace of who read which student record

---

## 14. Go/No-Go Decision

### 14.1 Readiness Criteria

| Criterion | Status | Blocker? |
|---|---|---|
| Security: JWT secret configured | **FAIL** — `SECURITY_JWT_SECRET` not set | YES |
| Security: TLS termination | **FAIL** — no TLS material exists | YES |
| Security: Neon credential rotation | **FAIL** — SEC-01 open | YES |
| Security: CORS production origins | **FAIL** — only localhost origins configured | YES |
| Database: V10 verified on PG17 | **FAIL** — not verified | YES |
| Database: Migration chain applies cleanly | PASS — V1-V9 verified on PG15 and PG16 | No |
| Performance: NFR-02 targets met | **FAIL** — 3 of 6 probes breach budget | NO (owner decision) |
| Infrastructure: CD pipeline exists | **FAIL** — no deployment automation | NO (manual deploy possible) |
| Infrastructure: Backup/restore tested | **FAIL** — never tested | YES |
| Infrastructure: Monitoring configured | **FAIL** — no alerting, no metrics | NO (can deploy without) |
| Application: Tenant isolation verified | PASS — 626 assertions, 0 failures in CI | No |
| Application: MVP journey verified | PASS — 11/11 features proved in CI | No |
| Application: AuthZ gates enforced | PASS — 234/236 handlers guarded, ArchTest pins it | No |

### 14.2 Recommendation

**NO-GO for production deployment.**

Five blockers must be resolved before the system can be considered deployment-ready:

1. **Set `SECURITY_JWT_SECRET`** — generate a strong random key and configure it in the production environment
2. **Provision TLS** — obtain and configure a TLS certificate for the production domain; terminate TLS at nginx or a reverse proxy
3. **Rotate Neon credential** — the current credential is in git history; it must be rotated before any production data is stored
4. **Configure production CORS origins** — replace localhost origins with the actual production HTTPS domain(s)
5. **Verify V10 on PostgreSQL 17** — apply the full V1-V10 chain to a PG17 database and verify `pg_trgm` extension creation and index builds
6. **Execute and test a backup/restore** — take a `pg_dump`, restore it to a fresh database, and verify data integrity

### 14.3 Conditional Go

If the owner accepts the following conditions, a **limited pilot deployment** (single school, internal users only) may proceed after resolving items 1-5 above:

- Performance breaches at 8-wide concurrency are accepted as-is for a pilot (NFR-02 is a scale target, not a correctness issue)
- No CD pipeline is required for a manual pilot deployment
- Monitoring and alerting can be added post-pilot
- The 17 structural skips in live tenancy verification are documented and accepted
- The open security findings (F-1, F-2, F-4, F-5) are recorded as known issues for the pilot

### 14.4 Path to Full Go

Full production readiness requires, in addition to the NO-GO blockers:
- CD pipeline with image tagging and rollback automation
- Structured logging (JSON) and log aggregation
- Metrics endpoint (Prometheus/Micrometer) and alerting
- Automated daily backups with tested restore procedure
- Secret scanning and dependency vulnerability scanning in CI
- Resource limits on all containers
- Graceful shutdown and restart policies
- Resolution of performance breaches (NFR-02)
- Resolution of open security findings (F-1 through F-5)

---

**Document version:** 1.0
**Next review:** After V10 PG17 verification and TLS provisioning
