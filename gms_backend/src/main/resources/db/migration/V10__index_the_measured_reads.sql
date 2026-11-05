-- V10__index_the_measured_reads.sql
--
-- NFR-02: the deferred step arrives on the trigger V4 published for it.
--
-- V4__indexes.sql:12-15 did not skip `pg_trgm` by preference. It named the measurement that would
-- bring it back: "when p95 on the student search crosses the acceptable number, the extension is
-- one migration, and a btree that cannot serve the query is not the same as one that was never
-- built." The acceptable number is published (PRODUCT_REQUIREMENTS.md §9 NFR-02: p95 < 300 ms and
-- p99 < 800 ms for the ten hottest endpoints at volume), and section 4b of
-- scripts/verify-perf-50k.sh crossed it on 2026-11-03 at 8 clients in flight over 96,443
-- servable pupils — `search?query=<selective>` p95 323.9 ms, `search?query=<matches-all>` 510.2,
-- `search?page=4000` 451.9, `GET /dashboard/school` 1023.6 with a worst run at 1620.3. This file
-- is that one migration.
--
-- What it does NOT contain is equally deliberate, because an index the planner will not choose is a
-- write-time tax with no reader-side return:
--   * no index for `search?page=4000 (deep)`. The probe is `LIMIT 20 OFFSET 80000` over the whole
--     roll ordered by last_name. A btree on (last_name) reaches the 80,000th entry by fetching
--     80,000 heap tuples — strictly worse than the top-N heapsort it would replace. Deep offsets
--     are fixed in the query, by keyset pagination, which is Stage 4's bounded-list work and not
--     something a migration can do;
--   * no index for `exams (school_id, status)`. The dashboard's exam count is the only one of its
--     seven aggregates without a supporting composite (DashboardStatisticsRepository.java:94
--     filters on a `status` that appears in no index), and it is not the driver: `exams` holds
--     hundreds of rows per school against `student_fees`' 509,000, so the aggregate below answers
--     the measured breach and this one stays recorded as an unmoved gap rather than dressed up as
--     closed.
--
-- Rule I-1 and Rule I-2 (V4__indexes.sql:121-160, re-asserted by V6) are untouched by anything
-- here: this migration only adds, so an index leading on `school_id` cannot go missing, and every
-- new expression index sits beside the btree it supplements rather than replacing it.

-- ── The extension the deferred step needs ────────────────────────────────────────────────────
-- `IF NOT EXISTS` is the one statement in this file that is idempotent, and it has to be: an
-- extension is cluster-wide, not per-schema, so a restore into a cluster that already has
-- `pg_trgm` — or a second Flyway run against a database where someone enabled it by hand — must
-- not die on the CREATE. `pg_trgm` has been a trusted extension since PostgreSQL 13, so the
-- schema-owning role that runs this migration may create it without superuser rights; on the
-- target deployment, where `ADR-022` moves migrations out of the application process, the role
-- that runs them needs `CREATE` on the database and nothing more.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ── Substring search on the four expressions the query actually lowers ────────────────────────
-- StudentRepository.findStudents (StudentRepository.java:63-92) matches a pattern against exactly
-- these four lowered expressions, joined by OR. Two facts decide the shape below.
--
-- The predicates are `lower(col) LIKE '%…%'` with the pattern pre-lowercased in Java
-- (StudentServiceImpl.java:217-226). A leading wildcard is invisible to a btree — including
-- `varchar_pattern_ops`, which serves prefix only — and a trigram index on the bare `col` would
-- not match either, because PostgreSQL matches an index on its *expression*, so the indexed
-- expression has to be `lower(col)`, which is what V4's `idx_students_last_name_prefix` is not.
--
-- Four indexes, not three and not one: for `a OR b OR c OR d` the planner can only use indexes at
-- all when *every* branch has one, combining them with BitmapOr. A trigram on the names alone
-- would leave the whole disjunction on a sequential scan, and the missing branches are the ones
-- the probe hits — `query=student52000@` is a `student_id`/`email` pattern, not a name.
--
-- What this does not fix, stated here so the roadmap cannot quietly claim it: a pattern shorter
-- than three characters yields no trigrams, so the planner has nothing to look up and falls back
-- to scanning. That is `query=er`, the `<matches-all>` probe, which matches most of the roll by
-- design. Its 510.2 ms is the cost of returning and sorting a large result under 8 concurrent
-- clients, and the remedy is a bounded response or a search that requires three characters, not
-- an index.
CREATE INDEX idx_students_first_name_trgm     ON students USING gin (lower(first_name) gin_trgm_ops);
CREATE INDEX idx_students_last_name_trgm      ON students USING gin (lower(last_name)  gin_trgm_ops);
CREATE INDEX idx_students_student_id_trgm     ON students USING gin (lower(student_id) gin_trgm_ops);
CREATE INDEX idx_students_email_trgm          ON students USING gin (lower(email)      gin_trgm_ops);

-- ── The dashboard's two fee sums, in one index ────────────────────────────────────────────────
-- DashboardServiceImpl.getSchoolDashboard (DashboardServiceImpl.java:48-75) issues seven
-- aggregates per request, and the two that decide the endpoint's latency are StudentFeeRepository
-- .java:28 and :31 — `SUM(sf.amount_paid)` where `school_id = ? AND status IN ('PAID','PARTIAL')`
-- and `SUM(sf.balance_amount)` where `status IN ('PENDING','PARTIAL','OVERDUE')`. V4 gave
-- `student_fees` an index leading on `school_id` (idx_student_fee_school, V4:88) and nothing else
-- that fits: the school predicate matches every fee row in the tenant, so the plan is a bitmap
-- scan over all of them and a heap fetch per row to reach the two numbers, which at 509,000 rows
-- is where the 1023.6 ms goes.
--
-- One index carries both sums. `school_id` first because it is the equality, `status` second
-- because it is the `IN`, and the two aggregated columns as INCLUDE payloads so the scan can stay
-- index-only and never touch the heap. Two separate indexes would let each sum use its own, at
-- double the write cost on the hottest table in the product for no reading benefit — V4:144-146
-- argues this exact point about write-time taxes, and one index with both payloads is the answer
-- to it. `balance_amount` is nullable and `amount_paid` is not (V1:1009-1010); `status` is the
-- `IN` column at V1:1014. Neither aggregated column is a predicate here, so INCLUDE is the right
-- clause rather than a key.
CREATE INDEX idx_student_fee_school_status    ON student_fees (school_id, status)
  INCLUDE (amount_paid, balance_amount);

-- ── The deferred step, arrived ────────────────────────────────────────────────────────────────
-- V4 said the extension is "one migration" when the measurement crossed. It has, and this is it.
-- The two properties the migration depends on are asserted here in the same style as V4's own
-- guards (V4:121-160), so a future change that breaks them fails the deploy instead of re-appearing
-- as a slow dashboard three months later. First: all four GIN branches exist, because the planner
-- refuses to index-scan an OR with an uncovered branch. Second: each one indexes the *lowered*
-- expression, because an index on the bare column silently stops matching `lower(col) LIKE ?` —
-- the exact failure mode V4's `varchar_pattern_ops` choice was already exposed to, and the reason
-- this migration says `lower(first_name)` rather than `first_name`.
DO $$ BEGIN
  IF (SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
        JOIN pg_am a ON a.oid = c.relam
       WHERE n.nspname = 'public' AND a.amname = 'gin'
         AND c.relname IN ('idx_students_first_name_trgm', 'idx_students_last_name_trgm',
                           'idx_students_student_id_trgm', 'idx_students_email_trgm')) <> 4
  THEN RAISE EXCEPTION 'V10: the student search needs all four lowered GIN branches; BitmapOr refuses to plan a partial set'; END IF;

  IF (SELECT count(*) FROM pg_index i JOIN pg_class c ON c.oid = i.indexrelid
       WHERE c.relname LIKE 'idx_students_%_trgm'
         AND pg_get_indexdef(i.indexrelid) NOT LIKE '%lower%') > 0
  THEN RAISE EXCEPTION 'V10: a student trigram index does not index the lowered expression, so the query''s lower(col) LIKE ? predicate cannot use it'; END IF;
END $$;
