# 🎓 GMS - Global Management System

A comprehensive School Management System built with Spring Boot and React, featuring role-based access control, multi-tenant architecture, and complete CRUD operations.

> **Read this sentence before believing the one above it.** "Multi-tenant architecture" describes the *data model*
> (25 of 36 entities carry a `school_id`), not the *enforcement*: [docs/audit/REPOSITORY_AUDIT.md](docs/audit/REPOSITORY_AUDIT.md)
> records `TEN-01…07`, including entities with no tenant column at all and inherited endpoints with no ownership
> check. "Complete CRUD operations" is literally true of `AbstractCRUDController`, which auto-registers an unguarded
> `DELETE /{id}` on six resources (`SEC-05`) and is scheduled for deletion by `ADR-023`.
>
> **Where the authoritative documents are:** [docs/README.md](docs/README.md) is the index of who owns what. The
> sequence and the definition of done are [docs/ENGINEERING_ROADMAP.md](docs/ENGINEERING_ROADMAP.md); the frozen
> product scope is [docs/PRODUCT_REQUIREMENTS.md](docs/PRODUCT_REQUIREMENTS.md); module boundaries, the tenancy
> mechanism and the index design are [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md). **Nothing in GMS has been
> deployed**, and no stage past Stage 1 has started. Setup commands below are real; the state of testing is stated
> honestly in [docs/audit/BASELINE_STATUS.md](docs/audit/BASELINE_STATUS.md).

## 🚀 Features

### 🔐 Authentication & Authorization
- **JWT-based authentication** with secure token management
- **Role-based access control (RBAC)** with 5 user roles:
  - SuperAdmin (system-wide access)
  - Admin (school-level management)
  - Teacher (academic operations)
  - Student (personal data access)
  - Parent (child's academic information)

### 🏫 SuperAdmin Features
- **Complete school management** with CRUD operations
- **System-wide dashboard** with real-time analytics
- **Multi-tenant school creation** with automatic admin bootstrap
- **Soft delete implementation** for data preservation
- **Cross-school operations** and management

### 🎯 Core Modules
- **School Management** - Create, manage, and monitor schools
- **User Management** - Students, employees, parents, and teachers
- **Academic Management** - Classes, subjects, attendance, exams
- **Financial Management** - Fees, payments, invoices, reports
- **Communication** - Notifications, announcements, messaging

## 🛠 Tech Stack

### Backend
- **Framework**: Spring Boot 3.3.3
- **Language**: Java 21
- **Database**: PostgreSQL
- **Security**: Spring Security + JWT
- **Build Tool**: Maven
- **Architecture**: Multi-layered with service abstraction

### Frontend
- **Framework**: React 18
- **UI Library**: Material-UI (MUI) v5
- **State Management**: Redux Toolkit + RTK Query
- **Routing**: React Router v6 with protected routes
- **Build Tool**: Vite
- **Styling**: CSS-in-JS with MUI theming

## 📁 Project Structure

```
WindsurfGMS/
├── gms_backend/           # Spring Boot backend
│   ├── src/main/java/com/gms/
│   │   ├── config/        # Security, CORS, Bootstrap configs
│   │   ├── controller/    # REST API endpoints
│   │   ├── model/         # Entities, DTOs, Requests/Responses
│   │   ├── repository/    # JPA repositories
│   │   ├── service/       # Business logic layer
│   │   ├── security/      # JWT, authentication components
│   │   └── util/          # Utility classes and helpers
│   └── src/main/resources/
│       └── application.properties
├── gms_frontend/          # React frontend
│   ├── src/
│   │   ├── components/    # Reusable UI components
│   │   ├── features/      # Feature-based modules
│   │   ├── store/         # Redux store and API slices
│   │   ├── hooks/         # Custom React hooks
│   │   └── services/      # API client services
│   └── package.json
└── README.md
```

## 🚀 Quick Start

### Prerequisites
- Java 21+
- Node.js 18+
- PostgreSQL 14+
- Maven 3.8+

### Backend Setup

1. **Clone the repository**
```bash
git clone https://github.com/shubhamdiyos/gms.git
cd gms/gms_backend
```

2. **Give the application a database and a signing key**

`gms_backend/src/main/resources/application.properties` carries no host and no credential. The datasource is
`${DB_URL:}` / `${DB_USERNAME:}` / `${DB_PASSWORD:}` (`:24-26`), each with an intentionally empty default, and
`security.jwt.secret` (`:48`) has no default at all. That is the SEC-01 remediation, and the empty defaults are why a
first run **fails instead of connecting somewhere you did not mean**: measured on 2026-11-02,
`java -jar target/gms-0.1.0-SNAPSHOT.jar` with no environment stops with
`IllegalStateException: security.jwt.secret is missing, shorter than 32 bytes, or a retired public default`
(`JwtTokenProvider.java:64`). Set the three values rather than editing the file, so nothing local can become a
committed default:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # JDK 21 only — see below

# the disposable Postgres; it publishes to 127.0.0.1:5433 and to no other interface (docker-compose.yml:18)
cp .env.example .env        # then fill in DB_PASSWORD
docker compose up -d postgres

export DB_URL=jdbc:postgresql://localhost:5433/gms
export DB_USERNAME=gms_user
export DB_PASSWORD=<DB_PASSWORD from your .env>
export SECURITY_JWT_SECRET=$(openssl rand -base64 48)

mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Two caveats that are real today, not aspirational:

* **Java 21 is required.** With a newer default JDK the build fails with
  `Fatal error compiling: … TypeTag :: UNKNOWN` (a Lombok/javac incompatibility). Nothing in the repository pins this
  yet: there is no `.mvn/jvm.config`, no `maven-enforcer` rule and no `.java-version`, and `pom.xml:109`'s
  `<source>${java.version}</source>` constrains the language level, not the compiler that reads it.
* **The schema comes from Flyway, and only from Flyway.** `db/migration/` holds `V1` through `V7`,
  `spring.flyway.enabled=${FLYWAY_ENABLED:true}` (`application.properties:43`) is on by default, and
  `spring.jpa.hibernate.ddl-auto=validate` is a literal in all three profiles. An empty database is therefore built
  during boot — the log shows `Migrating schema "public" to version` seven times, ending at
  `"7 - record who wrote the pupil"` — and Hibernate never writes it. The `--spring.jpa.hibernate.ddl-auto=update`
  workaround this section used to recommend is gone from the repository: ADR-022 removed the `${JPA_DDL_AUTO:…}`
  indirection, so there is no environment variable left that can reach `update` in any profile.

Full reproduction steps and measured outputs: [docs/audit/BASELINE_STATUS.md](docs/audit/BASELINE_STATUS.md).

3. **Start the backend**

The command in step 2 is the start. Plain `mvn spring-boot:run` is not a shortcut around it: with no `DB_URL` and no
`SECURITY_JWT_SECRET` exported, it refuses to boot for the reason quoted above.

Backend will start on `http://localhost:8080`

### Frontend Setup

1. **Navigate to frontend directory**
```bash
cd ../gms_frontend
```

2. **Install dependencies**
```bash
npm install
```

3. **Start development server**
```bash
npm run dev
```

Frontend will start on `http://localhost:5173`

**One flag to know about before a demo.** `VITE_MOCK_AUTH=true` (read at build time by
`src/services/authService.js:13` and `src/store/api/apiSlice.js:16`) routes every request to in-memory fixtures and
makes the login form accept any password, so a stale local `.env` silently turns the whole application into fake data.
It is unset here — `gms_frontend/` carries only `.env.production`, and the committed `dist/` bundle contains no
mock module at all — so both `npm run dev` and the built site talk to the real backend.

## 🔑 Initial SuperAdmin

The first SuperAdmin is created at startup by the bootstrap runner. Its username and
password are **not** stored in this repository — they come from deployment configuration:

| Property | Purpose |
| --- | --- |
| `APP_SUPERADMIN_ENABLED` | Turn the bootstrap on or off |
| `APP_SUPERADMIN_USERNAME` | Login name of the initial platform administrator |
| `APP_SUPERADMIN_PASSWORD` | Initial password (must satisfy the password policy) |
| `APP_SUPERADMIN_EMAIL` | Contact address for the account |
| `APP_SUPERADMIN_FULL_NAME` | Display name |

See [docs/audit/SECURITY_AUDIT.md](docs/audit/SECURITY_AUDIT.md) for why a shipped default is
not acceptable. The initial password is single-use: the first login forces a change.

## 🎬 Demo / showcase run

[`scripts/demo/`](scripts/demo/README.md) turns a running backend into a presentable tenant and
then proves it. Nothing is inserted into the database directly: the platform owner signs in,
`POST /schools/create` provisions the school, and every pupil, fee, payment, exam, result,
attendance row and announcement after that is a request the SPA and the Flutter client already
make.

```bash
# backend already running (needs the platform owner's credentials)
GMS_SUPERADMIN_USERNAME=... GMS_SUPERADMIN_PASSWORD=... scripts/demo/run-demo.sh

# or let it start the database and backend from docker-compose.yml / .env
make demo
```

It writes the demo credentials to `/tmp/gms-demo-tenant.env` (mode `600`), signs in as all five
demo roles to confirm their screens have data, and serves the React SPA on
<http://localhost:5173> and the Flutter web build on <http://localhost:3000>. Re-running is
safe; [the demo README](scripts/demo/README.md) lists the access sheet, the eleven MVP features
mapped to the screens a presenter clicks, the reset options and the troubleshooting table.

## 📊 API Endpoints

### Authentication
- `POST /api/v1/auth/login` - User login
- `POST /api/v1/auth/change-password` - Change password

### Schools (SuperAdmin)
- `GET /api/v1/schools` - List all schools
- `POST /api/v1/schools/create` - Create new school
- `PUT /api/v1/schools/update` - Update school
- `PATCH /api/v1/schools/toggle` - Toggle school status
- `GET /api/v1/schools/{id}` - Get school details

### Students
- `GET /api/v1/students` - List students
- `POST /api/v1/students/create` - Create student
- `PUT /api/v1/students/update` - Update student

### Employees
- `GET /api/v1/employees` - List employees
- `POST /api/v1/employees/create` - Create employee
- `PUT /api/v1/employees/update` - Update employee

## 🔐 Security Features

Status labels below describe the **implemented** behaviour, measured on 2026-11-01 against a local instance
([docs/audit/BASELINE_STATUS.md](docs/audit/BASELINE_STATUS.md) §4). The target design is in the audit; the gaps are
tracked by finding ID.

### JWT Authentication
- Stateless authentication with JWT tokens — implemented
- Role-based claims in the JWT payload — implemented
- Secure password hashing with BCrypt — implemented
- ~~Automatic token refresh mechanism~~ — **does not exist** (SEC-11): there is no refresh endpoint, no logout and no
  revocation list, so a 1-hour token cannot be withdrawn, even after a password change
- Token signing key — **currently the source-code default**, which is public (SEC-02). Rotating it is an owner action

### Multi-Tenant Architecture
- One `School` = one tenant, resolved from the token — implemented as a convention
- ~~School-level data isolation~~ / ~~Tenant-aware repository queries~~ — **not enforced**. The seven controllers that
  inherit `AbstractCRUDController` expose `GET /{id}` and `DELETE /{id}` with no tenant predicate (TEN-01…TEN-07),
  `users`, `schools`, `students`, `fee_payments` and 12 tables in total have no tenant column at all, and the student
  and parent portal routes are unreachable because of filter-chain ordering (SEC-06)
- ~~Cross-school access for SuperAdmin only~~ — a correctly signed token asserts whatever `schoolId` it carries

### RBAC Implementation
- Method-level security with `@PreAuthorize` — partially: 6 of 36 controllers have none, and 20 of 36 are protected
  only by `anyRequest().authenticated()`
- ~~Role hierarchy enforcement~~ — no hierarchy is configured; authorities come from the `user_roles` table
- Permission-based UI rendering and protected frontend routes — implemented, but client-side only and therefore not a
  security control

## 🎨 Frontend Features

### Dashboard System
- **SuperAdmin Dashboard**: System overview with analytics
- **Admin Dashboard**: School-specific management
- **Teacher Dashboard**: Class and academic management
- **Student Dashboard**: Personal academic information
- **Parent Dashboard**: Child's academic progress

### UI/UX Features
- **Responsive design** with Material-UI components
- **Dark/Light theme** support
- **Real-time notifications** with toast messages
- **Loading states** and error handling
- **Form validation** with Yup schemas
- **Data tables** with sorting, filtering, pagination

## 🔧 Development Features

### Backend Patterns
- **Abstract CRUD services** for code reuse
- **Global exception handling** with structured responses
- **Audit trail** with created/updated timestamps
- **Soft delete** implementation for data preservation
- **Repository pattern** with custom queries

### Frontend Architecture
- **Feature-based** folder structure
- **Redux Toolkit** for state management
- **RTK Query** for API integration and caching
- **Custom hooks** for reusable logic
- **Protected routes** with role-based access

## 🧪 Testing

**Both halves have tests now.** The backend measured 36 tests in 6 classes on 2026-11-01, 121 in 18 classes on
2026-11-02, and **126 in 19 classes** the same day once `RetiredRowReadContractTest` landed — the class the
live-tenancy harness's first run earned, pinning that a retired row reads 404 rather than 403; the frontend's earlier "no tests at all" is closed too — `npm test` is `vitest run` over 2 files and 9
tests, and the CI step fails rather than shrugging if the script or a test file goes missing. So this section's
original claim that the repository had none is no longer true, and it is corrected rather than left to rot.

| Command | What actually happens today |
| --- | --- |
| `mvn -B verify` | runs `gms_backend/src/test` — **19 test classes, 126 tests** on this machine (`Tests run: 121` was `8a83d37`'s CI line, before the fifth contract class arrived) — and writes `target/site/jacoco/index.html`. **Locally it needs a database and two environment variables:** at that 121-test baseline,
`DB_USERNAME`/`DB_PASSWORD` unset reported `Tests run: 113` plus one error in `SqlStatementCountPerRequestTest`
(`FATAL: role "gms_user" does not exist`) and **BUILD FAILURE**; with them exported against the operator's Homebrew
PostgreSQL 15.8 on `127.0.0.1:5432` the current tree reads `Tests run: 126, Failures: 0, Errors: 0, Skipped: 0` and BUILD
SUCCESS (measured on this machine on 2026-11-02). The refusal is deliberate — the class does not skip when the database is missing, because
a skipped guard reads as a passing one — and CI supplies the same three inputs through its `postgres:15` service
container. This list used to name six classes; the **thirteen** that arrived since are the tenancy and authorization guards (`RepositoryTenantScopeArchTest`, `TenantScopedReadArchTest`, `TenantParameterArchTest`, `EndpointInheritanceTest`, `EntityTenantColumnArchTest`, `EntityLazyAccessorArchTest`, `DenyByDefaultAuthorizationArchTest`, `TenantContextTest`, `TenantContextAuditorAwarePrincipalAttributionTest`, `NotificationRecipientMatchTest`) plus the three contract guards (`EmptyCollectionContractTest`, `SqlStatementCountPerRequestTest`, `RetiredRowReadContractTest`) |
| `mvn -DskipTests` | still the command the production image build uses (`gms_backend/Dockerfile:11`), so the shipped jar is not built from a tested compile — see INF-09 |
| `npm test` | **runs.** `gms_frontend/package.json` declares `"test": "vitest run"` and there are two suites — `ErrorBoundary.test.jsx` (4) and `ProtectedRoute.test.jsx` (5) — **9 tests, 2 files, green**. In CI the step is blocking and refuses two things it used to tolerate: a missing `test` script, and a script with no `src/**/*.test.*` file behind it (measured on `8a83d37`: `Test Files 2 passed (2)`, `Tests 9 passed (9)`). `--run` is passed explicitly so no future edit to the script can put a watcher on a runner |
| `npm run lint` | runs, and reports **16 errors across 7 files** plus 1 warning — measured from CI's own summary line at `8a83d37`, not from this table's memory; it used to say 47 errors across 22 files, a count that no longer holds. CI fails the job only on the classes that break at runtime (`no-undef` under `src/`, any fatal parse error): the same run reports **0 in the gated classes**, so the gate is green while the count is recorded, not ignored |
| `npm run e2e` (in `gms_frontend`) | **runs, and is the only path in this repository with a browser in it** — four tests, real Chromium, the built bundle, a real backend. It is the one command here that needs a backend already booted, so the harness is `scripts/ci-playwright-e2e.sh`; both are documented in §Browser journey (Playwright) below |
| JaCoCo | bound to `prepare-agent` + `report` at `verify`, so coverage is measured; there is still no coverage **threshold** that can fail a build |
| CI | **three** workflows now run on every pull request: `.github/workflows/ci.yml` (six jobs — backend, frontend, verify-scripts, infrastructure definitions, the live-tenancy gate and, new on this branch, the `Playwright e2e` browser gate; the `backend` job boots a `postgres:15` service container so the DB-backed tests really have a database), `.github/workflows/validate-workflows.yml` (actionlint plus fifteen structure guards over the yaml itself) and `.github/workflows/gate-coverage.yml` (a meta check that refuses a pull whose owed gates did not run). **Since 2026-11-02 `master` requires `Gate coverage`**, with `strict` and `enforce_admins` — a red or missing check now blocks a merge even for an administrator, demonstrated once in [docs/ENGINEERING_ROADMAP.md](docs/ENGINEERING_ROADMAP.md) Stage 5 criterion 4. The former `deploy-backend.yml` was **retired on 2026-11-01** — it SSHed to an EC2 host that is permanently closed — so a merge to `master` still deploys nothing anywhere |
| `scripts/verify-security.sh` | 18 executable security assertions against a local boot — see [docs/audit/BASELINE_STATUS.md](docs/audit/BASELINE_STATUS.md) §13.2 |

What this table's last paragraph used to say — "no integration tests against a real database, no frontend tests, no
end-to-end suite" — is no longer true of the first two and only partly true of the third, and the correction belongs
in the README rather than only in the roadmap:
- **Against a real database**: `SqlStatementCountPerRequestTest` drives nine real HTTP requests through the full
  filter chain on a `RANDOM_PORT` against a PostgreSQL and asserts how many statements each prepares; CI runs it
  against a container, not a mock. It is a per-request detector, not a schema-migration harness.
- **Frontend**: 9 Vitest + Testing Library tests over the two components that had been failing in the browser.
- **End-to-end**: `scripts/verify-mvp-e2e.sh` exists and last read **298 checks / 0 failed / 11 of 11 MVP features
  PROVED**. It drives a *running* backend, and since 2026-11-02 CI runs it: the `live-tenancy` job boots the jar
  the `backend` job built against a throwaway `postgres:15` on the runner's loopback and executes nine live steps
  through `scripts/ci-live-tenancy.sh` — the same command an operator types. The job that used to declare itself
  `skipping`, blocked on twelve secret names the repository does not have, now reads no secret at all. It has run
  there: **82 s, 626 assertions, 0 failed, 17 skips at their documented ceiling** (run `41488389433`), after the
  run before it came back red on two defects only a real runner could find.

What is still genuinely missing for safe refactoring is coverage **thresholds** and Testcontainers (the DB-backed
tests use a real server, not a containerised-per-class one). The third thing this sentence used to name — "a
Playwright browser journey" — has landed, so it is documented below rather than still owed. Until those two land, the
manual verification path alongside the script above is the Postman collection — 45 requests against roughly 230
endpoint mappings — documented in
[docs/WindsurfGMS-Postman-Testing-Guide.md](docs/WindsurfGMS-Postman-Testing-Guide.md). Run it against a disposable
database only.

### 🌐 Browser journey (Playwright)

`gms_frontend/e2e/` is a real Chromium driving the **built** bundle against a **real** backend: no mocks, no stubbed
API, no second implementation of the login flow. The ten live suites in `scripts/` already prove behaviour over HTTP,
but `verify-mvp-e2e.sh` has no browser in it and the Vitest suite has no server behind it, so nothing in this
repository had ever proved that a human clicking the shipped build reaches a dashboard. Four tests live in
`gms_frontend/e2e/admin-journey.spec.js`:

- **a provisioned administrator reaches its dashboard only after rotating the password it was issued** — the login
  response's `requirePasswordChange` sends the page to `/change-password`, `ProtectedRoute` holds
  `/admin/dashboard` even when it is typed into the URL bar, and after the rotation a reload stays on the dashboard.
- **the administrator registers a pupil through the dialog and the roll shows it** — the created row is on screen, the
  page's own server-side search finds it, and `/students/search` run as that tenant returns `totalElements: 1`, so a
  row living only in the grid's local state would fail.
- **a second school's administrator searches for the first school's pupil and finds nothing** — the stranger's grid
  answers `No students match this search`, its API search returns 0 and the owner's returns 1, and that second
  number is what stops the 0 from being a tautology.
- **the API refuses a browser whose origin the deployment has not allowed** — a page on the web port POSTs to the
  absolute API origin and the call dies as `TypeError: Failed to fetch`; the same request replayed outside the
  browser with that `Origin` header answers 403 `Invalid CORS request` with no
  `Access-Control-Allow-Origin` on it. It needs no account, because the refusal happens before authentication.

**What a run needs, in order.** `playwright.config.js`'s `webServer` starts `vite preview` and nothing else: **the
suite does not boot the backend**, deliberately — the same reasoning that keeps the live suite list inside
`scripts/ci-live-tenancy.sh` instead of in workflow YAML. So either run the harness, which boots the jar and then
runs the suite, or boot a backend yourself.

The one command, and the same one the CI job types, is the browser equivalent of `scripts/ci-live-tenancy.sh`:

```bash
# a running container named gms-postgres is a prerequisite: `docker compose up -d postgres`
GMS_JAR=gms_backend/target/gms-0.1.0-SNAPSHOT.jar \
DB_URL=jdbc:postgresql://localhost:5433/gms \
DB_USERNAME=gms_user \
DB_PASSWORD=<DB_PASSWORD from your .env> \
GMS_SUPERADMIN_USERNAME=<platform administrator login name> \
GMS_SUPERADMIN_PASSWORD=<its password — a local value; never commit one, never paste one here> \
bash scripts/ci-playwright-e2e.sh
```

It creates a database that exists only for the run (`gms_e2e_<tag>`, so the role behind `DB_URL` must be able to
`CREATE DATABASE`; the database name in your URL is replaced, never written to), boots the jar against it with **no
Spring profile** so Flyway builds the schema and `StartupSecurityValidator` has to pass on the way, waits on
`/actuator/health/liveness`, builds the bundle, runs the four tests, then stops the application and drops the
database — including on a red run, because a gate that can only be run once per tag is a gate that gets its guard
removed. It needs `curl docker java node npm openssl` on `PATH`, and refuses outright if something already answers on
the web port: a suite driving a build this run did not make is the worst kind of green. Two lines of the boot log are
printed verbatim into the job output and the step summary — `Startup security validation passed (profiles: none).`
and `SuperAdmin bootstrap completed successfully!` — because both were long only *checked*: the harness refused a run
whose log lacked them, which is not the same as a reader being able to see they were there.

To run the suite against a backend that is already up on `http://localhost:8080` with a platform account that may
create schools:

```bash
cd gms_frontend
npm ci
npx playwright install chromium   # or: npm run e2e:install — same command, in package.json
npm run build                     # vite preview serves dist/ from disk; a stale bundle is what you would drive
```

```bash
export GMS_E2E_TAG=$(date +%s)                                  # ≤12 lowercase alphanumerics, unique per run
export GMS_E2E_PLATFORM_USERNAME=<platform administrator login name>
export GMS_E2E_PLATFORM_PASSWORD=<its password — placeholder only; never a real value in a document>
npm run e2e
```

| Variable | Default | What it is for |
| --- | --- | --- |
| `GMS_E2E_API_ORIGIN` | `http://localhost:8080` | where the backend is: the target of `vite preview`'s `/api` proxy, and the absolute origin the fixture posts its arranging calls to and the fourth test measures the refusal against |
| `GMS_E2E_WEB_PORT` | `4173` | the port `vite preview` binds, with `--strictPort` |
| `GMS_E2E_BASE_URL` | `http://localhost:${GMS_E2E_WEB_PORT}` | the origin the browser is driven at; derived from the port, so set it only to point the suite at a server that already exists |
| `GMS_E2E_TAG` | **none — required** | stamped into every school code the fixture mints (`E2E<tag><n><label>`), which is what makes two runs on one database not read each other's rows; `ci-playwright-e2e.sh` supplies it (the clock on a laptop, `r<run number>` in the workflow), and a hand run has to. Keep it ≤12 lowercase alphanumerics: that is the harness's own database-name guard (`^gms_e2e_[a-z0-9]{1,12}$`), and `SchoolRequest.schoolCode` is `@Size(max = 20)` besides — a longer tag turns the fixture's own school code into a 400 that reads as a backend defect |
| `GMS_E2E_PLATFORM_USERNAME` | **none — required** | the platform account the fixture logs in as to create each tenant — the boot and the suite must be given the same one (`APP_SUPERADMIN_*` configures the boot; `scripts/ci-playwright-e2e.sh` exports the pair from `GMS_SUPERADMIN_USERNAME`, the name the live-tenancy harness already requires) |
| `GMS_E2E_PLATFORM_PASSWORD` | **none — required** | its password |

A missing required variable fails with a message that names it (`e2e/helpers/api.js` throws `${NAME} is not set`
before the first navigation), so it does not arrive as a 60-second timeout, and on a database that already holds a
previous run's rows the fixture checks the `schoolId` claim in the token it derived and refuses rather than driving
the browser at someone else's tenant.

**Why the built bundle behind a proxy, and not `vite dev`.** In production mode `getApiBaseUrl()`
(`gms_frontend/src/constants/index.js:2-11`) returns the relative `/api/v1`, which is the wiring a real deployment
uses, while the dev server defaults to an absolute `http://localhost:8080/api/v1` — a cross-origin call. The backend
will not be loosened for that: `StartupSecurityValidator` (`gms_backend/src/main/java/com/gms/config/StartupSecurityValidator.java:57-65`)
refuses to boot outside the dev profile with any `localhost` entry in `cors.allowed-origins`, and the harness boots
with no profile on purpose. Serving `dist/` same-origin through `vite preview`'s `/api` proxy therefore needs no CORS
exception, and nothing looser has to be smuggled into a boot that is otherwise the real one. One thing this does
avoid rather than test: the proxy strips the `Origin` header on the way through, so `admin-journey.spec.js` covers
the CORS refusal separately, from the other side, in its fourth test — the filter is not being hidden, only routed
around on the three journey tests.

**Result policy, and what has actually been measured.** One worker, zero retries, Chromium only — the engine the
portal's users have, one worker so each test may provision its own tenant against a shared database without two runs
inventing the same school code, and no retries because **a red run is a finding, not a flake**. Measured on this
machine three times — 2026-11-02 against a jar booted by hand, then twice through the harness itself — and once on
the platform. The harness run that matters is the platform one: Actions run
[`41503973978`](https://github.com/shubhamdiyos/gms/actions/runs/41503973978) executed
`scripts/ci-playwright-e2e.sh` on an `ubuntu-latest` runner and printed `4 passed (18.3s)`, exit 0, against the
per-run database `gms_e2e_r48` it created and dropped, with `Startup security validation passed (profiles: none).`
in the uploaded boot log. The job `Playwright e2e` concluded `success` in **2m0s** and `Gate coverage` accepted it
as one of the pipeline's eight owed gates, so it is a blocking check rather than a report. Local runs are labelled
as local: the most recent, on 2026-11-03, is `4 passed (16.0s)` with a 9 s boot.

**Where the evidence goes.** `gms_frontend/playwright-report/` is the HTML report (the config's own reporter, and the
place a reader checks that the console guard actually ran). Failure traces and screenshots go to Playwright's output
directory — the default `gms_frontend/test-results/` for a hand run, and `$GMS_CI_WORK_DIR/e2e-artifacts/` under the
harness, because it passes `--output` so the traces sit next to `backend.log` and the suite transcript in one folder
the CI job uploads. Both are gitignored, since a commit holding them grows with every red run. The console guard
(`e2e/console-guard.fixture.js`) collects all three channels a browser complains on — `console.error`, `pageerror`,
`requestfailed`, minus client-side-routing `ERR_ABORTED` — and every journey test ends by asserting the array is empty.
Whatever that array held is then attached to the report as `browser console`, `(clean)` included: the transcript is
written in the fixture's teardown, which runs on the failed test too, and a red run additionally records it as a
`console-output` annotation. Measured on the harness run of 2026-11-03 — all four tests carry the attachment, so a
report cannot be confused with a guard that never ran. A passing test therefore proves the clause by asserting it, not
by printing nothing, and the CI job uploads the report and the work dir on a green run too (`if: always()`) for exactly
that reason.

## 📝 Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/new-feature`
3. Commit changes: `git commit -m 'Add new feature'`
4. Push to branch: `git push origin feature/new-feature`
5. Submit a pull request

## 📄 License

**No license has been chosen for this project.** The repository contains no `LICENSE` file, and this section
previously claimed MIT and linked a file that does not exist — corrected rather than quietly deleted, because
choosing a license is an owner decision with legal effect, not a documentation detail. Until one is recorded,
default copyright terms apply and nothing here may be assumed reusable.

## 👥 Team

- **Developer**: Shubham Kumar
- **GitHub**: [@shubhamdiyos](https://github.com/shubhamdiyos) — open an issue on this repository for anything
  GMS-related

## 🙏 Acknowledgments

- Spring Boot team for the excellent framework
- React team for the powerful frontend library
- Material-UI team for the beautiful components
- PostgreSQL team for the robust database

---

**Built with ❤️ using Spring Boot and React**
