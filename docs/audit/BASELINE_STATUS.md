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
| `docker compose up` works out of the box | ❌ exit 1 without an undocumented `.env`; and even with one the backend healthcheck can never pass (R3 + `wget --spider /actuator/health || exit 1`) | I1, I3, R3 |
| Authentication works | ✅ for an account whose password you know | R4 |
| Authorization works | ❌ forged token accepted; inherited CRUD routes unguarded | R5, §4 |
| Tenant isolation works | ❌ not enforced on inherited CRUD paths; 12 tables have no tenant column at all | §5, §6 |
| Database schema is reproducible from the repo | ❌ **0 migration files**, no `flyway-core` dependency, `spring.flyway.*` keys inert | §6 |
| Documentation matches the running system | ❌ see §7 |

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
measured twice on different machines. Still unverified: the prod profile has never booted, `deploy-backend.yml` has
never run (it needs the owner's `EC2_SSH_*` and `EC2_SSH_KNOWN_HOSTS` secrets), and no check is marked required, so CI
reports rather than enforces. The honest reading of this branch remains "the code awaiting deployment is measurably
safer than the code deployed"; §27 forbids more than that.

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
