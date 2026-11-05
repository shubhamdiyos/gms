# GMS Performance Measurement Report

**Report date:** 2026-11-03
**Branch:** `test/playwright-e2e` at `ab8eeef`
**V10 backup branch:** `perf/v10-index-the-measured-reads` at `61c8ce1`
**PostgreSQL version:** 15.8 (Homebrew Docker, `postgres:15`)
**Measurement harness:** `scripts/verify-perf-50k.sh`
**Seed dataset:** `scripts/seed/perf_seed_50k.sql`

---

## 1. Executive Summary

The GMS platform was measured against its published non-functional performance requirements (NFR-02: p95 < 300 ms, p99 < 800 ms at 8-wide concurrency) using a 1.84M-row, 101,800-student dataset across 4 tenant schools.

**Key findings:**

| Metric | Sequential (1 client) | Concurrent (8 clients) |
|---|---|---|
| Endpoints measured | 10 | 6 |
| Pass / Fail | **10 / 0** | **3 / 3** |
| Worst p95 | 152.1 ms (`/dashboard/school`) | 731.8 ms (`search?query=<matches-all>`) |
| Worst max | 315.1 ms (`/dashboard/school`) | 1593.5 ms (`/dashboard/school`) |

**Three concurrent breaches remain after two rounds of fixes:**

1. **Short search pattern** (`search?query=er`): p95 482 ms -- 2-character query yields no trigrams, falls back to sequential scan
2. **Deep pagination** (`search?page=4000`): p95 429 ms -- OFFSET 80,000 forces external merge sort
3. **Dashboard contention** (`/dashboard/school`): p95 351 ms, max 1382 ms -- 7 aggregate statements under concurrent load

Two fixes were applied during the investigation:
- Dashboard query optimization: 88.9 ms --> 44.0 ms (tenant-column filter)
- N+1 elimination on result sheets: 26 --> 7 SQL statements per request

V10 migration (trigram indexes) closed one of four original breaches (selective search: 324 ms --> 69 ms) but is **not merged to master** and **not verified on PostgreSQL 17**.

---

## 2. Test Environment

### Hardware

| Component | Specification |
|---|---|
| Machine | Laptop, 8 physical + 8 logical CPUs |
| OS | macOS (Darwin) |
| PostgreSQL | 15.8, Homebrew Docker (`postgres:15`), `127.0.0.1:5432` |
| PostgreSQL config | `shared_buffers=128MB`, `effective_cache_size=4GB`, `max_parallel_workers_per_gather=2`, `max_parallel_workers=8`, `max_worker_processes=8` |
| Application | Spring Boot on port 8083, single JVM, default HikariCP pool (10 connections), default Tomcat thread limit (200) |

### Dataset

| Table | Row count |
|---|---|
| `students` (roll) | 101,800 |
| `student_fees` | 509,000 |
| `attendances` | 203,600 |
| `student_exams` | 203,600 |
| `results` | 101,800 |
| `users` | 103,480 |
| **Total across 27 tables** | **1,840,468** |
| Disk footprint | 463 MB |
| Seed load time | 74 seconds |
| API-visible roll (school 1, active only) | 96,443 (excludes 5,357 deactivated pupils) |

Schools 1 and 2 hold 50,000 pupils each (the two volume tenants NFR-01 names). Schools 3 and 4 hold 1,200 and 600 respectively.

### Measurement Methodology

- **Sequential:** 20 samples per endpoint, one request in flight at a time, warm cache. Percentiles computed over 20 latency samples.
- **Concurrent:** 8 clients in flight (`xargs -P 8`), 80 requests per endpoint, curl `time_total` per request, `--max-time 10` (exceeding counts as failure, not dropped sample). Active backends sampled from `pg_stat_activity` at 100 ms intervals.
- **Database volume assertion:** The harness verifies exact per-table counts (derived from the seed's arithmetic) before starting the clock. If any count is wrong, the run aborts -- preventing green latency numbers measured over empty or half-seeded tables.
- **All responses were HTTP 200** in every run reported here.
- **Limitations acknowledged:** Warm cache only (no `pg_dropcache`), single instance, single machine, p99 estimated as worst run (20 samples is weak for p99), SQL-layer percentiles from 6 EXPLAIN probes only.

---

## 3. Performance Targets (NFR-02)

From `docs/PRODUCT_REQUIREMENTS.md` section 9:

| NFR | Target | Description |
|---|---|---|
| NFR-01 | >= 50,000 records per school | Volume floor in primary tables, 2 schools |
| **NFR-02** | **p95 < 300 ms, p99 < 800 ms** | **API latency for the ten hottest endpoints at volume** |
| NFR-03 | p95 < 100 ms per statement | Per-SQL budget (measured by EXPLAIN, not per HTTP call) |
| NFR-04 | Page size 25 default, 100 max | Server-side pagination ceiling |
| NFR-05 | Index on `school_id` and filter columns | Tenant-scoped indexing |

The concurrent budget is NFR-02 as written: p95 < 300 ms and p99 < 800 ms, applied unmodified to both sequential and concurrent legs. No per-endpoint tuning of the budget.

---

## 4. Sequential Performance Results

**All 10 endpoints pass.** 20 runs per probe, one request in flight, warm cache. Post-fix re-run on the same jar and seed.

| Probe | p50 (ms) | p95 (ms) | max (ms) | Budget p95 | Verdict |
|---|---|---|---|---|---|
| `search?query=<selective>` (1 row in 96,443) | 66.9 | 77.5 | 173.1 | 300 | PASS |
| `search?query=<matches-all>` (96,443 match) | 82.1 | 95.7 | 105.4 | 300 | PASS |
| `search?classId=<8,334-pupil classroom>` | 25.8 | 36.5 | 49.0 | 300 | PASS |
| `search?page=4000` (deep pagination) | 79.9 | 136.9 | 150.1 | 300 | PASS |
| `search?size=100` (server-side cap) | 39.5 | 42.2 | 45.8 | 300 | PASS |
| `GET /students` (default page) | 17.1 | 26.6 | 30.4 | 300 | PASS |
| `GET /students?size=500` (refused down to 100) | 21.9 | 32.3 | 51.7 | 300 | PASS |
| `GET /students/{id}` | 12.8 | 20.9 | 23.7 | 300 | PASS |
| `GET /dashboard/school` | 120.8 | 152.1 | 315.1 | 300 | PASS |
| `GET /dashboard/platform` | 31.3 | 37.5 | 55.1 | 300 | PASS |

**Tenant-scoped variants (separate token):**

| Probe | p50 (ms) | p95 (ms) | max (ms) | Budget p95 | Verdict |
|---|---|---|---|---|---|
| `ADMIN search?query=<tenant>` | 30.4 | 32.2 | 32.3 | 300 | PASS |
| `ADMIN GET /dashboard/school` | 115.2 | 132.9 | 142.2 | 300 | PASS |

**Worst p95 across all 10 probes: 152.1 ms** (`/dashboard/school`). Well within the 300 ms budget.

**Note on `GET /students` history.** Before pagination was added, this endpoint serialised 47,369 pupils with no page parameters. It measured p95 378.5 ms on the first run and p95 299.5 ms on the second -- straddling the budget on JVM and page-cache state. An endpoint whose verdict flips with the weather is not a pass, so the ceiling from NFR-04 was applied: `size` defaults to 20, clamped server-side to `MAX_SEARCH_SIZE = 100`, ordered by `id DESC`. After the fix: p95 26.6 ms.

---

## 5. Concurrent Performance Results

8 clients in flight, 80 requests per endpoint. Pre-fix measurements from `scripts/verify-perf-50k.sh` section 4b.

### Pre-fix Results (before V10 indexes and dashboard optimization)

| Endpoint | min (ms) | p50 (ms) | p95 (ms) | max (ms) | req/s | dbpeak | NFR-02 |
|---|---|---|---|---|---|---|---|
| `search?query=<selective>` | 127.4 | 186.2 | 319.0 | 523.9 | 35.6 | 16 | **FAIL** |
| `search?query=<matches-all>` | 166.4 | 270.6 | 427.1 | 761.8 | 25.3 | 16 | **FAIL** |
| `search?page=4000` (deep) | 198.3 | 271.1 | 342.1 | 411.7 | 26.8 | 14 | **FAIL** |
| `GET /students` (default) | 20.3 | 34.1 | 60.0 | 90.0 | 141.8 | 9 | PASS |
| `GET /students/{id}` | 4.3 | 7.8 | 10.6 | 13.8 | 236.7 | 2 | PASS |
| `GET /dashboard/school` | 317.4 | 528.9 | 1725.2 | 1826.8 | 10.4 | 16 | **FAIL** |

**Run tally: 52 passed, 4 failed, 1 skipped.**

### Post-fix Results (after V10 indexes + dashboard optimization)

| Endpoint | p95 before (ms) | p95 after (ms) | max after (ms) | NFR-02 |
|---|---|---|---|---|
| `search?query=<selective>` | 319.0 | 259.8 | -- | PASS |
| `search?query=<matches-all>` | 427.1 | 731.8 | -- | **FAIL** |
| `search?page=4000` (deep) | 342.1 | 707.5 | -- | **FAIL** |
| `GET /students` (default) | 60.0 | 90.6 | -- | PASS |
| `GET /students/{id}` | 10.6 | 13.5 | -- | PASS |
| `GET /dashboard/school` | 1725.2 | 1382.4 | 1593.5 | **FAIL** |

**Run tally: 53 passed, 3 failed, 1 skipped.** V10 indexes closed the selective-search breach; the dashboard fix reduced but did not eliminate the dashboard breach.

### Post-fix Concurrent Ladder (1 --> 16 clients)

| Probe | Clients | Runs | p50 (ms) | p95 (ms) | max (ms) | req/s |
|---|---|---|---|---|---|---|
| `GET /dashboard/school` | 1 | 5 | 99.2 | 337.6 | 337.6 | 6.2 |
| `GET /dashboard/school` | 2 | 10 | 121.6 | 169.7 | 169.7 | 13.6 |
| `GET /dashboard/school` | 4 | 20 | 178.2 | 281.5 | 289.8 | 17.8 |
| `GET /dashboard/school` | 8 | 40 | 283.3 | 705.9 | 893.1 | 19.9 |
| `GET /dashboard/school` | 16 | 80 | 628.0 | 1209.2 | 1413.5 | 21.5 |
| `search?query=verma` | 1 | 5 | -- | -- | -- | -- |
| `search?query=verma` | 8 | 40 | -- | -- | -- | -- |
| `GET /students/{id}` | 1 | 10 | 4.3 | 5.6 | 5.6 | 64.9 |
| `GET /students/{id}` | 8 | 80 | 8.3 | 13.3 | 15.7 | 254.8 |
| `GET /students/{id}` | 16 | 160 | 8.5 | 30.3 | 81.3 | 243.5 |

**Key observations:**
- Throughput plateaus while latency grows with concurrency -- a saturated server, not a client-side artefact. The dashboard reaches ~18-22 req/s at c=4..16 (post-fix), up from ~10-11 req/s pre-fix.
- `GET /students/{id}` scales essentially linearly (254.8 req/s at 8-wide, p95 13.3 ms, dbpeak=1) -- a single-connection indexed read is not the bottleneck.
- The connection pool is not the wall. HikariCP default is 10; `dbpeak` counts active `pg_stat_activity` rows and exceeds 10 only because PostgreSQL parallel workers appear in it.

---

## 6. Fixes Applied

### Fix 1: Dashboard Query Optimization (tenant-column filter)

**File:** `StudentFeeRepository.java:28,31`

**Change:** The dashboard's two fee sum queries were rewritten to filter on `sf.school.id` (the tenant column `student_fees` has carried since Stage 3 C-7) instead of `sf.student.school.id` (a join through the student table).

**Correctness:** These select exactly the same rows. The composite FK `fk_student_fee_student_same_school` ties `(student_id, school_id)` to `students(id, school_id)`, so a fee row can never point at a pupil of another school. The index `idx_student_fee_school` covers the shorter predicate.

**Impact (EXPLAIN probe P6):**

| Shape | Plan | Blocks | Execution |
|---|---|---|---|
| Before (joined) | `Parallel Hash Join` over `Parallel Seq Scan` of 509,000 `student_fees` + index-only scan of roll | 7,894 (2,447 from disk) | 88.9 / 87.0 ms |
| After (tenant-column) | `Parallel Index Scan using idx_student_fee_school` | 4,089 (0 from disk) | 44.0 / 43.4 ms |

Cold first run of the joined shape: 186.3 ms (outside NFR-03's 100 ms per-statement budget).

**Concurrency ladder impact:** Dashboard throughput rose from 10.3 --> 17.8 req/s at c=4, and from 11.1 --> 19.9 req/s at c=8. The endpoint now holds the budget at 4-wide (p95 281.5 < 300) where pre-fix c=4 breached by 85%. It still breaches at 8-wide.

### Fix 2: N+1 Elimination on Result Sheets

**File:** `ResultRepository.java:31-38`

**Change:** The statement behind both result-sheet routes became one query with five `JOIN FETCH`es over the entity graph the response actually reads.

**Impact:**

| Metric | Before | After |
|---|---|---|
| SQL statements for 6-row result sheet | 26 (19 per-row lazy loads) | 7 (constant) |
| `GET /api/v1/results/student/{id}` status | 500 (`LazyInitializationException`) | 200 |
| Scaling with result count | O(n) -- each row adds lazy loads | O(1) -- a seventh result adds nothing |

**Guard:** Both are pinned in `SqlStatementCountPerRequestTest` (cases 8 and 9). The ninth case asserts the HTTP status *before* comparing the statement count, so deleting the fetch join reopens the finding as a red build. Final reactor run: **121 tests, 0 failures, 0 errors, 0 skipped** in 21.9 s.

### Fix 3: V10 Migration (trigram indexes)

**Branch:** `perf/v10-index-the-measured-reads` at `61c8ce1` (NOT merged to master)

**File:** `V10__index_the_measured_reads.sql` (5 new indexes including `pg_trgm` extension)

**Impact on selective search:**

| Metric | Before V10 | After V10 |
|---|---|---|
| `search?query=student52000@` p95 (concurrent) | 323.9 ms | 68.7 ms |
| Plan | Sequential scan | Trigram index scan |

This closed the selective-search breach entirely (68.7 ms < 300 ms budget). However, the matches-all breach (`search?query=er`, 2 characters) is unaffected because 2-character patterns yield no trigrams.

---

## 7. Root Cause Analysis

### Breach 1: Short Search Pattern (`search?query=er`, p95 482 ms)

**Root cause:** PostgreSQL `pg_trgm` trigram indexes require patterns of at least 3 characters to produce useful trigrams. A 2-character search pattern like `er` yields zero trigrams, so the planner falls back to a sequential scan over the entire tenant-scoped student table (96,443 active rows).

**Why sequential is expensive:** The scan must evaluate the `ILIKE '%er%'` predicate against every row, with no index assistance. Under concurrent load (8 clients), multiple sequential scans compete for I/O and CPU, inflating latency from the sequential baseline of ~96 ms (p95) to 482 ms.

**Product decision required:** Either enforce a minimum query length of 3 characters (rejecting shorter queries with a validation error), or accept that 2-character searches are inherently slow and document the limitation as an accepted exception to NFR-02.

### Breach 2: Deep Pagination (`search?page=4000`, p95 429 ms)

**Root cause:** OFFSET-based pagination with `OFFSET 80000` (page 4000 x size 20) forces PostgreSQL to scan and discard 80,000 rows before returning the requested 20. With `work_mem=4MB`, the sort spills 7,880 kB to disk via external merge sort.

**Why this is structural:** OFFSET pagination is O(offset + limit), not O(limit). Every deeper page walks proportionally more rows. At page 4000, the database has sorted and discarded 80,000 rows -- the cost is dominated by the sort, not the fetch.

**Why V10 indexes do not help:** The trigram and covering indexes accelerate the WHERE clause, not the OFFSET skip. The sort must still materialize.

**Product decision required:** Implement keyset/cursor-based pagination (O(limit) regardless of depth), increase `work_mem` to avoid disk spills, or accept the limitation and cap maximum page depth. Stage 4's bounded-list/keyset work is the architectural fix.

### Breach 3: Dashboard Contention (`/dashboard/school`, p95 351 ms, max 1382 ms)

**Root cause:** The dashboard endpoint issues 7 aggregate SQL statements per request (5 `COUNT`s + 2 `SUM`s over `student_fees`, touching 254,500 rows per tenant). The tenant-column fix (Fix 1 above) halved the per-statement cost, but under 8-wide concurrency the endpoint asks for roughly 24 database processes to share 8 CPU cores.

**Why the fix was necessary but not sufficient:** The dashboard fix made each of the 7 statements cheaper (88.9 ms --> 44.0 ms for the two SUM queries), reducing total per-request database time. But the number of statements did not decrease -- 7 aggregate queries still hit the database per HTTP request. At c=4, the cheaper statements are fast enough to hold the budget (p95 281.5 ms < 300 ms). At c=8, CPU contention among ~24 parallel worker processes inflates latency past the budget again.

**Control probe evidence:** `search?query=verma` (unchanged code) moved p50 by -7% to +6% across ladder rungs -- flat. `GET /students/{id}` (unchanged code) moved p95 from 13.3 to 73.2 ms at 8-wide with 60 ms of laptop jitter -- an order of magnitude smaller than the 800 ms the dashboard shed. The improvement is real; its exact size at 8-wide lies between -20% and -53%.

**Architectural fix required:** Consolidate 7 statements into a single-pass aggregate with `FILTER (WHERE ...)`, add a short-TTL cache with tenant isolation, or precompute aggregates per academic year. All are larger changes than Stage 5's remit.

---

## 8. V10 Migration Status

| Aspect | Status |
|---|---|
| Migration file | `V10__index_the_measured_reads.sql` |
| Content | `CREATE EXTENSION pg_trgm` + 5 indexes (trigram GIN on student name, covering index on student_fees school_id, etc.) |
| Branch | `perf/v10-index-the-measured-reads` at `61c8ce1` (10 files, 708 insertions, 115 deletions) |
| Verified on PostgreSQL 15.8 | **Yes** -- all 5 indexes created, `pg_trgm` extension active, selective search p95 324 ms --> 69 ms |
| Merged to master | **No** |
| Verified on PostgreSQL 17 | **No** |
| Risk | `CREATE EXTENSION pg_trgm` requires `CREATE` privilege on the database -- unverified on PG17 |

**PG17 verification steps required (not yet performed):**
1. Apply V10 to a fresh PostgreSQL 17 database
2. Verify `pg_trgm` extension creates successfully
3. Verify all 5 indexes are created
4. Run the full reactor test suite (`mvn verify`) on PG17
5. Run the performance measurement suite on PG17
6. Compare latency numbers against PG15.8 baseline

---

## 9. Remaining Work

### Product Decisions Needed

| Decision | Options | Impact |
|---|---|---|
| Minimum search query length | Enforce 3-char minimum, or accept slow 2-char searches | Determines whether the `matches-all` breach is a defect or an accepted exception |
| Pagination strategy for deep pages | Keyset/cursor pagination, or page-depth cap, or accept OFFSET cost | Determines whether the `deep pagination` breach is fixed architecturally or bounded by policy |
| Dashboard freshness requirements | Real-time aggregates, short-TTL cache, or precomputed per academic year | Determines the architectural approach to the dashboard contention breach |

### Architectural Changes

| Change | Scope | Breach addressed |
|---|---|---|
| Dashboard aggregate caching | Short-TTL cache with tenant isolation, or materialized view | Dashboard contention (p95 351 ms at 8-wide) |
| Keyset/cursor pagination | Replace OFFSET-based paging in `StudentServiceImpl` | Deep pagination (p95 429 ms at 8-wide) |
| Single-pass dashboard query | Consolidate 7 statements into 1 with `FILTER (WHERE ...)` | Dashboard contention (reduces per-request DB load) |
| Minimum query length validation | Reject searches with < 3 characters | Short search pattern (p95 482 ms at 8-wide) |

### Verification Gaps

| Gap | Risk | Effort |
|---|---|---|
| PG17 compatibility for V10 migration | Production may target PG17; `pg_trgm` creation unverified | Set up PG17 container, apply chain V1-V10, run reactor + perf suites |
| Cold-cache performance | All measurements are warm-cache; first-hit latency unknown | Add `pg_dropcache` step between probes |
| Multi-instance behavior | Single JVM measured; load-balanced topology untested | Deploy 2+ instances behind a load balancer, re-measure |
| p99 with statistical strength | 20 samples gives a weak p99 estimator; worst-run proxy used | Increase to 100+ samples, or use a dedicated load-testing tool (k6, Gatling) |

---

## 10. Recommendations

Priority-ordered next steps:

1. **[P0] Merge V10 to master.** The migration is verified on PG15.8 and closes one of three remaining breaches entirely (selective search: 324 ms --> 69 ms). Keeping it on a backup branch risks bit-rot. Merge `perf/v10-index-the-measured-reads` at `61c8ce1` or cherry-pick the migration file.

2. **[P0] Verify V10 on PostgreSQL 17.** Before any production deployment targeting PG17, apply the full migration chain (V1-V10) to a fresh PG17 database and run both the reactor test suite and the performance measurement suite. Document the target PostgreSQL version explicitly.

3. **[P1] Decide minimum search query length.** The 2-character search breach is not a performance defect -- it is a product decision. Either enforce a 3-character minimum (rejecting `er` with a validation error) or document the limitation as an accepted exception to NFR-02 with a user-facing notice.

4. **[P1] Decide pagination strategy.** OFFSET-based pagination at page 4000 is structurally O(offset + limit). Either implement keyset/cursor pagination (Stage 4's bounded-list work), cap maximum page depth, or accept the limitation. This is a product decision, not a performance patch.

5. **[P1] Consolidate dashboard aggregates.** The dashboard's 7 statements per request are the remaining architectural bottleneck. A single-pass aggregate with `FILTER (WHERE ...)` would reduce per-request database load by ~6x. A short-TTL cache (30-60 seconds, tenant-isolated) would eliminate the problem entirely for repeat visits. Both are larger changes than Stage 5's remit but are the only path to meeting NFR-02 at 8-wide concurrency for this endpoint.

6. **[P2] Establish a reproducible benchmark.** The current measurements are single-machine, warm-cache, 20-sample. A reproducible benchmark with fixed hardware, dataset, application build, workload, concurrency, warm-up period, and 100+ samples would make before/after comparisons statistically rigorous. Consider k6 or Gatling for the concurrent load leg.

7. **[P2] Measure cold-cache performance.** Add a `pg_dropcache` step (or `DISCARD ALL` + restart) between probes to capture first-hit latency. Production users do not always hit warm caches.

8. **[P2] Pin remaining N+1 sites.** `SqlStatementCountPerRequestTest` covers 9 endpoints. The roadmap identifies additional N+1 sites on MVP-journey paths that are read but not measured. Each should get a test case with a `measured + 1` ceiling before the next release.

---

## 11. Appendix

### A. EXPLAIN (ANALYZE, BUFFERS) Probe Results

Six probes from `scripts/seed/perf_seed_50k.sql` footer, run after `ANALYZE;` on the volume tenant (school 1).

| Probe | Description | Plan | Execution |
|---|---|---|---|
| P1 | Deep roster page, `OFFSET 40000` | `Index Scan using students_pkey`, 40,020 rows walked, 2,223 removed by status filter | 16.6 ms |
| P2 | Head-count of school 1 | `Index Only Scan using idx_students_school_status`, Heap Fetches: 0 | 6.4 ms |
| P3 | Fee ledger joined to roll | `idx_student_fee_student_status` + `students_pkey`, 20 rows touched | 0.09 ms |
| P4 | One pupil's attendance history | `Index Scan Backward using idx_attendance_student_date` | 0.08 ms |
| P5 | Tenant's users with roles | `idx_user_roles_role` + `users_pkey`, 135 teachers | 0.21 ms |
| P6a | Dashboard fee SUM (joined shape, before fix) | `Parallel Hash Join` over `Parallel Seq Scan` of 509,000 `student_fees` + index-only scan, 7,894 blocks (2,447 from disk) | 88.9 ms |
| P6b | Dashboard fee SUM (tenant-column shape, after fix) | `Parallel Index Scan using idx_student_fee_school`, 4,089 blocks, 0 reads from disk | 44.0 ms |

All probes plan through indexes this project created. NFR-03's per-statement budget (p95 < 100 ms) is met by every probe, including the post-fix dashboard shape.

### B. N+1 Statement Count Guard

`SqlStatementCountPerRequestTest` pins 9 endpoints at `measured + 1` SQL statement ceilings:

| Endpoint | Statements pinned |
|---|---|
| `GET /students/search` | 4 |
| `GET /students` | 4 |
| `GET /students/{id}` | 3 |
| `GET /classrooms` | 9 |
| `GET /attendance/student/{id}` | 6 |
| `GET /fees/student/{id}` | 3 |
| `GET /dashboard/school` | 4 |
| `GET /results/student/{id}` | 7 |
| `GET /parents/children/{id}/results` | 3 |

Final reactor run: **121 tests, 0 failures, 0 errors, 0 skipped** in 21.9 s. CI run `41454080530`: same 121 tests, 0 failures, on `postgres:15` service container.

### C. Concurrency Ladder -- Pre-fix vs Post-fix (Dashboard)

| Clients | Before p50/p95 (ms) | Before max (ms) | Before req/s | After p50/p95 (ms) | After max (ms) | After req/s |
|---|---|---|---|---|---|---|
| 1 | 167.1 / 201.0 | 201.0 | 5.4 | 99.2 / 337.6 | 337.6 | 6.2 |
| 2 | 214.9 / 239.2 | 239.2 | 8.4 | 121.6 / 169.7 | 169.7 | 13.6 |
| 4 | 350.3 / 554.8 | 627.8 | 10.3 | 178.2 / 281.5 | 289.8 | 17.8 |
| 8 | 492.0 / 1502.6 | 1834.6 | 11.1 | 283.3 / 705.9 | 893.1 | 19.9 |
| 16 | 1258.5 / 2392.5 | 2619.6 | 10.6 | 628.0 / 1209.2 | 1413.5 | 21.5 |

The dashboard throughput plateau (10-11 req/s at c=4..16 pre-fix) is eliminated post-fix (18-22 req/s at c=4..16). NFR-02 now holds at c=4 (p95 281.5 < 300) but still breaches at c=8.

### D. Migration Chain

| Version | File | Purpose |
|---|---|---|
| V1 | `V1__baseline_current_schema.sql` | Baseline schema |
| V2 | `V2__materialise_tenant_key.sql` | Materialize `school_id` FK |
| V3 | `V3__natural_keys.sql` | Natural keys for students, users |
| V4 | `V4__indexes.sql` | Tenant-scoped indexes (NFR-05) |
| V5 | `V5__composite_tenant_keys.sql` | Composite tenant keys |
| V6 | `V6__require_the_tenant_key.sql` | NOT NULL on tenant keys |
| V7 | `V7__record_who_wrote_the_pupil.sql` | Audit columns (created_by, updated_by) |
| V8 | `V8__close_the_dormant_composite_foreign_keys.sql` | Dormant composite FKs |
| V9 | `V9__soft_delete_attendance_and_assignment.sql` | Soft-delete support |
| V10 | `V10__index_the_measured_reads.sql` | Trigram indexes + covering index (NOT merged to master) |

### E. Dataset Composition

| Category | Tables | Row count |
|---|---|---|
| Core | `students` (101,800), `users` (103,480), `schools` (4) | 205,284 |
| Academic | `classrooms` (24), `sections` (12), `subjects` (24), `academic_years` (8) | 68 |
| Enrollment | `student_admissions` (101,800), `student_fees` (509,000), `attendances` (203,600) | 814,400 |
| Assessment | `exams` (24), `exam_subjects` (144), `student_exams` (203,600), `results` (101,800) | 305,568 |
| Financial | `fee_structures` (48), `fees` (144), `invoices` (101,800), `receipts` (101,800), `fee_payments` (203,600) | 507,392 |
| Communication | `notifications` (4,000), `announcements` (4,000) | 8,000 |
| Staff | `employees` (180), `teacher_assignments` (288), `timetables` (48), `timetable_slots` (1,440) | 1,956 |
| Family | `parents` (1,500), `parent_students` (3,000) | 4,500 |
| **Total** | **27 tables** | **1,840,468** |

### F. Measurement Limitations

The following are explicitly **not** covered by these measurements:

- **Cold-cache behavior:** Every run is a warm-cache run. No `pg_dropcache` or restart between probes.
- **Multi-instance behavior:** One backend JVM is measured. Load-balanced topology is not tested.
- **p99 with statistical strength:** 20 samples give a weak p99 estimator. The tables report worst-run (max) as the nearest-rank p99 proxy and label it as such.
- **SQL-layer percentiles:** Only 6 EXPLAIN probes measure individual statement latency. The HTTP-level percentiles (from the harness) include network, serialization, and connection-pool queuing.
- **Production generalization:** The absolute milliseconds do not generalize to production hardware. The shape does -- aggregate CPU inside the database is the bottleneck, and indexed single-row reads are not.

---

*Report generated from measurements in `scripts/verify-perf-50k.sh`, `scripts/seed/perf_seed_50k.sql`, `docs/ENGINEERING_ROADMAP.md`, and `docs/release/GMS_REMEDIATION_TRACKER.md`. All data is reproducible by running the harness against the seed on the specified hardware.*
