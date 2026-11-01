# GMS Engineering Baseline — Verified State at Milestone 0

**Date of measurement:** 2026-11-01 (Asia/Kolkata)
**Commit under measurement:** `256b8e4` (`master`, "Merge pull request #8 from shubhamdiyos/fix/remove-mock-mode-banner")
**Measured on:** macOS 27.0.1, arm64, single developer laptop
**Prepared by:** repository audit pass mandated by §3 of the engineering mandate

---

## 0. What this document is, and what it is not

[REPOSITORY_AUDIT.md](REPOSITORY_AUDIT.md) is the findings register. [SECURITY_AUDIT.md](SECURITY_AUDIT.md) is the
attack-path view. [TECHNICAL_DEBT.md](TECHNICAL_DEBT.md) is the ranked backlog. **This file is the evidence layer
under all three**: commands that were actually executed, the outputs they produced, and an explicit list of what was
deliberately *not* run.

Two rules governed every measurement:

1. **Nothing here is inferred.** Every claim carries the command or probe that produced it. Where a claim could not
   be verified locally, it is listed in §8 as *not executed* rather than softened into a guess.
2. **No production system was touched.** The live Neon database, the EC2 host and the Vercel deployment were never
   connected to, queried or deployed to during this baseline (§25 rules 1–3, 13). All runtime evidence below comes
   from a disposable PostgreSQL container and a locally started process, both created and destroyed inside this
   session.

The headline is short: **the product compiles and builds, and it does not work.** It has no tests, no migrations, no
reachable student/parent portal, no reproducible database, and a signing key published in a public repository that
grants platform administrator access to anyone who reads it.

---

## 1. Environment of record

| Component | Version used | Note |
| --- | --- | --- |
| JDK (project requirement) | Oracle Java SE **21.0.1** LTS (`/Library/Java/JavaVirtualMachines/jdk-21.jdk`) | `pom.xml:22 <java.version>21</java.version>` |
| JDK (shell default on this machine) | OpenJDK **24.0.2** | **build fails** — §2 rows B1/B2 |
| Other JDKs installed | Corretto 17.0.13, Corretto 23.0.1, Corretto 8 | irrelevant to this project |
| Apache Maven | **3.9.9** (Homebrew) | no Maven wrapper in repository |
| Node.js / npm | **24.22.0** / **11.19.1** | no `.nvmrc`, no `engines` field |
| Docker CLI | **29.9.2** | |
| Docker Compose | **v5.6.1** | `docker compose` (v2 syntax) works |
| Container runtime | **Colima v0.10.3** (Ubuntu 24.04 VM, Docker 29.5.2) | Docker Desktop is *not* installed |
| PostgreSQL image | `postgres:16-alpine` → server **16.15** | Neon production server version: unverified (§8) |
| Local PostgreSQL binaries | Homebrew **14.13** | could not initialise a cluster — §7 of this file |

There is **no version pinning in the repository** for any of the above: no `.mvn/`, no `toolchains.xml`, no
`maven-enforcer-plugin`, no `.nvmrc`, no `engines`, no `mise`/`asdf`/`.tool-versions`. A developer on the JDK their
shell defaults to cannot build this project and gets a message (`TypeTag :: UNKNOWN`) that does not mention Java.

---

## 2. Command evidence log

All commands were run from `/Users/shubhamkumar/Desktop/GMS-V1` on 2026-11-01. Results are verbatim summaries.

| # | Command | Result |
| --- | --- | --- |
| B1 | `mvn -o -B clean compile` (default JDK 24.0.2) | ❌ `Failed to execute goal maven-compiler-plugin:3.11.0:compile … Fatal error compiling: java.lang.ExceptionInInitializerError: com.sun.tools.javac.code.TypeTag :: UNKNOWN` |
| B2 | same, after `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` | ✅ `BUILD SUCCESS` (283 source files) |
| B3 | `mvn -o -B test` | ⚠️ `--- surefire:3.2.5:test --- No tests to run.` → **`BUILD SUCCESS`** |
| B4 | `ls gms_backend/src/test` | ❌ directory does not exist — the project has **zero** test sources |
| B5 | `mvn -o -B -DskipTests package` | ✅ `target/gms-0.1.0-SNAPSHOT.jar` (59,604,184 bytes, Boot repackaged) |
| F1 | `npm ci` (in `gms_frontend/`) | ✅ exit 0; `node_modules` was absent before, `package-lock.json` is consistent |
| F2 | `npm run build` | ✅ `vite v7.1.8`, 11,995 modules, `dist/assets/index-*.js` **1,147.47 kB / 347.36 kB gzip** in a **single chunk**; Vite warns `Some chunks are larger than 500 kB` |
| F3 | `node -e "require('./package.json').scripts"` | `{dev, build, lint, preview}` — **no `test` script**, no `vitest`, no `@testing-library`, no `playwright` in dependencies |
| I1 | `docker compose config --quiet` (no `.env`) | ❌ exit **1**: `env file …/.env not found` + warning `the attribute version is obsolete` |
| I2 | `cp .env.example .env && docker compose config --quiet` | ⚠️ exit 0, but still emits the obsolete-`version` warning |
| I3 | `docker compose up -d postgres` | ✅ `Container gms-postgres Started`; `health_status: healthy` within 3 s |
| R1 | `java -jar target/gms-….jar` (only `--spring.datasource.*` overrides, `ddl-auto` left at the committed default `validate`) against the empty local DB | ❌ **startup failure**: `Schema-validation: missing table [academic_years]` → `APPLICATION FAILED TO START`, exit 1 |
| R2 | same jar, additionally `--spring.jpa.hibernate.ddl-auto=update` | ✅ `Started GmsApplication in 5.853 seconds`; Hibernate creates **38 tables**, **123 FKs**, **14 unique constraints**, then runs the SuperAdmin bootstrap to completion (`SuperAdmin bootstrap completed successfully in 226 ms`) |
| R3 | `GET /actuator/health` on the running instance | ❌ `{"status":"DOWN"}` — cause in log: `jakarta.mail.AuthenticationFailedException: failed to connect, no password specified?` |
| R4 | `POST /api/v1/auth/login` with the default bootstrap credentials | ✅ **HTTP 200**, `accessToken` issued |
| R5 | `GET /api/v1/schools` with a **locally forged** JWT (§4.1) | ✅ **HTTP 200** with tenant data — signature accepted |
| R6 | `GET /api/v1/users` with a legitimate SUPERADMIN token | ❌ **HTTP 500** `HttpMessageNotWritableException` / `rootCause: org.hibernate.LazyInitializationException` |
| R7 | `POST /api/v1/students/create` with `{"fullName":"x"}` | ❌ **HTTP 400** `{"error":"Validation failed","message":"must not be blank"}` — one field only, no field name |

`mvn -o` (offline) was used throughout; every dependency resolved from the local repository, so the build is
reproducible without network access once dependencies are cached.

---

## 3. Baseline verdict per dimension

| Dimension | Verdict | Evidence |
| --- | --- | --- |
| Backend compiles | ✅ **pass** — on JDK 21 only | B2 |
| Backend compiles on a default modern JDK | ❌ **fail** | B1 |
| Backend packages | ✅ pass | B5 |
| Backend has automated tests | ❌ **0 test classes, 0 test files** | B3, B4 |
| JaCoCo produces a coverage number | ❌ plugin declared at `pom.xml:119-120` with **no `<executions>`** → never instruments, no report | B3 |
| Frontend builds | ✅ pass | F2 |
| Frontend is code-split | ❌ single 1.147 MB chunk | F2 |
| Frontend has tests | ❌ no script, no framework installed | F3 |
| Application starts on a fresh database | ❌ **fails closed at schema validation** | R1 |
| Application starts with schema auto-update | ✅ starts (only escape hatch that exists) | R2 |
| Readiness/liveness probe usable | ❌ `/actuator/health` is permanently `DOWN` without SMTP | R3 |
| `docker compose up` works out of the box | ❌ exit 1 without an undocumented `.env`; and even with one the backend healthcheck can never pass (R3 + `wget --spider /actuator/health \|\| exit 1`) | I1, I3, R3 |
| Authentication works | ✅ for an account whose password you know | R4 |
| Authorization works | ❌ forged token accepted; inherited CRUD routes unguarded | R5, §4 |
| Tenant isolation works | ❌ not enforced on inherited CRUD paths; 12 tables have no tenant column at all | §5, §6 |
| Database schema is reproducible from the repo | ❌ **0 migration files**, no `flyway-core` dependency, `spring.flyway.*` keys inert | §6 |
| Documentation matches the running system | ❌ | §7 |

**One-line verdict: green build, red product.** The CI signal a reviewer would see today (`mvn test` → BUILD SUCCESS,
`vite build` → success) certifies nothing about behaviour, because there is nothing to run.

---

## 4. Runtime-proven defects

These were reproduced against a locally started instance on a disposable database. Finding IDs refer to
[REPOSITORY_AUDIT.md](REPOSITORY_AUDIT.md).

### 4.1 AP-1 / SEC-02 — a token can be forged by anyone who can read the repository

`security/JwtTokenProvider.java:22` defaults `security.jwt.secret` to a 50-character literal, and no file in the
repository overrides it (verified by repository-wide grep for `security.jwt`, `JWT_SECRET` in `src/main/resources/`,
`.env.example`, `docker-compose.yml`, `gms_backend/Dockerfile` and `.github/`). The value is therefore **public
knowledge**, and the algorithm is HS256 — symmetric, so the signing secret is also the verification secret.

Proof executed (R5): a JWT was constructed in ~15 lines of Python using only that published literal, claiming
`{"sub":"superadmin","roles":["SUPERADMIN"],"schoolId":99}`. The server had **never issued** such a token and the
claimed `schoolId` (99) does not exist. Response:

```
GET /api/v1/schools   Authorization: Bearer <forged>   → HTTP 200
[{"id":1,…,"schoolName":"System","schoolCode":"SYSTEM",…}]
```

Consequence: unauthenticated read of any tenant in the platform, and — combined with `UserServiceImpl.java:85-87`
(AP-2) — the ability to mint further accounts. There is no revocation path (§7 of the mandate) because there is no
refresh/revocation architecture (SEC-10): `AuthController` exposes exactly three endpoints (`login`,
`change-password`, `self-register`), and the access token is not bound to a password/version counter, so
`change-password` does not invalidate a stolen token.

*Not reproduced here:* the same technique against the live deployment. That is an owner-side decision and would
require explicit authorization; the code path is identical, so the exposure is assumed live until proven otherwise
(see §8).

### 4.2 SEC-03 — the shipped SuperAdmin bootstrap password authenticates successfully

R4: `POST /api/v1/auth/login` with the username and password hardcoded as defaults in
`config/SuperAdminBootstrapProperties.java:58,67` returned **HTTP 200** and a token. `validate()` at
`SuperAdminBootstrapProperties.java:188-191` only rejects blank values, and
`SuperAdminBootstrapRunner.java:193-197` logs a warning rather than failing startup, so a deployment that forgets to
set `APP_SUPERADMIN_PASSWORD` silently runs with a publicly known administrator password.

### 4.3 SEC-07 — credentials are written to standard output

The log captured during R4 contains, verbatim in shape:

```
Login request received for username: superadmin
User found: superadmin with password hash: $2a$10$…(60-character BCrypt digest)…
User found: superadmin with password: $2a$10$…
Authentication failed: Bad credentials
```

Two independent sources print the digest: `controller/AuthController.java:76` and
`security/CustomUserDetailsService.java:35,40,46`. The latter executes on **every authenticated request**, not just
login. Mandate §7 rule 4 ("never log passwords or password hashes") is violated in the current runtime, and
`logging.level.com.gms=DEBUG` is the default in every profile (`application.properties:42-43`), so the same channel
also emits SQL-level and bootstrap detail. The digest itself is redacted from this document per mandate §3; anyone
holding the log has an offline BCrypt-cracking target for a known username.

### 4.4 SEC-08 — error responses disclose internals

R6 returned HTTP 500 with a JSON body containing the Spring exception class, the Hibernate exception class, the
entity and identifier of the failed proxy (`com.gms.model.entity.School#1`), the JVM source file
(`AbstractJackson2HttpMessageConverter.java:492`) and a stack-trace path. This comes from
`exception/GlobalExceptionHandler.java:44-66`, which serialises `exception`, `rootCause`, `rootMessage`, `at` and
`rootAt` into the response. An unauthenticated attacker can use those bodies to enumerate the framework, Hibernate
version behaviour and internal class layout.

### 4.5 SEC-04 / ACD-xx — raw JPA entities in responses are not merely ugly, they are broken

`GET /api/v1/users` (`controller/UserController.java:44-49`, zero `@PreAuthorize`) returns `List<User>` directly.
With `spring.jpa.open-in-view=false` (`application.properties:38`) serialisation hits a lazy `School` proxy outside
any session and the request **500s** (R6). The same class of failure applies to `Student`, `Employee` and every
`@JsonProperty` convenience getter that dereferences a lazy association (`model/entity/User.java:135-163`).

This is the strongest single piece of evidence in this baseline for a larger conclusion: **these endpoints have
never been exercised end-to-end by the people who wrote them.** A 500 on the primary "list users" call of an
administrative dashboard is not something that survives one manual click. The frontend's parallel mock layer
(`gms_frontend/src/services/mockBaseQuery.js`, switched on by `VITE_MOCK_AUTH`) has been absorbing that reality.

### 4.6 OPS-xx — `/actuator/health` is `DOWN` in every environment without SMTP

R3. `spring.mail.*` is configured with `smtp.gmail.com:587` and empty credentials
(`application.properties:23-29`), so Boot's `MailHealthIndicator` fails, drags the aggregate status to `DOWN`, and
`management.endpoints.web.exposure.include=health,info` (`:20`) does not show components, so the cause is invisible
to an operator. Consequences: the Compose backend healthcheck (`wget --spider …/actuator/health || exit 1`) can never
succeed; any orchestrator or uptime monitor using `/actuator/health` as the readiness gate sees a permanently
unhealthy service; and a future CI "wait for healthy" step would time out.

### 4.7 Request-contract feedback is lossy

R7: a validation failure returns `{"error":"Validation failed","message":"must not be blank"}` — the offending field
is not named and only the first violation is reported. `GlobalExceptionHandler` maps exactly three exception types,
so anything else falls into the 500 of §4.4.

---

## 5. Reproducibility — what happens to a new developer today

Walking the repository's own instructions from a clean clone:

| Step | Documented in | Outcome |
| --- | --- | --- |
| `mvn test` / `mvn package` | README | ❌ fails unless the developer *already knows* to pin JDK 21 (B1) |
| `docker compose up` | README §Quick Start | ❌ exit 1 — needs a `.env` that `.env.example` does not describe completely (I1) |
| `docker compose up` with `.env` | — | ⚠️ Postgres starts (I3); backend container starts but never becomes healthy (R3/§4.6); `depends_on` has no condition for the backend → frontend starts against a broken upstream |
| `mvn spring-boot:run` for local dev | README | ⚠️ **silently connects to the hosted Neon database** — the datasource URL, user and password are hardcoded literals at `application.properties:5-7` with no `${…}` placeholder. A developer following the README's "local development" path is on production data from the first keystroke |
| Start against a local database | — | ❌ needs `ddl-auto` changed away from the committed `validate`, because the repository cannot produce a schema (R1 vs R2) |
| Log in as a student or parent and open the portal | frontend routes exist | ❌ `SecurityConfig.java:43-59` orders `ADMIN`-only matchers ahead of every `:hasAuthority("STUDENT")` route, so the first-match-wins filter chain returns 403 before `@PreAuthorize` is consulted (SEC-06) |
| Open the students screen in the UI | `StudentsPage.jsx:43-50` | ❌ posts `classroomId` where `StudentRequest.java:41` requires `classId`; several other RTK Query paths in `store/api/apiSlice.js` do not exist on the backend (FE-01…FE-05) |
| Run any automated check | — | ❌ nothing to run (B3/B4/F3); CI contains only a deploy workflow (`.github/workflows/deploy-backend.yml`) |

**There is today no path from a clean clone to a running system that does not touch production data.** That is the
baseline this project starts from.

**Re-measured on `feat/stage2-flyway-baseline` (2026-11-01, unmerged) — two rows of this table change, and one gains a
new failure.**

| Step | Now, on that branch |
| --- | --- |
| Start against a local database | ✅ **the row that made R1 famous is closed.** No `ddl-auto` override is needed: the boot logs `Successfully applied 5 migrations … now at version v5`, then `Started GmsApplication in 6.763 seconds` with `ddl-auto=validate`, and readiness answers `UP`. The repository can produce a schema. |
| Start against a database an *older* build created with `ddl-auto=update` | ❌ **a new, deliberate failure**: `Found non-empty schema(s) "public" but no schema history table. Use baseline() or set baselineOnMigrate to true…`, and the application does not start. This is the correct behaviour and the reason is in `ADR-022`: a `update`-built schema is not a baseline, it is an unreviewed second schema, and silently baselining it would apply V2–V5 on top of whatever Hibernate guessed. The fix is to let the chain build the database — `dropdb`/`docker compose down -v` on a **local** volume — not to set `baselineOnMigrate=true`. |

The second row is the one a developer meets first, because the local `gms` database on this machine is exactly the
situation. It has been left in place rather than dropped: deleting a database is not reversible, and the rows in it
(1 school, 2 users) are someone's, not the migration chain's.

---

## 6. Database reality (measured on the disposable instance)

Generated by Hibernate from the current 37 entity classes under `ddl-auto=update` (R2), then inspected with
`pg_dump --schema-only`:

| Metric | Measured | Implication |
| --- | --- | --- |
| Tables created | **38** | matches the entity set; the "39 tables" figure in `memory-bank/postgresqlMCPConnection.md` describes a different, older local database |
| Tables carrying `school_id` | **26** | |
| Tables with **no** `school_id` | **12** | `attendances`, `exam_subjects`, `fee_payments`, `parent_student`, `parents`, `results`, `student_exams`, `student_fees`, `teacher_assignments`, `timetable_slots`, `timetables`, `user_roles` — tenancy for the most sensitive records (payments, results, attendance, parent links) is only reachable through joins, so a single repository-level filter cannot protect them |
| Foreign keys | **123** | referential integrity is genuinely in place |
| Unique constraints | **14** | includes `schools.school_code`, `users.username`, `users.email`; **no tenant-scoped composite unique** (e.g. `(school_id, roll_number)`), so two schools collide on any other natural key |
| Secondary indexes | **0** | every tenant query filters an unindexed `school_id` column; the only indexes are those backing primary and unique keys |
| Migration files in repository | **0** | `src/main/resources/db/migration` does not exist and `flyway-core` is not a dependency, so `spring.flyway.enabled` (`application.properties:16`) configures a plugin that is not on the classpath |
| Audit-column conventions | **2 coexisting** | `BaseAuditEntity` maps `created_on`/`updated_on`, while entities declaring their own fields use `created_at`/`updated_at` — `Exam`, `Section`, `SchoolCalendar` end up with four audit columns |
| Hibernate DDL churn | **12 "constraint … does not exist, skipping" warnings** | `ddl-auto=update` attempts drops it cannot make; evidence that `update` is being used as a migration tool |

Two structural consequences follow, and they drive milestone M2:

1. **A Flyway baseline cannot be authored from the repository alone.** The live Neon schema was produced by
   `ddl-auto=update` over time (the Compose file even hardcodes `SPRING_JPA_HIBERNATE_DDL_AUTO: update`, overriding
   the `validate` default its own `.env.example` advertises). The generated schema above is *a* reference, not *the*
   production schema; baselining requires an authorized schema-only export from Neon, or the first deployment after a
   controlled diff will fail `validate`.
2. **Any schema change requires a migration, and no migration tool exists.** Until Flyway is on the classpath, every
   entity edit is a silent production incident risk.

---

## 7. Configuration surface — four sources that disagree

| Key | `application.properties` | `.env.example` | `docker-compose.yml` | Actually reaches the app? |
| --- | --- | --- | --- | --- |
| Datasource URL | hardcoded Neon literal, **no placeholder** (`:5`) | `DB_HOST`/`DB_PORT`/`DB_NAME` | `SPRING_DATASOURCE_URL` (relaxed binding, wins) | ⚠️ yes in Compose; ❌ **always Neon outside Compose** |
| Datasource user/password | hardcoded Neon literal (`:6-7`) | `DB_USERNAME`/`DB_PASSWORD` | `SPRING_DATASOURCE_*` | ⚠️ same split-brain |
| `ddl-auto` | `${JPA_DDL_AUTO:validate}` (`:11`) | `JPA_DDL_AUTO=validate` | **hardcoded `update`** | ❌ Compose silently downgrades the safety setting |
| `security.jwt.secret` | **absent** | **absent** | **absent** | ❌ only ever the insecure default (SEC-02) |
| `app.superadmin.*` | **absent** | **absent** | **absent** | ❌ only ever the insecure defaults (SEC-03) |
| Mail host/credentials | `${MAIL_HOST:smtp.gmail.com}` + empty user/pass (`:23-26`) | `MAIL_*` present | not passed | ⚠️ configured but unusable, and it is what turns health to `DOWN` |
| `logging.level.com.gms` | `${LOG_LEVEL_COM_GMS:DEBUG}` (`:42`) | `LOG_LEVEL_COM_GMS=DEBUG` | not passed | ⚠️ **DEBUG is the production default** |
| Flyway | enabled (`:16`) | `FLYWAY_ENABLED=true` | not passed | ❌ no engine, no migrations, no files |
| Multipart uploads | disabled (`:39`) | — | — | ❌ documents/avatars cannot be uploaded; no finding is filed against the feature because the feature is off |
| CORS | Vercel + localhost allowlist (`:34`) | `CORS_ALLOWED_ORIGINS` | override present | ✅ consistent, but the EC2 origin itself is served over plaintext HTTP behind `vercel.json:5` |

`gms_frontend/.env.production` contains only `VITE_API_BASE_URL=/api/v1`. There is no committed non-production
frontend environment file; `VITE_MOCK_AUTH` is therefore undocumented, yet mock mode is the path most reviewers have
followed (mandate §25 rule 18 forbids it as a production fallback).

---

## 8. Not executed — and why

Deliberately out of scope for Milestone 0. Each item needs either owner authorization or a preceding milestone.

| Item | Why it was not run |
| --- | --- |
| Any connection to the hosted Neon database (read-only or otherwise) | §25 rules 1–2. Also, no authorized schema export means the Flyway baseline in §6 stays *provisional* and is stated as such |
| Deploying or invoking `deploy-backend.yml` | §25 rule 13; the workflow SSHes to a production EC2 host on push |
| Rotating the exposed Neon credential, or removing it from git history | **Owner action.** The value is in the public repository's history (`git log -S`, commit `5eb1c23`); a rewrite of `master` history would break the branch the deployment tracks, so it must be coordinated, not done silently. Editing `HEAD` does not revoke anything |
| Cross-tenant exploit attempts against production | Same as above; the equivalent local proof is §4.1 |
| `docker compose up` with all three services | Backend image build plus frontend image build were not needed to establish the health-check defect (§4.6), which is provable from the running process. Full-stack Compose verification is milestone M4 scope |
| A second tenant for a live cross-tenant HTTP assertion | Requires provisioning a second school through the API; deliberately deferred to the M1 automated tests, where it becomes a permanent assertion instead of a one-off manual probe |
| Playwright / Vitest / Testcontainers runs | No such tooling exists in the repository yet (F3); introducing them is M3 |
| Building a local PostgreSQL cluster for the disposable DB | Attempted and failed for an unrelated host reason: PostgreSQL 14.13 refuses `initdb` on this kernel (`could not create shared memory segment: Cannot allocate memory`, `kern.sysv.shmall=1024`). Switched to the `postgres:16-alpine` container instead, which matches the Compose definition anyway |

---

## 9. Repository quantification

| Metric | Count |
| --- | --- |
| Backend Java files | 283 |
| `@RestController` classes | 36 |
| JPA entities | 37 |
| Spring Data repositories | 35 |
| Service classes / interfaces | 75 |
| Controllers inheriting tenant-unchecked `GET /{id}`, `GET`, `DELETE /{id}` from `AbstractCRUDController` | **7** (`Classroom`, `Employee`, `Parent`, `School`, `Student`, `Subject`, `User`) — none overrides `DELETE /{id}` |
| Controllers with **no** `@PreAuthorize` on any method | **6** (including `UserController`) |
| Controllers with **no** path rule in `SecurityConfig` (protected only by `anyRequest().authenticated()`) | **20 of 36** |
| Methods with a `@Transactional` annotation | 73 across the backend |
| Fee/exam/result service implementations with **zero** `@Transactional` | **10** (`Fee`, `FeeStructure`, `StudentFee`, `FeePayment`, `Invoice`, `Receipt`, `PaymentGateway`, `Exam`, `StudentExam`, `Result`) — `AttendanceServiceImpl` alone carries 8 |
| Backend test classes | **0** |
| Frontend test files / test script | **0** / none |
| Database migration files | **0** |
| Markdown files in the repository | 59 |
| GitHub Actions workflows | **1**, and it is a deploy-to-production workflow with no test or build gate before it |
| Postman requests | 45 across two collections, against **230** declared handler methods counted in `controller/*.java` (128 GET, 44 POST, 30 PUT, 23 DELETE, 5 PATCH) — ≈19% coverage |
| Known P0 findings | 9 (see §3 of REPOSITORY_AUDIT.md) |

**These are baseline counts, and five of them no longer describe the tree.** Read against the code rather than
against this table: `controller/` holds **36** classes, and the table's 36 is right again — but not for the reason the
baseline had, because `C-1` deleted `AbstractCRUDController` and the directory has since gained `DashboardController`
in its place;
the row above about 7 controllers inheriting tenant-unchecked reads is **0**, because `C-1` deleted the base class and
`EndpointInheritanceTest` fails the build if any controller extends one. "Controllers with no `@PreAuthorize` on any
method: 6" is **0** — 234 of the 236 handler methods carry a guard and the two exceptions (`AuthController.login`,
`HealthController.ping`) are pinned, by name, by `security/DenyByDefaultAuthorizationArchTest`; the "20 of 36 unprotected
by a path rule" row is doubly stale, since the catch-all is `anyRequest().denyAll()` (`SecurityConfig.java:152`) and an
unlisted route is now refused rather than admitted. "Backend test classes 0" and "Database migration files 0" were the
audit's truest rows and are now the largest changes: 16 test classes under `gms_backend/src/test/java/com/gms/`
(counted with `find … -name '*.java' | wc -l`), and seven migrations `V1`–`V7` in
`gms_backend/src/main/resources/db/migration/`, with `flyway-core` in `gms_backend/pom.xml:54`. The later sections of
this file (§13 onward) carry the counts for each card as it closed: §18 is the card evidence for `C-4`, `C-5`, `C-6` and
`C-8`, and what is still open is the set of "does not close" / "still open" lines at §17.6 and §18.1–§18.6.

---

## 10. State left on this machine by the baseline

Recorded so the environment is not silently different from before:

* **Colima was started** (`colima start`) because Docker Desktop is not installed and no daemon socket existed. This
  also created and selected a Docker context named `colima`. Stop with `colima stop`.
* **Container `gms-postgres` exists and is running** (`postgres:16-alpine`, port 5432), with volume
  `gms-v1_postgres_data` holding a **disposable, Hibernate-generated schema and one bootstrap SuperAdmin row**. It
  contains no production data. Remove with `docker compose down -v`.
* **`/Users/shubhamkumar/Desktop/GMS-V1/.env`** was created by copying `.env.example` (git-ignored; contains only the
  placeholder password from the example file). Delete it if you prefer a clean tree.
* The backend process used for probes was **terminated**; port 8099 is closed.
* Logs and the generated schema dump live in `/tmp` (`gms_boot*.log`, `gms_hibernate_generated.sql`) and are outside
  the repository — nothing from a database was committed.

---

## 11. Owner actions required before M1 can be considered complete

These are outside the repository and cannot be performed by an agent.

1. **Rotate the exposed Neon credential** (the value currently in `application.properties` and in public git history)
   and re-inject it as a runtime secret for the EC2 process.
2. **Set a real `APP_SUPERADMIN_PASSWORD` and a fresh ≥256-bit random secret for `security.jwt.secret`** (environment
   variable `SECURITY_JWT_SECRET`) on the EC2 host, then decide the invalidation window for tokens issued under the
   published default. Every JWT signed with the repository's default secret must be treated as forgeable by a third
   party.
3. **Confirm whether the deployment reads `/opt/gms/…` environment files or the packaged `application.properties`**,
   because rotating a credential only helps if the runtime stops using the committed literal.
4. **Authorize a schema-only export from the production database** so the Flyway baseline in M2 describes reality
   rather than the locally generated approximation in §6.
5. **Confirm the target branch protection state** on GitHub: the mandate requires feature branches and PRs with green
   checks, but the only workflow that exists today deploys on push.

### 11.1 Re-measured, and now ordered (Infra-M0, 2026-11-01)

Item 5 is answered by measurement rather than recollection: `required_status_checks.contexts = []`,
`strict = false`, `enforce_admins.enabled = false`, while reviews are configured
(`required_approving_review_count = 1`, `require_last_push_approval = true`). Two GitHub Environments exist
(`Production`, `Preview`) with **no** protection rules and `can_admins_bypass: true`, and no workflow references
either. So the gate is advisory in both directions, and PR #10 was merged with an admin override — as was PR #11
(the infrastructure audit, `483a41c`, 2026-11-01), this time after the blocker was reported and the owner chose the
override. On a single-account repository the configured rule (`required_approving_review_count = 1` with
`require_last_push_approval = true`) can never be satisfied, because GitHub refuses an author's own approval;
Infra-M2 has to choose a protection shape that is satisfiable rather than rely on repeated bypasses.

**Twelve consecutive PRs have now needed one.** #13 (EC2 retirement, `964acad`, 2026-11-01T07:13:33Z) and #14 (the
workflow-validation gate, `13bc2ed`, 07:14:21Z) both hit `REVIEW_REQUIRED` and both went in on the owner's standing
instruction to manage the PR lifecycle rather than queue it for a reviewer who cannot approve their own work, followed
by #15 (`d274ebc`, 07:27:31Z) — the documentation pass that recorded those two — and #16 (`7fb25b8`, 07:27:41Z), the
citation-key fix, **nine seconds** after #15: two branches stacked on one base, merged as fast as the CLI would let
them be. Then #17 (`ea3cebf`, 07:33:52Z), the pass that counted seven, and #18 (`3cf8fa1`, 08:10:46Z), the OpenTofu
stack, thirty-seven minutes later. #20 (`88b6f17`, 08:48:41Z) recounted that list at nine, #21 (`04aff7b`, 09:56:05Z)
closed part of TEN-03, and #22 (`3b6a6a5`, 10:14:04Z) narrowed this repository's own path filter. Twelve merges, twelve
overrides — and two of the last three were documentation passes *about* the count, which is the loop this section keeps
failing to exit.

No required check was bypassed: #13's CI jobs, #14's two `Workflow validation` jobs and #15/#16's absence of runs under
`paths-ignore` are all consistent, and `Workflow validation` re-ran green on master's own `7fb25b8` (run 41284869478).
#18's run is the one that changes what this paragraph can say. All three `ci.yml` jobs were green on the pull request
(run 41287513214) and the new `Infrastructure definitions` job then re-executed on the push to `master` at its own
merge commit (run 41287750885, job 124792405019), where each of its seven guard assertions printed `ok` and the §9.2
port grep found nothing. So the merge that *added a check* was itself gated by nothing: the new job is not in
`contexts` either, and with `contexts = []` a fourth green job is exactly as non-binding as the first three. The number
of assertions in `ci.yml` is therefore not a proxy for protection, and growing the gate cannot close INF-10 — only the
protection shape can. What is overridden every single time is review, not verification, and that distinction is why the
override keeps feeling defensible while the rule keeps being meaningless.

The filter gap #22 closed was measured rather than inferred, and it is worth its own paragraph because it was hiding in
a comment that described itself. `ci.yml`'s `paths-ignore` held `docs/**`, which matches everything below `docs/`, and
`*.md`, which matches **one directory level** — so prose under `docs/` was free while prose anywhere else was not. PR
#20 changed four `docs/` files plus `infra/oci/README.md` and nothing else, and that single nested markdown file started
the whole matrix twice: run 41290349536 on the pull request (backend 27s, frontend 31s, infrastructure 19s) and run
41290409900 on the push to `master` (18s, 24s, 21s) — **140 runner-seconds for a documentation change**, on the very
commit whose message argued that documentation changes should not cost builds. `infra/**/*.md` is now in both trigger
sets, and the negative control is free: the PR that lands this paragraph touches `docs/**` and `infra/oci/README.md`
only, so it starts **no** `ci.yml` run — same repository, same class of edit, six jobs then and zero now. What that
buys is stated in the audit rather than glossed here: a prose-only change is now unverified by CI *by design*, so a
wrong sentence in `docs/` cannot fail a build. The control ran on this paragraph's own PR: head commit
`a5e2c0abcbd12d1c5d2c816c85975c2bbc5004e4` returns **zero** workflow runs from `GET /actions/runs?head_sha=`. That is
the accepted half of INF-10's trade, and it is another reason the required-checks decision outranks the runner-minute
saving.

The count is stated as twelve rather than as "every PR this year" because of what the API can and cannot prove.
`GET /pulls/N/reviews` was called once per PR for #10 through #22 and returned an empty array twelve times, so none of
them ever held an approving review, and `required_approving_review_count` has been 1 across that whole window —
re-read after the twelfth merge, still `contexts: []`, `strict: false`, `enforce_admins: false`, i.e. nothing about the
configuration changed to make any of this necessary. #8 and #9 also report zero reviews and `mergeable_state: blocked`,
but that field is recomputed against the *current* protection rules, so it cannot show what the configuration was when a
May 2026 PR merged. The defensible claim is therefore the window this programme owns: twelve merges, twelve overrides,
zero reviews. The merge that lands this paragraph is the thirteenth, and the pass that counts it is the fourteenth — a
documentation loop that only ends when the rule changes, not when it is measured.

Two measurement corrections belong here, because an earlier version of this section got them wrong and the errors were
in the direction of sounding worse. #13 and #14 are **forty-eight** seconds apart, not eighty-eight, and #15 follows
#14 by thirteen minutes, not ninety seconds; both figures now come from the merge commits themselves. Those commits are
also the timestamp source for #18, for a reason that has since become a finding of its own. `GET /pulls/18` reports
`merged_at` `2026-11-01T09:10:46Z` and `GET /actions/runs/41287750885` reports `created_at` `09:10:49Z`, while
`GET /commits/3cf8fa1` reports a committer date of `08:10:46Z` and the runner's own log lines for that job are stamped
`08:10:59Z`–`08:11:06Z` — one event described by two clocks exactly 3600 seconds apart. An earlier version of this
section recorded that split and stopped, calling it a one-off about a merge that had happened eleven minutes ago. It was
wrong to stop: #20 carries the identical gap (`merged_at` `09:48:41Z`, merge commit `08:48:41Z`, and the first log line
of its `master` run reads `2026-11-01T08:48:48.3628913Z` while that run's own `started_at` says `09:48:47Z`), while #21
and #22 carry none (`04aff7b` commit and `merged_at` both `09:56:05Z`; `3b6a6a5` `10:14:04Z` against `10:14:05Z`). Two
merges in twelve, then — not a property of the repository, and not the systematic offset either.

Which surface is the wall clock is settled without asking GitHub, because the commits *this machine* authored carry
this machine's clock and they bracket the disputed events: `c3f7860` is stamped `14:15:39+05:30` and `82ea32c`
`15:24:26+05:30`. If `merged_at` were the true clock, PR #20 landed at 15:18:41 IST — six minutes before the first
commit of the TEN-03 package, which contains a service rewrite, six new tests and a full `mvn verify`, and whose pull
run then started sixty-two minutes after the push that triggered it. On the commit clock the same two intervals are
sixty-six minutes of work and two minutes ten seconds of queue, which is what actually happened. All four merges above
carry `Committer: GitHub <noreply@github.com>`, so this is two GitHub services disagreeing, not a local merge artifact:
the merge-commit dates and the runner logs are the wall clock, and `merged_at`/`created_at`/`started_at` ran an hour
ahead for those two events. The practical rule, stated once: intervals in this programme come from merge commits and
runner logs and are never mixed with `merged_at`, and any cross-source comparison is off by 3600 seconds for #18 and
#20.

The change that would make the override unnecessary is recorded in
[INFRASTRUCTURE_AUDIT.md §11](../infrastructure/INFRASTRUCTURE_AUDIT.md) and is one settings call away.

The list above is unordered, and one ordering now matters enough to be stated as a rule:

1. Rotate the exposed Neon credential (item 1). Nothing else is safe until this is done.
2. Make the two CI checks required and turn on `enforce_admins` (item 5's substance) — repository settings, owner-only.
3. Provision `SECURITY_JWT_SECRET` and `APP_SUPERADMIN_*` as runtime configuration, not repository secrets
   (item 2), and confirm the runtime's env-file path (item 3).
4. Authorise the schema-only prod export for the Flyway baseline (item 4).
5. **Only after 1–4 and after Infra-M2/Infra-M3 land:** restore SSH to the host and add `EC2_SSH_KNOWN_HOSTS`.
   Doing this early is actively harmful — the missing known-hosts secret is presently the only thing preventing a
   merge to `master` from SSHing an unverified build into production, because `deploy-backend.yml` has no build step,
   no `environment:` gate and no required check upstream of it. See
   [INFRASTRUCTURE_AUDIT.md §6.7](../infrastructure/INFRASTRUCTURE_AUDIT.md).

Two further owner decisions are now unavoidable and are **not** engineering tasks, because both involve spend or an
account and mandate §2 forbids either without approval:

- **A domain name** (~$10–15/year) so the API origin can hold a publicly trusted certificate. Without it, INF-01
  cannot be closed with zero-cost tooling, and "HTTPS enforced" cannot honestly be claimed.
- **One off-host backup destination.** At the measured data size this is under $1/month, but a backup that lives
  only on the host is not a backup, and choosing a vendor, an account and a key is the owner's call
  ([ADR-009](../infrastructure/INFRASTRUCTURE_DECISIONS.md)).

---

## 12. Entry criteria for Milestone 1

M1 (`fix/gms-critical-security`) may start when the following are true — the first two are already satisfied, the
rest are the owner items above:

- [x] A reproducible build verdict (B1/B2).
- [x] A findings register with IDs that code changes and tests can cite.
- [ ] Owner acknowledgment of the credential exposure (rule 5 of §25 makes this non-optional).
- [ ] Agreement on the resource-disclosure policy (403 vs 404) so §4.1-style probes become deterministic assertions.
- [ ] A disposable-database recipe (`make db-dev`, or Compose-only) that makes it impossible to reach production by
      accident — this is the fix for the trap described in §5 row 4, and it is the reason M1 changes configuration
      before it changes controllers.

### Definition of "baseline achieved"

A verified engineering baseline now exists: every P0/P1 claim in the three audit documents is backed by a command in
§2 or a probe in §4, the gap list in §8 is explicit about what remains unverified, and no claim in this file depends
on a database, host or deployment that was not touched during the measurement.

---

## 13. M1 verification evidence (`fix/gms-critical-security`)

Everything above §13 describes the **baseline**; nothing in it was edited to reflect the remediation, because a
before/after document is only useful if the *before* stays fixed. The per-finding dispositions are in
[REPOSITORY_AUDIT.md §16](REPOSITORY_AUDIT.md); this section is the command evidence behind them.

**Environment.** Local `dev` profile, JDK 21.0.1, PostgreSQL 16 in container `gms-postgres`, backend on `:8080`.
The operator-supplied test account was created from `APP_SUPERADMIN_*` environment variables only. No Neon
connection was made at any point in M1 (§25 rules 1–2).

### 13.1 Build and tests

| Command | Result |
| --- | --- |
| `mvn -o -DskipTests compile` (JDK 21) | BUILD SUCCESS |
| `mvn -o test` | **Tests run: 36, Failures: 0, Errors: 0, Skipped: 0** — BUILD SUCCESS |
| surefire per class | `JwtTokenProviderTest` 10 · `CustomUserDetailsServiceTest` 6 · `SuperAdminBootstrapPropertiesTest` 8 · `GlobalExceptionHandlerTest` 4 · `UserServiceImplRoleGuardTest` 4 · `LoginAttemptServiceTest` 4 |
| JaCoCo | `prepare-agent` + `report` executions now bound to `verify`; report produced against the same 36 tests |
| Baseline comparison (§2) | `mvn test` used to report **BUILD SUCCESS while executing nothing**. It now executes, and one of the 36 tests found a real defect rather than confirming one: the retired-default check was case-sensitive, so the historically shipped `SuperAdmin123!` passed it |

### 13.2 Security matrix — `scripts/verify-security.sh`, 18 checks, 0 failures

The matrix is committed, not a transcript: `scripts/verify-security.sh` re-runs every row below against any
`GMS_BASE_URL`. Its first execution returned **11 PASS / 7 FAIL**, and the discrepancy was the harness, not the
product: a previous manual probe had left the test account `account_locked=true`, so every token-dependent check
inherited a locked state. The script now unlocks its own test account before §2 and on exit (`trap`), and prints the
raw login body when it cannot extract a token. An assertion that depends on undeclared prior state is not evidence.

```
1. Anonymous surface        GET /actuator/health 200 · /api/v1/ping 200 · /actuator/info 401 · /students 401 · /auth/self-register 401
2. Credential handling      wrong password 401 · correct password issues a token · JWT forged with the retired default secret 401
3. Authorisation            /users/me 200 · /users 200 · /students/my-fees 403 for an admin · /actuator/info 200 for SUPERADMIN · unlisted path 403 · denied body leaks no internals
4. Error shape              per-field validation errors present · body leaks no internals
5. Brute-force throttling   ten failures set account_locked=true (read from the database) · correct password after lock 401
GMS_BASE_URL=http://localhost:8080  checks=18  failures=0
```

### 13.3 Log inspection (SEC-07)

`grep -c '\$2a\$' ` over the captured dev-profile boot log covering the entire matrix run — every failed login, the
lockout, and dozens of authenticated reads — returns **0**. The login-failure line is
`Login failed for smokeadmin: BadCredentialsException`; the principal line is
`Loaded authentication principal smokeadmin (enabled=true, locked=false, passwordExpired=false)`.

### 13.4 Error-contract probes (BKL-07, the row that failed first)

`POST /api/v1/users/create` as SUPERADMIN. The payloads are shown as sent; the first run of these five probes is what
discovered BKL-08.

| Payload condition | Baseline | Now |
| --- | --- | --- |
| `email` = `not-an-email` | 500 (no `@Email` constraint existed) | `400 {"error":"Validation failed","fields":{"email":"must be a well-formed email address"}}` |
| duplicate email | 500 `{"error":"Internal Server Error"}` | `400 {"error":"Invalid request","message":"Email already in use"}` |
| `password` below `PasswordValidator` | 500 | `400 {"error":"Invalid request","message":"Password does not meet policy requirements"}` |
| creator reference unresolvable (BKL-08) | 500 | `404 {"error":"Not Found","message":"The requested record does not exist."}` |
| any 500 still reachable | class, root cause, two stack frames | `{"traceId":…,"path":…,"error":"Internal Server Error","message":"The request could not be completed. Report reference <id>."}` |

### 13.5 What changed about §5's "clean clone" walk

Two rows of the §5 table no longer describe the repository, and the developer-facing trap in row 4 is closed:

* `mvn test` no longer requires the operator to know that JDK 24 breaks the build — it requires JDK 21 (§1) and
  executes 36 tests.
* **`mvn spring-boot:run` no longer connects to the hosted database.** The datasource is `${DB_URL:}` with blank
  defaults, `dev` supplies a localhost container URL, and `StartupSecurityValidator` aborts any non-`dev` boot that
  has a blank datasource, a writable `ddl-auto`, a wildcard/empty CORS origin, an incomplete enabled bootstrap, or
  `com.gms` at DEBUG/TRACE. A fresh clone can now reach a running system without touching production.
* Not yet closed: `docker compose config` still fails without a `.env` (`env_file:` on both services — §5 row 2), and
  the frontend still has no test command (§5 row 8).

### 13.6 Still unverified after M1

The EC2 host remains unreachable (`ssh: connect to host … port 22: Connection timed out`), so **no M1 change has been
observed in production**. `ci.yml` has now executed — its first run, on PR #10, reported
`Backend build and tests=success, Frontend lint and build=success` on a clean `ubuntu-latest` runner, which is the
strongest form of evidence §13.1 could ask for, because §13.1's local result and the CI result are now the same claim
measured twice on different machines. Re-measured in Infra-M0: the merge commit `7a75d39` also ran CI green in 50 s,
and **`deploy-backend.yml` did run** on that same push (run 41245769175) — it was refused by its own host-key guard,
so the accurate statement is that the deploy pipeline has never *reached* the host, not that it has never executed.
`EC2_HOST`, `EC2_USER` and `EC2_SSH_KEY` are already provisioned; `EC2_SSH_KNOWN_HOSTS` is not. Still unverified: the
prod profile has never booted, and no check is marked required, so CI reports rather than enforces
(`required_status_checks: []`, `enforce_admins: false` — re-fetched 2026-11-01). The honest reading of this branch
remains "the code awaiting deployment is measurably safer than the code deployed"; §27 forbids more than that.

Also unverified by choice: the frontend gates in `ci.yml` are **advisory**. `npm run lint` fails on 47 pre-existing
errors (FE-14) and there are no frontend tests yet (F3), so only `npm ci` and `npm run build` can block a merge today.
Marking a step `continue-on-error` is not the same as green — it is a gate that reports and does not enforce, recorded
here so nobody reads "CI passed" as "the frontend was checked".

### 13.7 State left on this machine by M1

Supersedes §10 for the items it touches.

* Container `gms-postgres` is running, holding the same disposable schema. It now additionally contains: a second user
  row (`smokeadmin`, SUPERADMIN, created from environment variables during verification), and **one deliberately
  inserted `employees` row (id 9001) attached to that user** so that the BKL-08 creator lookup could be exercised past
  its failure. That row is probe furniture, not product data; `docker compose down -v` removes it. Never use
  `docker compose down -v` against anything but this container.
* The backend was left **running** on `:8080` (`dev` profile, launched from `gms_backend` with `.env` sourced).
  Stop with `lsof -ti tcp:8080 | xargs kill`.
* `/tmp/gms-m1-boot.log` holds the boot and matrix log referenced in §13.3. It is outside the repository and contains
  no credential material.
* `/Users/shubhamkumar/Desktop/GMS-V1/.env` (git-ignored) supplies `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` and the
  dev-profile bootstrap variables used above. It is not committed and is not copied into any file in this document.

---

## 14. Stage 3 `C-1` verification evidence (`feat/stage2-flyway-baseline`)

Everything above §14 describes the **baseline at commit `256b8e4`** and the **M1** remediation; none of it was edited
to reflect `C-1`, for the reason §13 states. This section is the command evidence behind the `C-1` disposition in
[REPOSITORY_AUDIT.md §16](REPOSITORY_AUDIT.md) and [SECURITY_AUDIT.md §5.2](SECURITY_AUDIT.md).

**Environment.** JDK 21.0.1, PostgreSQL 16.15 in container `gms-postgres`, the packaged jar
(`target/gms-0.1.0-SNAPSHOT.jar`) run with `--spring.profiles.active=dev` against a **disposable database created
for this verification**. The platform account was created from `APP_SUPERADMIN_*` environment variables only, and the
JWT secret was generated per run. No Neon connection was made at any point, and no schema outside the disposable
database was touched: the pre-existing `gms` database was left exactly as it was.

### 14.1 The schema is reproducible from Flyway alone

The `gms` database already held a schema created by an earlier Hibernate `ddl-auto=update` run and **no**
`flyway_schema_history`, so a boot against it fails with *"Found non-empty schema(s) "public" but no schema history
table"*. That is the correct refusal, and it is the reason the verification used a second database rather than
adopting the old schema with `baselineOnMigrate`.

| Command | Result |
| --- | --- |
| `CREATE DATABASE gms_c1_verify` on the disposable container | empty — `psql -c "\dt"` → *"Did not find any relations"* |
| boot the packaged jar against it | `Migrating schema "public" to version "1 - baseline current schema"` … `"5 - composite tenant keys"`, then **`Successfully applied 5 migrations to schema "public", now at version v5`** (0.679 s) |
| Hibernate validation | no mismatch — the boot proceeds past `ddl-auto=validate` to `Started GmsApplication in 6.133 seconds` |
| `select installed_rank, version, description, success from flyway_schema_history order by installed_rank` | 5 rows, versions 1–5, `success = t` on all |
| `ls gms_backend/src/main/resources/db/migration` | `V1__baseline_current_schema.sql`, `V2__materialise_tenant_key.sql`, `V3__natural_keys.sql`, `V4__indexes.sql`, `V5__composite_tenant_keys.sql` |
| `unzip -l target/gms-0.1.0-SNAPSHOT.jar \| grep -E "db/migration\|flyway"` | `BOOT-INF/classes/db/migration/V1..V5` and both `flyway-core-10.10.0.jar` and `flyway-database-postgresql-10.10.0.jar` |

### 14.2 Build and tests

| Command | Result |
| --- | --- |
| `mvn -B test` | **Tests run: 79, Failures: 0, Errors: 0, Skipped: 0** — BUILD SUCCESS |
| surefire per class | `JwtTokenProviderTest` 10 · `SuperAdminBootstrapPropertiesTest` 8 · `GlobalExceptionHandlerTest` 8 · `EndpointInheritanceTest` 33 · `CustomUserDetailsServiceTest` 6 · `NotificationRecipientMatchTest` 6 · `UserServiceImplRoleGuardTest` 4 · `LoginAttemptServiceTest` 4 |
| `mvn -B package` | BUILD SUCCESS; the packaged jar was the one the live run below used |

The 42 pre-existing tests are still green. Nothing was skipped, nothing was marked `continue-on-error`, and no
assertion was weakened.

### 14.3 Live cross-tenant run — `scripts/verify-c1-tenancy.sh`, 29 checks, 0 failures

The script is committed, not a transcript: it creates two schools and their administrators, a teacher, a classroom and
a student, and asserts the boundary from both sides. It runs against any `GMS_BASE_URL`.

| Group | Assertions | Observed |
| --- | --- | --- |
| Platform login | 1 | `POST /auth/login` → 200 |
| Two schools created by the platform owner | 2 | 200, 200 — school A id 2, school B id 3 |
| Both school administrators log in | 2 | 200, 200 — `aprincipal`, `bprincipal` |
| School A builds its own data | 3 | classroom 200, subject 200, student 200 |
| School A provisions a teacher | 2 | account 200, login 200 |
| Reads C-1 re-declared | 5 | own student **200** · another school's student **403** · anonymous **401** · missing student **404** · another school's subject **403** |
| Deletes C-1 re-declared | 4 | teacher delete **403** · another school's delete **404** · own delete **204** · deleted row then **404** |
| Referenced row | 3 | another school's classroom **404** · classroom holding a student **409** · unreferenced classroom **204** |
| Routes the deleted base class used to supply | 4 | student **405** · parent **403** · employee **405** · school **405** |
| Method-level refusal | 1 | teacher reading a student **403** |
| Platform reach | 1 | `GET /schools` → 200, three schools listed |

### 14.4 Three 500s the live run found, and what they were

None of these three was visible in the code; each was found by making a request.

| Request | Was | Now | Cause |
| --- | --- | --- | --- |
| `DELETE /api/v1/students/1` | **500** | 405 | `HttpRequestMethodNotSupportedException` — the path still answers `GET`, so the method mismatch reached the catch-all |
| `DELETE /api/v1/classrooms/1` (a classroom with a student) | **500** | 409 | `DataIntegrityViolationException` — `students.classroom_id` still references the row |
| `GET /api/v1/students/1` as a `TEACHER` | **500** | 403 | `AuthorizationDeniedException extends AccessDeniedException` — **every** method-level refusal in the app answered 500 |
| `PUT /api/v1/subjects/update/` (an id that is not an integer) | **500** | 400 | `MethodArgumentTypeMismatchException` |

The third is the serious one: it was not specific to `C-1`, it was the answer to every refusal the method layer had
ever produced. `GlobalExceptionHandler` now maps `AccessDeniedException` → 403,
`HttpRequestMethodNotSupportedException` → 405, `DataIntegrityViolationException` → 409 and
`MethodArgumentTypeMismatchException` → 400, each with the same `{traceId, path, error, message}` envelope and no
disclosure. The rest of the client-error mapping — 415, an unreadable body, a missing parameter — is still outstanding
and is now recorded as **BKL-10**.

### 14.5 What the live run found about the frontend contract

`FE-01` is a Stage 4 card, but `C-1`'s live run measured two of its rows rather than assuming them.

| Frontend call | Backend route | Measured |
| --- | --- | --- |
| `apiSlice.js:249` `PUT /classrooms/{id}` | `ClassroomController` declares `PUT /classrooms/update` (id in the body) | **405** — the classroom edit form calls a route that does not exist |
| `apiSlice.js:281` `PUT /subjects/update/{id}` | `SubjectController.java:47` declares `PUT /subjects/update/{id}` | **200** — the subject edit is fine |
| `apiSlice.js:258` `DELETE /classrooms/{id}` | inherited before `C-1`; now declared and tenant-scoped | **204** for the caller's own row, **404** for another school's |
| `apiSlice.js:290` `DELETE /subjects/{id}` | inherited before `C-1`; now declared and tenant-scoped | **204** for the caller's own row, **404** for another school's |

### 14.6 State left on this machine by `C-1`

* Container `gms-postgres` is running. It holds three databases: `gms` (the pre-existing one, **untouched** by this
  verification), `gms_flyway_verify` and `gms_c1_verify` (the two disposable ones built by Flyway during this run,
  holding the schools, teachers and students the script created). `docker compose down -v` removes the container and
  all three.
* The backend was left **running** on `:8080` against `gms_c1_verify`, started from the packaged jar with
  `APP_SUPERADMIN_*` and `SECURITY_JWT_SECRET` supplied as environment variables for that run only. Stop with
  `lsof -ti tcp:8080 | xargs kill`.
* `/tmp/gms-boot*.log` and `/tmp/gms-c1-final.log` hold the boot logs referenced in §14.1 and §14.4. They are outside
  the repository and contain no credential material — the JWT secret was generated per run and never written to a
  file. The disposable platform password lives in `/tmp/gms-c1-secret.env` (mode 600), also outside the repository.

---

## 15. Stage 3 `C-2` verification evidence (`feat/stage2-flyway-baseline`)

Section §14 described the `C-1` disposition; this one describes `C-2`, the card that gives the tenant a single
resolution path. Everything above §14 is unchanged, for the reason §13 states.

**Environment.** The same as §14: JDK 21, PostgreSQL 16.15 in container `gms-postgres`, the packaged jar run with
`--spring.profiles.active=dev` against the **disposable** database `gms_c2_verify`, created empty and built by
Flyway (5 migrations, `V1` → `V5`). The platform account came from `APP_SUPERADMIN_*` environment variables only.
No Neon connection was made and the pre-existing `gms` database was not touched.

### 15.1 What changed, measured on the code

| Measure | Before | After |
|---|---|---|
| Files that re-parse and re-verify the JWT signature | 41 (325 call sites) | **0** — `util/SecurityUtil.java` deleted, 100 lines |
| Service methods that take the tenant as a parameter | 221 | **0** — pinned by `TenantParameterArchTest` |
| Service interfaces rewritten | — | 35 |
| Service implementations rewritten | — | 31 |
| Controller locals that became dead | — | 201 removed across 22 files |
| Reads that re-verified one token three times (username, roles, school) | every controller | **once**, in `JwtAuthenticationFilter` |

The tenant now arrives as a `ThreadLocal` principal that the filter fills from the claims it has already verified and
clears in a `finally`, so a pooled thread cannot carry one caller's tenant into the next caller's request.

### 15.2 Build and tests

```
mvn -B clean test
Tests run: 86, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

**86 tests green**: 79 before this card, plus 5 in `TenantContextTest`, 1 in `TenantParameterArchTest` and 1 in
`GlobalExceptionHandlerTest`.

**One environment trap, recorded so the next person does not lose an hour to it.** The suite must be run on **JDK
21**, which is what the project targets (`pom.xml` `<java.version>21</java.version>`) and what CI pins
(`.github/workflows/ci.yml:51`, with the comment *"the project targets 21; JDK 24 breaks Lombok"*). This machine's
default `java` is **JDK 24**, and on it the run fails with **38 errors** that look like application failures and are
not:

```
java.lang.IllegalArgumentException: Unsupported class file major version 68
    at org.jacoco.agent.rt.internal_4a7f17c.asm.ClassReader.<init>
```

That is the JaCoCo agent (0.8.10) trying to instrument JDK 24's own classes — class file major version 68 — and it
is a **tooling** failure, not a test failure. The same suite on JDK 21 is 86/86 green. Fix: run
`export JAVA_HOME=$(/usr/libexec/java_home -v 21)` first. Bumping JaCoCo to a version that understands class file v68
would remove the trap for anyone on a newer JDK; it was left alone here because 0.8.10 is the only version in the
local Maven repository and the change could not be verified offline. `OPS-12` owns that upgrade.

### 15.3 The deliberate-failure control

`TenantContextTest.theTokenIsVerifiedOncePerRequest` asserts `verify(tokenProvider, times(1)).parseClaims(anyString())`.
To prove the assertion had teeth, a second `tokenProvider.parseClaims(token)` call was added to the filter as a
deliberate failure. The suite went red on **exactly that one test** ("Wanted 1 time but was 2 times") and the call
was removed. Without this control a `times(1)` assertion that never fires would look identical to one that holds.

### 15.4 Live run — `scripts/verify-c2-tenant-context.sh`, 32 checks, 0 failures

Run against the running instance and the real database. Two schools, two administrators, a teacher, two classrooms and
two students were created; every row-level check reads the database through `docker exec`.

| Group | Checks | Measured |
|---|---|---|
| Platform login | 1 | 200 |
| Two schools created | 2 | 200 · 200 |
| Two administrator logins | 2 | 200 · 200 |
| School A builds data | 3 | classroom 200 · subject 200 · student 200 |
| School B builds data | 2 | classroom 200 · student 200 |
| The tenant is the one in the token | 9 | own student **200** · another school's student **403** · each list holds rows from **exactly one** school · neither list contains the other school's student · the two schools are **different** tenants |
| The audit trail comes from the token's employee | 7 | teacher list by role **403** · `users.created_by` = the caller's account · `users.school_id` = the caller's school · `students.school_id` = the caller's school · school A's administrator wrote **0** rows in school B |
| A cross-service check refuses another school's row | 2 | **403** · the refused student was **not written** |
| A token that does not verify | 2 | tampered **401** · no token **401** |
| The platform owner is not a tenant | 2 | lists schools **200** · sees both new schools |

The row-level checks report **SKIP**, never PASS, when `GMS_VERIFY_DB` is not set.

### 15.5 The regression check — `scripts/verify-c1-tenancy.sh`, 29 checks, 0 failures

`C-1`'s script was re-run against the same build and the same instance. Every check still passes, including the
cross-tenant refusals (403 for another school's row, 404 for a row that is not there, 409 for a referenced row).

### 15.6 Two defects the review found, and one the live run found

| Defect | How it was found | Effect if shipped | Repair |
|---|---|---|---|
| The transformation rewrote the **string literal** `"schoolId"` inside three `BeanUtils.copyProperties` ignore-lists into `"TenantContext.schoolId()"` | diff review — the rewrite was textual, not syntactic | the ignore list names no bean property, so the property stops being ignored | restored to `"schoolId"`; the grep for `"[^"]*TenantContext\.` now returns nothing |
| `AuthController.selfRegister` called `findByEmployee_Id(employee.getId())` — the employee **being registered**, who is not the caller. The rewrite made it the caller's own employee | a scripted audit of every identity argument at every call site against `HEAD` | the endpoint is unauthenticated, so the context is empty, the uniqueness check silently passes, and an employee can hold two accounts | `UserService.existsByEmployee_Id(Integer subjectEmployeeId)` — the employee is the subject of the question, and the parameter is named for what it is |
| A cross-tenant refusal answered **400** on the write path and **403** on the read path | the live run: `POST /api/v1/students/create` naming another school's classroom answered 400 | the same refusal had two answers depending on the endpoint | 23 refusals now throw `TenantAccessDeniedException extends AccessDeniedException`; both paths answer 403 |

The scripted audit in the second row is the one worth keeping: it read every service interface at `HEAD`, recorded the
old parameter order, and listed every call site whose identity argument was **not** the caller's own id. It reported
12 suspects, of which 9 were the caller's own token lookup (`SecurityUtil.getSchoolIdFromToken()`), 1 was
`employee.getId()`, 1 was `student.getSchoolId()`, and 2 were a literal `null`.

### 15.7 One deliberate behaviour change

`ParentServiceImpl.getChildFees` called `studentFeeService.getStudentFeesByStudent(studentId, null)`. The callee
compared `student.getSchoolId().equals(null)` — always false — so it answered **403**, and the parent saw an **empty**
fee list for every child, always. The tenant now comes from the context, the comparison is against the caller's
school, and the query is school-scoped in SQL. The parent's fee list is now populated. This is a fix; it is recorded
because it changes what a parent sees.

### 15.8 What `C-2` does not close

`C-2` gives every service one way to learn the tenant. It does not give every **query** a school predicate. The
queries that still span schools, and were `C-3`'s work list:

`resultRepository.findByStudentExam_Student_Id` · `studentExamRepository.findByStudentId` ·
`feePaymentRepository.findByStudentFee_Student_Id` · `invoiceRepository.findByStudentId` ·
`receiptRepository.findByStudentId` · `attendanceRepository.findByStudent_IdAndDateBetween` ·
`timetableRepository.findByClassroom_Id` · `teacherAssignmentRepository.findByTeacher_Id` ·
`parentRepository.findByStudentsId` · `userRepository.findByRole`

`TEN-01` is therefore **partly** closed by `C-2` alone: the tenant is unambiguous and cannot be named wrongly, but
the reads above still reached across schools. **`C-3` shipped on 2026-11-01 and every method in this list is gone** —
rewritten tenant-keyed, or deleted with the scoped form kept. §16 records the measurement; the list is left here
because it is the record of what `C-2` owed, and a document that quietly rewrites its own history is worth less than
one that shows the correction.

### 15.9 State left on this machine by `C-2`

Superseded by §16.6.

## 16. Stage 3 `C-3` — the tenant reaches the SQL (2026-11-01, `feat/stage2-flyway-baseline`)

### 16.1 The rule, and the instrument that holds it

`RepositoryTenantScopeArchTest` (`gms_backend/src/test/java/com/gms/repository/`) reads the declared methods of every
Spring Data interface in `com.gms.repository` and fails the build when a read carries no tenant and records no reason.
A read counts as scoped when its derived name walks into `school` (`…School_Id…`), when a parameter names the tenant,
or when its `@Query` filters on `school.id`/`school_id`. The allow-list carries a reason per entry, and an entry whose
method no longer exists fails the test too, so the list cannot outlive the code it describes.

The test prints its own count on every build. Run against `8981842`, the build that ended `C-2`, it is red; run
against this branch, green:

| | declared reads | carry the tenant | carry none | offences |
|---|---|---|---|---|
| at `8981842` | 170 | 115 | 55 | **37** |
| on this branch | 168 | 150 | 18 | **0** |

The 18 are 12 on `SchoolRepository` (that table *is* the tenant), 4 on `UserRepository` (2 on `uq_users_username`,
global by `V3:34`; 2 transitively scoped through the employee) and 2 on `ParentRepository` (`uq_parents_email`,
global by `V3:38` and by owner decision D-2).

**Two blind spots are recorded in the test, not hidden.** `SchoolRepository.findBySchoolId`/`existsBySchoolId`
satisfy the name rule while meaning the school's *own* business code, not a tenant predicate — a name that reads like
a scope passes. And the scan sees declarations only: the `findById`/`findAll`/`findAllById`/`delete` that
`JpaRepository` hands every repository are inherited, and are `C-4`'s.

### 16.2 Code changed

17 repository interfaces and 13 services (22 call sites). `findByEmail`, `findByUserId`, `existsByUserId`,
`existsByEmail` and `findByRole` are gone from `UserRepository`; `EmployeeRepository`'s four global forms are gone;
`TeacherAssignmentRepository` was rewritten whole; `TimetableSlotRepository` went from empty to two scoped reads.
Deleted rather than rewritten: the `EmailLog`/`SmsLog` `findByToEmail`/`findByToPhone` lookups (each had a scoped
sibling and no caller), `TeacherAssignment.findByAcademicYear` (a true duplicate of the scoped method that already
existed), `TimetableRepository.findAll()` as used by `getTimetableForTeacher` (a platform-wide walk any authenticated
caller could trigger), and `StudentRepository.findAllById(ids)` in `ParentStudentServiceImpl.getChildFees`.

### 16.3 One deliberate behaviour change

User and employee email uniqueness went from **global** to **per school**, which is what `uq_users_school_email`
(`V3:41`) and `uq_employees_school_email` (`V3:53`) promise. The global check was stricter than the schema and was a
cross-tenant oracle: it told school A that school B had already registered a mailbox. Before changing it, every
caller of `existsByEmail` was re-read to confirm it runs inside a tenant — a null tenant would have made the scoped
check silently always-false, which is the exact defect `C-2` found in `selfRegister`.

### 16.4 Verification

* `mvn test` on JDK 21: **87 tests, 0 failures, 0 errors** (86 before this card, +1 arch test).
* **The application boots.** Spring Data parses every derived path at bean creation and the `@WebMvcTest` suite
  excludes JPA, so no unit test can catch a bad property path. The packaged jar was started against an empty
  database: Flyway applied `V1`→`V5`, `Started GmsApplication in 6.189 seconds`. Proved to be a real check by adding
  `findBySchool_IdAndNotARealProperty` to `StudentRepository`, which aborts startup with
  `PropertyReferenceException: No property 'notARealProperty' found for type 'Student'`.
* `scripts/verify-c3-scoped-reads.sh` — new, **25 checks, 0 failures, 0 skipped**.
* Regressions on the same build and database: `verify-c1-tenancy.sh` **29/29**, `verify-c2-tenant-context.sh`
  **32/32**.
* **Deliberate-failure controls, four of them.** (1) An unscoped `findByName` added to `SubjectRepository` turns the
  arch test red on exactly that entry. (2) An allow-list entry whose method does not exist turns it red on the stale
  list — this control initially *passed* when it should have failed, because `cp`/`mv` restore an older mtime and
  Maven then skips the recompile; the test was re-validated against forced-rebuilt bytecode before it was trusted.
  (3) The invalid derived path above, against the boot. (4) The whole C-3 script re-run against the pre-C-3 build in
  a separate worktree: **24 pass, 6 fail**, headed by
  `school B naming school A's classroom finds no timetable (expected 404, got 200)`.
* The predicate was captured from the running application, not inferred from a method name:

  ```sql
  select t1_0.id, … from timetables t1_0 join classrooms c1_0 on c1_0.id = t1_0.classroom_id
  where c1_0.school_id = ? and t1_0.classroom_id = ?
  ```

### 16.5 What `C-3` does not close, and the measurement that says so

The join in that SQL is the finding: the tenant travels through `classrooms.school_id` because `timetables.school_id`
is **NULL**. Measured on the verification database after the run — all nine `V2` `school_id` columns exist, and the
timetable row the application created through its own service has `school_id = NULL`; no Java entity maps the column.
Since Postgres does not enforce a composite FK whose column is NULL (MATCH SIMPLE), `V5`'s same-school constraint is
currently bypassable by the application's normal writes. That is `C-7`, and it is now measured rather than asserted.
`C-4` (inherited reads), `C-5` (deny-by-default on endpoints) and `C-8` are untouched by this card.

*Closed the same day by `C-7` — see §17. The measurement above is kept because it is what made the card's shape
inevitable: the fix is not the DDL, which Stage 2 had already written, but the nine mappings and the eleven
assignments that let the DDL be honest.*

### 16.6 State left on this machine

* Container `gms-postgres` is running. It now holds `gms` (pre-existing, **untouched**), `gms_schema_probea`,
  `gms_schema_probeb`, `gms_v3_negative`, `gms_flyway_verify`, `gms_c1_verify`, `gms_c2_verify`, `gms_c3_r1` and
  `gms_prec3_r1` — the last two built by Flyway during this card. All are disposable; `docker compose down -v`
  removes the container and every one of them. No non-disposable database was connected to, and the retired
  `gms-v1-neon` host was not contacted or read.
* The backend is left **running** on `:8080` (pid in `lsof -ti tcp:8080`) against `gms_c3_r1`, started with
  `APP_SUPERADMIN_*`, `SECURITY_JWT_SECRET` and `JPA_SHOW_SQL=true` supplied as environment variables for that run
  only. Stop with `lsof -ti tcp:8080 | xargs kill`. The pre-C-3 control instance on `:8081` was stopped and its
  `git worktree` removed; `git worktree list` shows only this repository.
* `/tmp/gms-c3-app.log` (boot + SQL), `/tmp/gms-c3-boot.log`, `/tmp/gms-prec3-app.log` and
  `/tmp/gms-c3-secret.env` (mode 600, disposable platform password) are outside the repository. No credential in
  this document, the script, or the repository.


## 17. Stage 3 `C-7` — the tenant key becomes enforced (2026-11-01, `feat/stage2-flyway-baseline`)

### 17.1 The rule, and the instrument that holds it

`EntityTenantColumnArchTest` (`gms_backend/src/test/java/com/gms/model/entity/`) does the half of the job a repository
test cannot: it reads every `V*.sql` off the classpath, collects the tables that declare `school_id integer`, maps the
`@Entity` classes to their `@Table` names, and fails the build when a tenant table's entity does not map the column —
or when an allow-listed entry no longer describes a real table. The allow-list has exactly one entry, with its reason:
`parent_student` is a `@ManyToMany` join table (`Parent.java:41-47`), so Hibernate writes `parent_id` and `student_id`
and there is no field for a third column; `V6` materialises its tenant with a trigger and `C-8` replaces it with an
entity. The test also self-guards: `assertThat(tenantTables).hasSizeGreaterThanOrEqualTo(30)`, because a regex that
stopped matching would shrink the set and turn a red test green without a line of code changing. It prints its own
measurement on every build:

```
C-7: 35 tables carry an integer school_id, 36 entity classes cover 34 of them, 1 have no entity and a reason
```

The two numbers that are not the same size are the two facts. 36 entity classes cover 34 *tenant* tables because two of
them (`School`, `Parent`) sit on tables that carry no tenant by design, and 34 + 1 = 35 because the one table without an
entity is allow-listed with a reason rather than forgotten.

**One number this card does not have.** The honest way to read an arch test is before-and-after, the way `C-3`'s is
recorded in §16.1. `C-7`'s test cannot be run against the pre-`C-7` build without a worktree at `HEAD` plus a copy of
the new test file and the new migration it reads, so no before/after pair is recorded here. §17.5's control says the
same thing in a stronger form: the test is red on exactly the entity you break, and names it.

### 17.2 Code changed

* **Nine entities** now carry the canonical tenant block — `@JsonIgnore` + `@ManyToOne(fetch = LAZY, optional = false)`
  + `@JoinColumn(name = "school_id", nullable = false)`: `Attendance`, `ExamSubject`, `FeePayment`, `Result`,
  `StudentExam`, `StudentFee`, `TeacherAssignment`, `Timetable`, `TimetableSlot`. `@JsonIgnore` because the association
  would otherwise serialise a lazy proxy from a controller that returns the entity.
* **Eleven assignments across eight services** set it: `AttendanceServiceImpl` (×2 — single and bulk register),
  `ExamServiceImpl` (exam subject), `FeePaymentServiceImpl`, `ResultServiceImpl`, `StudentExamServiceImpl`,
  `StudentFeeServiceImpl`, `TeacherAssignmentServiceImpl`, `TimetableServiceImpl` (×2 — timetable and slot). Every one
  takes the tenant from `TenantContext.schoolId()`, none copies it from the parent row. Copying is the failure this
  card exists to avoid: a tenant inherited from `exam.getSchool()` satisfies the NOT NULL and makes
  `fk_…_same_school` a comparison of a value against the row it was copied from, which can never disagree.
* **`TimetableSlotRepository.findByIdAndSchool_Id`** — a new scoped read, and the reason `C-3`'s counter moved from
  168/150 to 169/151 without either card touching the other's test.
* **`AttendanceServiceImpl`** stopped discarding `request.getTimetableSlotId()`. It read the field, left a TODO where
  the assignment belonged, and wrote the row against a NOT NULL column — so `POST /attendance` could not have
  succeeded on any build since the column existed. The single path now refuses a missing slot with 400, resolves the
  slot tenant-scoped, and sets it; the bulk path loads it once and restores the classroom match the code had commented
  out; the `PUT` path keeps the existing slot when the request names none, instead of writing a null over it.
* **Three create paths became `@Transactional`** (`StudentExamServiceImpl`, `FeePaymentServiceImpl`,
  `ResultServiceImpl`): `mapToResponse` walked a lazy proxy after `save()` returned and outside any session, so
  `POST /student-exams` and `POST /fee-payments` answered **500** with `LazyInitializationException` (`Exam#1`,
  `Student#1`, "no Session"). Pre-existing, found by the live run, not caused by this card.
* **`GlobalExceptionHandler.handleConflict`** now distinguishes a `*_same_school` violation (and the trigger's
  `not the student's school`) from an ordinary integrity failure: **403** `That record belongs to another school.`,
  with a test that neither the constraint name nor the table name reaches the response body.

### 17.3 The migration, and why it is `V6`

`V6__require_the_tenant_key.sql`, four sections: (1) ten idempotent backfills, a ten-clause `DO $$` assertion that names
the table it refused to flip, and the ten `SET NOT NULL`; (2) `parent_student_tenant_key()` plus
`CREATE TRIGGER trg_parent_student_tenant_key BEFORE INSERT OR UPDATE OF student_id, school_id` — derives from the
student, raises if the student is missing, and **raises** on a disagreement rather than overwriting a value somebody
hand-wrote; (3) four `(id, school_id)` candidate keys and seven same-school foreign keys; (4) `V4`'s and `V5`'s index
rules re-run, copied rather than shared, and the assertion this file exists for — no table outside `schools`,
`email_logs` and `sms_logs` still carries a nullable `school_id`.

Measured on the migrated database: 39 tables; **35** integer `school_id` columns, **33** of them NOT NULL (the two
exceptions are the dead log tables `COM-05` describes, and the exemptions are written in the migration with the reason
— `EmailLogService` and `SmsLogService` have no implementation, so a NOT NULL on their tenant column is an outage
waiting for the first writer rather than a control); **141** foreign keys of which **18** are same-school pairs (11 from
`V5`, 7 from `V6`); **45** named unique constraints; **175** indexes. `schools.school_id` is excluded from the 35: it is
`varchar(20)` and it is the school's own business code, not a tenant reference.

It is `V6`, not the `V7` the card originally said — and the reason is a number in the repository, not a number in a
database. `git ls-tree HEAD gms_backend/src/main/resources/db/migration/` returns `V1`–`V5` and nothing else, and
`git log --all --diff-filter=A` shows exactly one commit (`231bdab`) ever adding a migration file, so no branch or
database owned by this repository is at v7; the verification databases from Stage 2 and from `C-1`/`C-2`/`C-3` all read
`1,2,3,4,5` in `flyway_schema_history`. `V6` was therefore the next free slot, and `V7` — the number §5.1 and §9 of
`ARCHITECTURE.md` reserved for the recipient join tables — is released to `C-8`. The paragraph this one replaces claimed
the branch's history already reached v7, which was true of this machine for an hour and is not true of the branch; the
`outOfOrder=false` hazard it described is real and was observed, just not against any committed state. While the file was
named `V7__require_the_tenant_key.sql` a local database migrated to v7, and after the rename Flyway refused the pending
`V6` on that database. A later-pending earlier number is still a startup failure no operator can attribute to the
change, which is why `C-8` must not be numbered `V8` to look sequential.

**The failure that taught this, recorded because it is a build-trap and not a migration bug.** After renaming the file,
`mvn -DskipTests package` (no `clean`) rebuilt the jar with **both** `V6__require_the_tenant_key.sql` and the old
`V7__require_the_tenant_key.sql` inside `target/classes` — Maven copies resources and does not delete the ones that
stopped existing upstream. That jar applied V6, then the ghost, and died on
`42710: trigger "trg_parent_student_tenant_key" already exists`. Flyway's checksum validation did not and could not
catch it: the ghost was byte-identical to a file that really existed once. `mvn clean package` produces the correct
artifact (`unzip -l` lists V1…V6 and nothing else), CI is unaffected because it runs `mvn -B verify` on a fresh
checkout, and the residue is numbered **OPS-18** in `TECHNICAL_DEBT.md`.

### 17.4 Live verification

`scripts/verify-c7-tenant-key.sh`, 389 lines, seven sections, **66 checks / 0 failures / 0 skipped** against a running
application on a disposable database built by the migration chain (`gms_c7_r1`, V1→V6 applied by Flyway,
`ddl-auto=validate`):

| § | What it proves | checks |
|---|---|---|
| 1 | Platform login, two schools created through the API, both administrator tokens issued | 5 |
| 2 | School A builds a whole term through its own endpoints — classroom, section, subject, employee, student, exam with a subject, exam registration, result, fee, student-fee, payment, assignment, timetable with a slot, attendance, admission applied and approved | 28 |
| 3 | Each of the eight owned tables: `written by school A, and by nobody else` — a row count for the tenant **and** a count of rows outside it, plus the exam-subject and parent-link rows the service wrote | 11 |
| 4 | 35 tenant tables at the database, `rows with a NULL tenant, in any tenant table, platform-wide: 0`, `tenant columns still nullable: 2` | 3 |
| 5 | Ten `UPDATE … SET school_id = NULL` probes in rolled-back transactions, each preceded by `there is an API-written <t> row to probe`; then the trigger re-deriving a hand-written NULL, and refusing a link that names another school | 11 |
| 6 | A write that straddles two schools refused with **403** and the right message; no timetable naming another school's classroom; no assignment naming another school's employee; the refused write left no row behind | 7 |
| 7 | The generated SQL — `Hibernate: insert into attendances (…, school_id, …)` — captured from the running application | 1 |

`C-1`'s, `C-2`'s and `C-3`'s scripts re-run against this build: **29/29, 32/32, 25/25**. `mvn -B verify`: **89 tests,
0 failures** (87 before the card, +1 `EntityTenantColumnArchTest`, +1 `GlobalExceptionHandlerTest` case for the 403).
A fresh database migrates V1→V6 and boots: `Successfully applied 6 migrations`, `now at version v6`.

### 17.5 The deliberate-failure controls

Two, each run red and then restored.

1. **The static half.** `Result.java`'s join column renamed from `school_id` to `org_id` — the faithful edit is a
   rename, not a deletion of the field, because deleting `school` stops compilation (`cannot find symbol: setSchool`)
   and a build error is not a test failure. `EntityTenantColumnArchTest` goes red naming exactly one offence: `Result
   maps table results, which carries school_id, without mapping the column`. Restored, green.
2. **The write half.** The same rename on `Attendance.java`, against a running server. `verify-c7-tenant-key.sh` reports
   `FAIL attendances: none -- the endpoint never wrote one`, which is §5's non-vacuity guard catching the missing write
   rather than a constraint failing — the check that fails is the one that refuses to call an empty table a pass.
   Restored, green, and both files `touch`ed afterwards: an older mtime makes Maven silently skip recompiling a
   restored source, which would have made the "green" half of this control meaningless.

Two vacuous checks were found and fixed in the script itself while writing it: §3's "no row is in the wrong school" is
also true of a table the application never wrote to, so it now asserts a row count first; and three §5 probes reported
"accepted" for tables with zero rows, because an `UPDATE` that matches nothing raises nothing, so each probe now
refuses to run unless a row exists.

### 17.6 What `C-7` does not close

*Recorded at this card's close, and two of the four items have since closed — in the corrected wording, because the
counts below counted call sites and were then read as leaks.*

`C-4` — **closed after this card.** 155 `findById` and 6 `getById` call sites resolved a row by raw id before anybody
decided whether the caller may name it, and `RepositoryTenantScopeArchTest` could not see any of them because they are
inherited rather than declared. That was a count of *call sites*: most of them already refused a foreign row, because
a hand-written ownership comparison sat immediately below (`grep` for `.equals(TenantContext.schoolId())` under
`service/impl/`: 107 lines at the commit `C-4` starts from, 82 after it deleted the redundant copies; the same `grep`
on this branch today returns **23 lines, 21 of them distinct comparisons** — 107 and 82 are the historical pair, 23 is
the current measurement). The measured defect was therefore **duplication of one predicate plus two copies that
dereferenced an unloaded Hibernate proxy** — `StudentFeeServiceImpl.getStudentFeeById` and
`FeePaymentServiceImpl.getFeePaymentById` walked `getStudent().getSchoolId()` across `FetchType.LAZY` associations
(`StudentFee.java:27-30`, `FeePayment.java:27-30`,
`Student.java:77`) after `open-in-view=false` (`application.properties:36`) had closed the session, and answered HTTP
500 to the row's rightful owner. `repository/TenantScopedRepository.java:56-61` is the closure: one `findById`
override, `where e.id = :id and e.school.id = :schoolId`, extended by 33 of the 36 repository interfaces — the three
that are not are `ParentRepository`, `SchoolRepository` and `DashboardStatisticsRepository`, each exempt with a reason
(§18.1) — guarded by `repository/TenantScopedReadArchTest`; inherited `findAll`/`findAllById`/`delete` stay open. Full
write-up: [SECURITY_AUDIT §5.5](SECURITY_AUDIT.md), and the card evidence in §18.1.

`C-5` — **closed after this card.** 23 mutating handlers reached a service with no `@PreAuthorize`; 234 of the 236
handlers now carry a guard of their own and `security/DenyByDefaultAuthorizationArchTest` fails the build on the next
one that does not, with `AuthController.login` and `HealthController.ping` as the only two named exemptions. What is
still true of `C-7`'s own scope: a guard is a gate, not a tenant predicate. Card evidence, including the control that
runs it red, in §18.2.

`C-8` — the recipient/announcement/message join tables, which every draft of `ARCHITECTURE.md §5` reserved as `V7`;
that number is now taken — `V7__record_who_wrote_the_pupil.sql` exists and is `C-8`'s authorship migration (§18.5) — so
the join-table slot has moved to `V8`, and with the delay goes the chance to retire this card's
`trg_parent_student_tenant_key` trigger by giving `parent_student` an entity. `FIN-04` — `invoices` and `receipts` map and
write `school_id` but never assign `student`, so their `V5` keys are vacuous for a reason that has nothing to do with
tenancy. `DB-08` — `uq_timetable_classroom_year` is platform-wide and is evaluated before `fk_timetable_classroom_same_school`,
which is why §6 needs a distinct academic year to reach the constraint it is testing. `C-9` and Stage 6 — none of this
is deployed, and `master` still has no migrations at all.

### 17.7 State left on this machine

* Container `gms-postgres` is running. It holds `gms` (pre-existing, **untouched by every card in Stage 3**), the
  databases §15–§16 list, and `gms_c7_r1`. All of the latter are disposable; `docker compose down -v` removes the
  container with all of them. The retired `gms-v1-neon` host was not contacted, read, or named in any command.
* The backend on `:8080` was **stopped** at the end of this card (`lsof -ti tcp:8080` is empty). To reproduce §17.4:
  start it against `gms_c7_r1` with `APP_SUPERADMIN_*`, `SECURITY_JWT_SECRET` and `JPA_SHOW_SQL=true` in the
  environment, then run the script with `GMS_SUPERADMIN_USERNAME`, `GMS_SUPERADMIN_PASSWORD`, `GMS_VERIFY_DB=gms_c7_r1`,
  `GMS_PG_PASSWORD_ENV` naming an exported variable that holds the local database password, and
  `GMS_SQL_LOG=<the boot log>`. Everything else has a default — including `GMS_CROSS_TENANT_ACADEMIC_YEAR=2027-2028`,
  which §6 needs in order to reach the tenant key instead of colliding on `uq_timetable_classroom_year` (`DB-08`).
* Outside the repository: `/tmp/gms-c7-run.txt` (the 66-check transcript), `/tmp/gms-c7-boot.log` (boot + generated
  SQL), `/tmp/gms-c7-final-verify.txt` (the 89-test run) and `/tmp/gms-c7-secret.env` (mode 600 — disposable platform
  credentials and the local database password, which lives in the gitignored `.env` and appears in no file here). The
  two control backups under `/tmp` were deleted once both files were restored.
* No credential appears in this document, in `V6__require_the_tenant_key.sql`, in `scripts/verify-c7-tenant-key.sh`, or
  anywhere else in the repository.


## 18. Stage 3 `C-4`, `C-5`, `C-6` and `C-8` — the load, the gate, the vocabulary and the author (2026-11-02, `feat/stage2-flyway-baseline`)

Four cards closed on this branch after §17. [ARCHITECTURE.md §4](../ARCHITECTURE.md) and
[INFRASTRUCTURE_DECISIONS.md](../infrastructure/INFRASTRUCTURE_DECISIONS.md) ADR-020 both cite this section as where
their card evidence lives, and the citations were written before the section existed — so until now a reader following
either link arrived at the end of §17.7 and learned nothing about `C-4`, `C-5`, `C-6` or `C-8`. §17.6 recorded only what
each card left open; the measurements, the instruments and the controls are here, one subsection per card, in §16/§17's
shape: the number, the mechanism that produced it, and what remains owed.

### 18.1 `C-4` — ownership at the point of load

`repository/TenantScopedRepository.java` is a `@NoRepositoryBean` interface `TenantScopedRepository<T extends
TenantOwned>`, and it is the base **33 of the 36 repository interfaces** in `com.gms.repository` extend. It overrides
`findById` with one query — `where e.id = :id and e.school.id = :schoolId` — so a read that names another school's row
matches nothing and the endpoint answers **404**. That is the whole card: the ownership predicate moved from a
hand-written comparison below the load to the load itself, so a service method cannot forget it by forgetting to write
it.

`getById` and `getReferenceById` are **refused, not scoped**, and `repository/TenantScopedReadArchTest` is the
instrument that refuses them. Scoping them would be a lie: both hand back a proxy without looking at the tenant, so the
row's identity is never established against the caller — the tenant check would run against a value nobody had resolved
yet. A method that cannot be made honest is taken away rather than wrapped.

`EntityLazyAccessorArchTest` polices the other half of the same bug, and it prints the number this card started with:

```
C-4: 41 derived accessors read a lazy association, 17 of them ask whether it is loaded first
```

With `open-in-view=false` (`application.properties:36`) there is no session when a response is serialised, so any getter
other than `getId()` on a lazy to-one association is a `LazyInitializationException` and therefore an HTTP **500** —
delivered to the row's rightful owner, which is why §17.6 counted it as a defect rather than as noise. The rule the test
holds is that a derived accessor must ask `Hibernate.isInitialized` first and answer with the id, or the null, or a
declared absence, rather than trigger the load. 41 is the exposure the test counts; **17 is how many of them ask**. What
the remaining 24 do about it is not measured by this number, and `C-4` does not claim that they are safe.

**The three interfaces that do not extend the base are named, with a reason** — the same convention §16.1 and §17.1 use,
where an exemption without a reason is indistinguishable from a gap. `SchoolRepository`, because its table *is* the
tenant; `ParentRepository` and `DashboardStatisticsRepository`, because the rows they read have no single owner — a
parent's children can sit in two schools, and a dashboard aggregate is assembled across rows rather than loaded from
one.

*Live.* `scripts/verify-c4-owned-reads.sh`: **118 passed / 0 failed / 17 skipped**. The 17 skips are stated rather than
buried: they are by-id routes whose own `@PreAuthorize` does not admit `SUPERADMIN`, the only identity the script holds
a token for, so the script cannot ask the question at all. **A skip is never counted as a pass** — the 118 are the
routes that were asked, and the 17 are still unasked.

### 18.2 `C-5` — deny by default

The rule is that a handler which declares no guard is a refusal, and `security/DenyByDefaultAuthorizationArchTest`
(7 tests) is the instrument that makes "forgot to guard" a build failure rather than a review finding. Its own
measurement, printed on every build:

```
C-5: 236 handlers, 234 guarded, 2 deliberately public
```

236 handler mappings, **234** carrying a guard, and **2** deliberately public — `AuthController.login` and
`HealthController.ping`, both named rather than counted as a tolerance.

The count is read off **compiled bytecode**, not source, for two measured reasons. The mapping annotation and
`@PreAuthorize` appear on a handler in either order in the source, so a scan anchored on the declaration line can walk
past a guard that is genuinely there. And 2 of the 236 textual `@PreAuthorize` occurrences in `controller/*.java` are
inside javadoc, not on a method — `SubjectController.java:21` and `ClassroomController.java:21` — so a text scan
over-counts guards by two. A test that reads the same bytes Spring does cannot drift from the application it polices.

The other six tests enforce the shape of the list rather than just its size: a **floor of 200** handlers, so a scanner
that stopped matching shrinks the set instead of silently turning red into green; `guarded + deliberatelyPublic ==
handlers`, so nothing can be neither; **no class-level guard**, because one annotation on a class is a guard the next
method inherits without anybody choosing it; and every authority named by any guard must be **a role the application can
mint** — the clause that made `C-6` visible (see §18.3).

The URL layer had its own defect, and it was the opposite of a leak: first-match-wins in `SecurityConfig` had the admin
matchers ordered ahead of the self-service ones, so a `STUDENT` calling `GET /timetables/student/me` was refused by the
perimeter even though the handler beneath it grants exactly that role. The self-service matchers now precede the admin
ones. A perimeter that over-refuses is still a broken authorisation surface, because it trains the next author to
broaden a matcher rather than to read the ordering.

**Control run, and the observed red.** `@PreAuthorize` was deleted from `AnnouncementController.createAnnouncement` and
the test printed `C-5: 236 handlers, 233 guarded, 2 deliberately public` and **failed naming that handler**. Restored,
it returns `234 guarded` and green — with both files `touch`ed afterwards, for the reason §17.5 records: an older mtime
makes Maven skip the recompile, and a green on stale bytecode would have made the restored half of this control
meaningless.

*Not closed by `C-5`.* A guard is a gate, not a tenant predicate — the sentence §17.6 already carried, and still true.
And the accountants' writes: `POST`, `PUT` and `DELETE` on the finance controllers are all `hasAuthority('ADMIN')`, so
the role this branch minted can read the money and not touch it (§18.3).

### 18.3 `C-6` — a role the application can mint

**39 guards named `ACCOUNTANT` while `RoleEnum` declared five constants and none of them was `ACCOUNTANT`.** Read
carefully, because the direction of the defect is not the obvious one: that is not a hole but a wall. A guard naming a
role that cannot exist can never be satisfied, so the money module was reachable only by `ADMIN`/`SUPERADMIN`, and the
39 annotations describing an accountant were describing nobody.

`RoleEnum` now declares **6** constants with `ACCOUNTANT` at `RoleEnum.java:11`, and **no migration was needed** —
measured, not assumed: `user_roles.role` is a plain `varchar(30)` with no Postgres enum type and no `CHECK` constraint,
so the vocabulary was never enforced at the database. It is enforced in code and policed by `C-5`'s scan, which is the
stronger of the two arrangements available here because the scan checks the guards against the enum rather than the rows
against a type.

`/auth/self-register` was minting the literal string `"EMPLOYEE"` — a role no guard in the application names, which is
how a self-registration could produce an identity with an authority and no way to use it. It now routes through
`UserService.authoriseRoles`, so every token the application issues is issued by the one path that validates against
`RoleEnum`. The `roles` claim is written **sorted and null-filtered** at signing, and that is what licenses the rule
stated for clients: no caller may read position zero as a precedence, because position zero is now whichever name sorts
first.

The frontend had to move the same distance, because a role the backend can mint and the UI cannot spell is a half
card. `ACCOUNTANT` is in the role vocabulary, the labels, the sidebar sections, the precedence order and the landing
routes, so an accountant signs in and lands on `/admin/fees`. Write controls are **hidden for a role whose writes the
server refuses** — which is the honest rendering of §18.2's last line rather than a workaround for it. Three other
frontend defects were found while wiring it and fixed there: `ProtectedRoute`'s deny path now renders a real
`/unauthorized` screen instead of a blank one; the catch-all route moved *inside* the layout so that it can actually
win the match; and a token carrying two roles composes both sections rather than taking the first.

*Deliberately not done, and recorded so it is not mistaken for an oversight.* `ACCOUNTANT` sits between `ADMIN` and
`TEACHER` in the precedence order — a choice, not a gap. The `DASHBOARD` matcher still admits only `ADMIN`/`SUPERADMIN`.
`apiSlice.js` has no endpoints for the finance reads beyond fees, so the accountant's landing page is narrower than the
role's read authority.

### 18.4 `C-7` — the tenant as a key

Recorded in full in §17 and not repeated here: the rule and the instrument are §17.1, the code is §17.2, the migration
and the reason it is `V6` are §17.3, the 66-check live run is §17.4, and the two deliberate-failure controls are §17.5.
This subsection exists only so §18 reads as the whole of Stage 3's card evidence rather than a part of it. What `C-7`
left open is in §17.6, and `C-4` and `C-5` have closed two of those items since — their evidence is §18.1 and §18.2.

### 18.5 `C-8` — the row records who wrote it

`model/entity/AuthorshipEntity.java` is a `@MappedSuperclass` that declares `createdBy` and `updatedBy` once.
`BaseAuditEntity` now **extends** it rather than re-declaring them, which is how the count reaches 33: **28 entities
audit through `BaseAuditEntity`, and 5 — `School`, `Student`, `Classroom`, `Subject`, `StudentAdmission` — extend
`AuthorshipEntity` directly**, because they name their timestamp columns `created_at`/`updated_at` and inheriting the
base would have given each of them two timestamp pairs on the same table. 28 + 5 = **33 of the 36 entity classes
listener-stamped**. The three that are not: `Employee` and `User` keep a hand-assigned `created_by`, because for those
two tables it is a business fact rather than an audit trail, and `AcademicYear` has none.

`V7__record_who_wrote_the_pupil.sql` adds the two columns and **8** `REFERENCES public.users(id)` foreign keys to
`students`, `classrooms`, `subjects` and `student_admissions` — two keys per table, one per actor column. They are
`NO ACTION`, matching `V1`'s existing **62** audit keys across **31** tables, and deliberately **not**
`ON DELETE SET NULL`: set-null is the option that looks safe and is not, because it lets a deletion quietly erase the
trail rather than telling the deleter that a trail depends on the row. **35 of the 38 tables now carry an actor column**;
the exceptions are `academic_years`, `parent_student` and `user_roles`. **Nothing is backfilled** — an author attributed
by inference would be a fabricated fact in a table whose entire purpose is to hold one.

**The bug this card found, which is the reason it is recorded rather than summarized.** `AuditingEntityListener` fires
*during* the flush, so the auditor may not issue SQL — and the first version of it did. It called
`userRepository.findByUsername(...)`, whose implicit auto-flush re-entered the flush already in progress, and every
`PUT`/`PATCH` on a school, a pupil, a classroom and an admission answered **500** with

```
org.hibernate.AssertionFailure: collection was processed twice by flush()
```

under a `StackOverflowError` — while `mvn -o clean package` stayed **green**. The suite ran, the jar built, and none of
it could see a failure that only existed inside a live flush.

The affected entities own **no collection of their own**, which is what made the failure look impossible when read from
the stack trace. The re-entered flush is the *session's*, not the entity's: any session that has already touched one of
the three collection owners — `Parent.students`, `Exam.subjects`, `Timetable.slots`, the last of them
`FetchType.EAGER` — is exposed.

The fix is at the mechanism, not the call site: the JWT now carries a **`uid`** claim (`users.id`), and the auditor
answers with `getReferenceById(uid)`, which returns a managed proxy and **makes no statement at all**. No query, so no
auto-flush, so no re-entry.

`uid` also settles **BKL-08**, which was open as a modelling question and closed as an authorship one: the existing
`empId` claim is an *employee* id for staff but the *user* id for the platform owner, so attributing a write through it
could name a different tenant's employee as the author of this tenant's row. Writing the user id directly removes the
ambiguity instead of routing around it.

*Live.* `scripts/verify-mvp-e2e.sh`, the `C-8` feature block **F11**: **20 checks PROVED**.

*The control was run, and it discriminated.* Deleting `@CreatedBy` and `@LastModifiedBy` from `AuthorshipEntity` — the
columns still mapped, the eight keys still present, nothing else changed — takes the MVP suite from 294 passed / 0
failed to **291 passed / 3 failed / 0 skipped** and the feature register from `11 of 11` to `10 of 11`. The three red
checks are `F11 a pupil row records who wrote it`, `F11 an admission records who applied` and `F11 a classroom records
who created it`, each printing `(expected 2, got )` — the blank is the point. The other 17 F11 checks stay green,
including *"an account created through the API records its author"*, because `User.createdBy` is assigned by the service
rather than by the listener, so the control fails on the mechanism and not merely on the column. Restoring the two
annotations and re-running against a freshly created database returns **294/0/0 and 11 of 11**. `C-8` therefore matches
§17.5 and §18.2: three cards in this stage have now been proved capable of going red.

*Still open for `C-8`.* Two things this card does not claim to have fixed: **hard deletes destroy the trail** — a row
and its authorship disappear together, and no constraint exists to keep them apart — and **no API surfaces the trail to
a school**, so the columns are evidence available to the database and not yet to the user.

### 18.6 The whole-build measurement

Taken on the branch after all four cards, against a fresh disposable database:

| Measurement | Result |
|---|---|
| `mvn -B verify` (JDK 21) | **106** unit + architecture tests, **0 failures** |
| Fresh database | applies `V1`→`V7` and boots under `ddl-auto=validate` |
| `scripts/verify-mvp-e2e.sh` | **294 passed / 0 failed / 0 skipped**, `FEATURES PROVED: 11 of 11`, on two tenants |
| Per-card regressions | `c1` 29/0 · `c2` 32/0/0 · `c3` 24/0/1 · `c4` 118/0/17 · `c7` 65/0/1 |

`11 of 11` is the line worth reading twice: it is the first time on this branch that every frozen-MVP feature has been
*demonstrated* by a running application rather than asserted by a test that could not reach one.

Two of the per-card numbers disagree with the card's own section, and the disagreement has a cause that was measured
rather than guessed: §16.4 measured `verify-c3-scoped-reads.sh` at 25/0/0 and it now reports 24/0/1; §17.4 measured
`verify-c7-tenant-key.sh` at 66/0/0 and it now reports 65/0/1. The skip in each case is the same check, and each
transcript names it — `SKIP generated-SQL check (GMS_SQL_LOG is not set (start the backend with JPA_SHOW_SQL=true))`
(`c3`) and `SKIP generated INSERT check (GMS_SQL_LOG is not set …)` (`c7`). This run's harness booted the jar without
`JPA_SHOW_SQL=true` and did not export `GMS_SQL_LOG`, so the two scripts could not read the SQL log they assert
against. **Nothing regressed and no check lost its power: the gap is in how this section's run was configured, not in
the build**, and re-running either card with the log exported is the way to close it. What this section claims for both
is `0 failed`; the two skips are the harness's own omission, recorded rather than presented as passes.

### 18.7 State left on this machine

* Container `gms-postgres` is running on `127.0.0.1:5433`. Alongside the pre-existing `gms` database — untouched by
  every card in Stage 3 — it now holds **34** disposable databases created by these runs: `gms_mvp_1`, `gms_post_c1`
  … `gms_post_c7`, `gms_recheck_1`, `gms_final_*`, `gms_c*_r*`, the two `gms_schema_probe*` pair, `gms_perf_seed`,
  `gms_v3_negative` and the rest. `docker compose down -v` removes the container and all of them with it. The retired
  `gms-v1-neon` host was not contacted, read, or named in any command.
* The backend on `:8080` is **stopped** (`lsof -ti tcp:8080` is empty). To reproduce §18.6: source `.env` and the
  disposable platform credentials, boot the jar with `--spring.profiles.active=dev` against a freshly created database
  (the `DB_URL` override points it at `127.0.0.1:5433`), then run `scripts/verify-mvp-e2e.sh` with
  `GMS_VERIFY_DB` naming that database and `GMS_REQUIRE_DB=1` so the script refuses to pretend rather than skip. The
  per-card scripts take the same `GMS_VERIFY_DB`; add `JPA_SHOW_SQL=true` and `GMS_SQL_LOG=<the boot log>` if the
  generated-SQL checks in `c3` and `c7` are wanted rather than skipped, which is the one thing §18.6's run left out.
* `V7__record_who_wrote_the_pupil.sql` was edited after the runs above — four comment lines, the table and entity counts
  in its own header. No DDL changed, and §18.6's numbers were re-measured against the rebuilt jar on a fresh database
  (7 `Migrating schema` lines, V1→V7 applied, 294/0/0 again). **The edit does change Flyway's checksum for `V7`**, so
  any database that already applied the old text will refuse a subsequent `migrate` with a checksum mismatch until it
  is repaired or recreated. The resolution is the one this project already mandates — a database Flyway can recreate —
  and every figure in this section was produced that way.
* Outside the repository: `/tmp/gms-mvp-harness4.txt` (the run summary), `/tmp/gms-mvp-e2e.txt` (the 294-check
  transcript and the feature register), `/tmp/gms-post-c*.txt` (the five per-card transcripts, including the two
  `SKIP … GMS_SQL_LOG is not set` lines §18.6 quotes), `/tmp/gms-mvp-boot-*.log`, `/tmp/gms-recheck-e2e.txt` (the
  re-run after the `V7` comment edit), `/tmp/gms-mvp-run.sh` and `/tmp/gms-recheck.sh` (scratch harnesses, not project
  scripts), and `/tmp/gms-c7-secret.env` (mode 600 — disposable platform credentials and the local database password,
  which lives in the gitignored `.env` and appears in no file in this repository).
* A second worktree at `/private/tmp/gms-pre-c4` is still checked out at `7c7789d` with local modifications. It is the
  pre-`C-4` tree the 107-line tenant-comparison count was taken against, and it has been left in place rather than
  pruned because its diff is the only remaining artifact of that measurement; `git worktree list` shows it and it
  appears in no build.
* No credential appears in this document, in `V7__record_who_wrote_the_pupil.sql`, in any `scripts/verify-*.sh`, or
  anywhere else in the repository.
