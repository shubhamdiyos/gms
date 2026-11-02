-- ============================================================================
-- perf_seed_50k.sql
-- ============================================================================
-- Purpose : Build the volume dataset NFR-01 asks for -- ">=50,000 records per school
--           in the primary tables, 2 schools" (docs/PRODUCT_REQUIREMENTS.md:182) -- so
--           the read paths behind the V4 tenant indexes and the V5/V6 same-school
--           composite keys can be MEASURED at real volume, not against a demo fixture.
--           The clause is PER SCHOOL, so schools 1 and 2 are each seeded with 50,000
--           students; a cross-table sum that merely clears 50,000 would not satisfy it.
--           The six EXPLAIN (ANALYZE, BUFFERS) probes are listed in the footer.
--
-- SAFETY  : This file TRUNCATEs and repopulates data. It runs ONLY against the
--           disposable database `gms_perf_seed`. The guard below aborts if it is
--           connected to any other database, so an operator cannot point it at
--           `gms` or a live gms_c*_verify / gms_c4_* / gms_c7_* database by mistake.
--
-- Schema  : The schema is Flyway's (V1..V7). This file creates NOTHING: no table,
--           no index, no constraint, no ALTER. It only inserts rows that satisfy
--           the schema as written, so `spring.jpa.hibernate.ddl-auto=validate`
--           accepts the result. It lives outside gms_backend/src/main/resources
--           on purpose, so it is never packaged into the jar.
--
-- HOW TO RUN
--   0) Recreate the disposable database and apply the seven migrations in version order
--      (host files are not visible inside the container, so pipe them over stdin):
--
--        set -a && . ./.env && set +a
--        docker exec -i -e PGPASSWORD="$DB_PASSWORD" gms-postgres psql -U gms_user -d postgres \
--          -v ON_ERROR_STOP=1 -c "DROP DATABASE IF EXISTS gms_perf_seed;" \
--          -c "CREATE DATABASE gms_perf_seed OWNER gms_user;"
--        cd gms_backend/src/main/resources/db/migration
--        for f in V1__baseline_current_schema V2__materialise_tenant_key V3__natural_keys \
--                 V4__indexes V5__composite_tenant_keys V6__require_the_tenant_key \
--                 V7__record_who_wrote_the_pupil; do
--          docker exec -i -e PGPASSWORD="$DB_PASSWORD" gms-postgres psql -U gms_user -d gms_perf_seed \
--            -v ON_ERROR_STOP=1 -q -f /dev/stdin < $f.sql
--        done
--
--   1) Load the seed. The file carries its own BEGIN/COMMIT, so do NOT add -1:
--
--        docker exec -i -e PGPASSWORD="$DB_PASSWORD" gms-postgres psql -U gms_user -d gms_perf_seed \
--          -v ON_ERROR_STOP=1 -f /dev/stdin < scripts/seed/perf_seed_50k.sql
--
--      One transaction, 1,840,468 rows over the 27 tables the footer counts. Section 20
--      is the slow one -- an invoice per pupil whose total_amount is a LATERAL SUM over
--      that pupil's five student_fees, so it runs 101,800 aggregations against a table
--      with no statistics yet. Expect it to dominate the load; it is not a probe, and
--      nothing in the harness's latency budget reads it.
--
--   2) Give the planner statistics, then run the footer probes:
--
--        docker exec -i -e PGPASSWORD="$DB_PASSWORD" gms-postgres psql -U gms_user -d gms_perf_seed -c "ANALYZE;"
--
-- DETERMINISM : One setseed(0.42) per run. Row COUNTS and the status/tenant
--               distribution come from integer arithmetic (id % n), never from
--               random(), so they are identical on every run; random() only jitters
--               money, which no count or probe depends on. Every id is explicit.
--               Because the counts are arithmetic, they are also a FUNCTION of the
--               student total N, and several of them are only exact when N divides
--               cleanly: exactly half of the 2N student_exams rows have an even id
--               (that half is `results`), exactly half of the 5N student_fees rows have
--               an odd id, and the 20%-untouched cut (student_id % 5 = 1) removes the
--               same fraction of that half only when N is a multiple of 10, which makes
--               fee_payments exactly 2N. N = 101,800 is. Every derived count pinned in
--               this file is recomputed from N, not carried over from the old shape.
--
-- SHAPE       : 4 schools, deliberately unequal the way real tenants are:
--               50000 / 50000 / 1200 / 600 students = 101,800. Schools 1 and 2 are the
--               two volume tenants NFR-01 names, 50,000 students EACH; schools 3 and 4
--               stay small on purpose, so the same code path is measured at a second
--               scale and the multi-tenant probes keep meaning something.
--               2 academic years each (exactly one is_current per school, because V3
--               creates the partial unique index uq_academic_years_one_current),
--               6 classrooms, 3 sections and 6 subjects each, 180 employees in total
--               (135 of them teaching), 36 fee definitions per school.
--               509,000 student_fees = 5 per student, 203,600 attendances = 2 per
--               student, plus the FK parents those require: fees, fee_structures,
--               timetables, teacher_assignments, timetable_slots, exams,
--               exam_subjects, student_exams, results, invoices, receipts.
--               Everything that is fixed PER SCHOOL did not move with the scale-up:
--               1,000 notifications and 1,000 announcements each (4,000 of both),
--               1,500 parents, 2 children per parent (3,000 links), 180 employees,
--               288 teacher_assignments, 1,440 timetable_slots, 144 fees, 144
--               exam_subjects, 48 timetables, 48 fee_structures, 24 each of classrooms,
--               subjects, exams, 12 sections, 8 academic_years, 4 schools.
--               Every child row carries the SAME school_id as its parent so the
--               V5/V6 composite keys accept it; the negative tests in section 27
--               prove those constraints reject a row whose tenant disagrees.
--               V7 put created_by / updated_by on students, classrooms, subjects and
--               student_admissions: nullable integer FKs to users(id), left NULL here,
--               because a raw SQL load is not an application write and V7 section 3
--               reads a NULL author as "unknown" rather than as a lie.
--
-- STATUS CODES: the status domains are CODES, not words, wherever the entity
--               defaults one in Java: schools.status, students.status,
--               users.user_status, employees.employee_status, sections.status,
--               classrooms.status, subjects.status, academic_years.status,
--               exams.status, exam_subjects.status, student_exams.status and
--               results.status are '1' (active) / '0' (inactive) -- e.g.
--               Student.java:61-62, User.java:77-78, SchoolServiceImpl.java:160.
--               Four columns are the exception and carry WORDS:
--               fees.status ACTIVE/ARCHIVED (FeeServiceImpl.java:54,110),
--               student_fees.status PENDING/PARTIAL/PAID/OVERDUE/ARCHIVED
--               (StudentFeeServiceImpl.java:76,125 + FeePaymentServiceImpl.java:168-170),
--               fee_payments.status SUCCESS/CANCELLED (FeePaymentServiceImpl.java:65,115),
--               attendances.status PRESENT/ABSENT/LATE, varchar(10) (Attendance.java:34-35),
--               receipts.status ISSUED/CANCELLED (ReceiptServiceImpl.java:111).
--
-- IDs         : block-allocated from the perf_school staging table below:
--               students 1..101800, employees 1..180, parents 1..1500,
--               classrooms (s-1)*6+c, sections (s-1)*3+n, subjects (s-1)*6+n,
--               academic_years (s-1)*2+y, fees (s-1)*36+(g-1)*6+t, exams (s-1)*6+e,
--               exam_subjects (s-1)*36+(e-1)*6+sub, timetables (classroom-1)*2+y,
--               invoice.id = student.id, fee_payment.id = its student_fee.id,
--               receipt.id = its fee_payment.id. users take three disjoint blocks that
--               section 22 derives -- 1..180 (employees), 1001..102800 (students),
--               200001..201500 (parents). student_fees, attendances,
--               student_exams, timetable_slots and teacher_assignments take ids from
--               an explicitly ordered row_number(). Identity sequences are resynced in
--               section 26 so the application cannot collide with a seeded id.
--               Integer is still the right type at this size: the widest id this file
--               writes is student_fees at 509,000 (fee_payments and receipts follow at
--               508,999, users at 201,500), against an `integer` ceiling of
--               2,147,483,647, and every block start is cast ::int for the reason it
--               always was -- SUM() OVER yields bigint, generate_series() yields integer.
-- ============================================================================

-- ── Guard: refuse to run anywhere but gms_perf_seed ─────────────────────────
DO $$ BEGIN
  IF current_database() <> 'gms_perf_seed' THEN
    RAISE EXCEPTION 'perf_seed_50k.sql must only run against gms_perf_seed; connected to %', current_database();
  END IF;
END $$;

BEGIN;

SELECT setseed(0.42);

-- ── Reset this database's own data (CASCADE takes the referrers with it) ───
TRUNCATE TABLE
  schools, academic_years, classrooms, sections, subjects, employees, parents,
  students, parent_student, exams, exam_subjects, student_exams, results,
  timetables, teacher_assignments, timetable_slots, attendances,
  fees, fee_structures, student_fees, fee_payments, invoices, receipts,
  users, user_roles, notifications, announcements,
  designations, grading_schemes, school_calendars, student_admissions, messages,
  communication_templates, payment_gateways, financial_reports,
  financial_transactions, email_logs, sms_logs
  RESTART IDENTITY CASCADE;

-- ── 0. Staging: the per-school shape, one row per tenant ──────────────────
--     Block starts are cumulative, so id ranges cannot overlap between schools.
--     ON COMMIT DROP: this table exists only inside this transaction, and it is a
--     temp table so it is never part of the Flyway-validated schema.
--     n_students is the column NFR-01 counts: schools 1 and 2 are the "2 schools" of
--     the clause and each carries 50,000, so the volume claim is true PER SCHOOL and
--     not merely in aggregate. n_emp / n_teaching / n_parents are the staff and family
--     shape and are deliberately NOT scaled with the roll: a real tenant's faculty and
--     parent roster grow far slower than its student list, and the probes below
--     (teacher_assignments, parent links, the role split) only stay meaningful if the
--     faculty rows are a realistic minority. Schools 3 and 4 keep the old sizes so the
--     same code path is measured at two scales.
--     Widening n_students widens exactly one derived block start that anything else
--     keys off: student_from, and through it the student user ids in section 22 --
--     which is why that file's parent user block moved to 200,000. Keep the invariant
--     in section 22 true whenever you change these numbers: 1,000 + SUM(n_students)
--     must stay below the parent user block start.
CREATE TEMP TABLE perf_school ON COMMIT DROP AS
SELECT v.school_id,
       v.n_students, v.n_emp, v.n_teaching, v.n_parents,
       -- ::int everywhere, because SUM() OVER yields bigint and there is no `date + bigint`
       -- operator; these blocks are far below int range and must stay comparable to
       -- generate_series() output, which is integer.
       (SUM(v.n_students) OVER w - v.n_students + 1)::int AS student_from,
       (SUM(v.n_emp)      OVER w - v.n_emp      + 1)::int AS emp_from,
       (SUM(v.n_parents)  OVER w - v.n_parents  + 1)::int AS parent_from,
       ((v.school_id - 1) * 6  + 1)::int                  AS classroom_from,    -- 6 each
       ((v.school_id - 1) * 3  + 1)::int                  AS section_from,      -- 3 each
       ((v.school_id - 1) * 6  + 1)::int                  AS subject_from,      -- 6 each
       ((v.school_id - 1) * 2  + 1)::int                  AS ay_from,           -- 2 each
       ((v.school_id - 1) * 36 + 1)::int                  AS fee_from,          -- 6 types x 6 grades
       ((v.school_id - 1) * 6  + 1)::int                  AS exam_from,         -- 6 each
       ((v.school_id - 1) * 36 + 1)::int                  AS exam_subject_from, -- 6 exams x 6 subjects
       ((v.school_id - 1) * 12 + 1)::int                  AS fee_structure_from,
       ((v.school_id - 1) * 1000 + 1)::int                AS notice_from
FROM (VALUES (1, 50000, 60, 45, 600),      -- volume tenant 1 (NFR-01)
             (2, 50000, 50, 38, 450),      -- volume tenant 2 (NFR-01)
             (3,  1200, 40, 30, 300),      -- small tenant: same code path, other scale
             (4,   600, 30, 22, 150))
     AS v(school_id, n_students, n_emp, n_teaching, n_parents)
WINDOW w AS (ORDER BY v.school_id ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW);

-- ── 1. schools (4) — the tenant roots ──────────────────────────────────────
INSERT INTO schools (id, school_code, school_name, status, enabled, established_year,
                     email, phone, principal_name, board_affiliation, address,
                     created_at, updated_at, version)
SELECT s.school_id,
       'SCH' || lpad(s.school_id::text, 3, '0'),
       CASE s.school_id WHEN 1 THEN 'Delhi Public Academy'
                        WHEN 2 THEN 'Riverside International School'
                        WHEN 3 THEN 'Sunrise Central School'
                        ELSE 'Greenwood High' END,
       '1',                                            -- schools.status is a CODE (SchoolServiceImpl:160)
       true, 1978 + s.school_id * 4,
       'admin@school' || s.school_id || '.example',
       '011' || lpad((s.school_id * 111111)::text, 8, '0'),
       'Principal ' || s.school_id,
       CASE WHEN s.school_id % 2 = 0 THEN 'CBSE' ELSE 'STATE' END,
       'Campus ' || s.school_id || ', Sector ' || s.school_id,
       now() - make_interval(days => s.school_id * 30), now(), 0
FROM perf_school s;

-- ── 2. academic_years (2 per school = 8; exactly one is_current) ───────────
INSERT INTO academic_years (id, school_id, name, start_date, end_date, status, is_current,
                            created_at, updated_at, version)
SELECT s.ay_from + y.n - 1,
       s.school_id,
       'AY ' || (2025 - (y.n - 1)) || '-' || (2026 - (y.n - 1)),
       DATE '2025-04-01' - ((y.n - 1) * 365),
       DATE '2026-03-31' - ((y.n - 1) * 365),
       CASE WHEN y.n = 1 THEN '1' ELSE '0' END,       -- y=1 is the live year
       (y.n = 1),
       now() - make_interval(days => y.n * 10), now(), 0
FROM perf_school s, generate_series(1, 2) AS y(n);

-- ── 3. classrooms (6 per school = 24; one per grade G1..G6) ────────────────
INSERT INTO classrooms (id, school_id, name, grade, section, status, created_at, updated_at, version)
SELECT s.classroom_from + c.n - 1,
       s.school_id,
       'ROOM-' || s.school_id || lpad(c.n::text, 2, '0'),
       'G' || c.n,
       'S' || (((c.n - 1) % 3) + 1),
       '1',
       now() - interval '300 days', now(), 0
FROM perf_school s, generate_series(1, 6) AS c(n);

-- ── 4. sections (3 per school = 12) ────────────────────────────────────────
INSERT INTO sections (id, school_id, name, status, capacity, description,
                      created_at, created_on, updated_at, version)
SELECT s.section_from + n.n - 1,
       s.school_id,
       'SEC-' || chr(64 + n.n),                        -- SEC-A / SEC-B / SEC-C
       CASE WHEN n.n = 3 AND s.school_id = 4 THEN '0' ELSE '1' END,
       120 + n.n * 10,
       'Section ' || n.n || ' of school ' || s.school_id,
       now() - interval '300 days', now(), now(), 0
FROM perf_school s, generate_series(1, 3) AS n(n);

-- ── 5. subjects (6 per school = 24) ────────────────────────────────────────
INSERT INTO subjects (id, school_id, code, name, status, created_at, updated_at, version)
SELECT s.subject_from + n.n - 1,
       s.school_id,
       'SUB' || lpad(n.n::text, 2, '0'),
       (ARRAY['Mathematics','Science','English','History','Geography','Computer Science'])[n.n],
       CASE WHEN n.n = 6 AND s.school_id = 1 THEN '0' ELSE '1' END,
       now() - interval '300 days', now(), 0
FROM perf_school s, generate_series(1, 6) AS n(n);

-- ── 6. employees (180 total, 135 of them teaching) ─────────────────────────
--     employee_id and email are unique PER SCHOOL (V3: uq_employees_school_employee_id,
--     uq_employees_school_email). is_teaching drives idx_employees_is_teaching and
--     which employees can appear in teacher_assignments.
INSERT INTO employees (id, school_id, employee_id, first_name, last_name, email, phone_number,
                       date_of_birth, date_of_joining, department, designation, employee_status,
                       enabled, is_teaching, experience_years, qualification, salary, address,
                       emergency_contact_name, emergency_contact_phone,
                       created_at, updated_at, version)
SELECT s.emp_from + e.n - 1,
       s.school_id,
       'EMP' || lpad(s.school_id::text, 2, '0') || lpad(e.n::text, 4, '0'),
       'Emp' || (s.emp_from + e.n - 1),
       'Kaur' || ((s.emp_from + e.n) % 90),
       'emp' || (s.emp_from + e.n - 1) || '@school' || s.school_id || '.example',
       '9' || lpad(((s.emp_from + e.n - 1) * 37)::text, 9, '0'),
       DATE '1982-03-10' + ((s.emp_from + e.n) % 4000),
       DATE '2015-06-01' + ((s.emp_from + e.n) % 3000),
       CASE WHEN e.n <= s.n_teaching THEN 'ACADEMIC' ELSE 'ADMINISTRATION' END,
       CASE WHEN e.n <= s.n_teaching THEN 'TEACHER'
            WHEN e.n % 7 = 0 THEN 'CLERK' ELSE 'COORDINATOR' END,
       CASE WHEN (s.emp_from + e.n) % 23 = 0 THEN '0' ELSE '1' END,   -- ~4% inactive
       (s.emp_from + e.n) % 23 <> 0,
       (e.n <= s.n_teaching),
       1 + ((s.emp_from + e.n) % 20),
       CASE WHEN e.n <= s.n_teaching THEN 'M.Sc' ELSE 'B.Com' END,
       (35000 + ((s.emp_from + e.n) % 12) * 2500 + round((random() * 900)::numeric, 2))::numeric(10,2),
       'Staff Quarter ' || (e.n % 9) || ', Block ' || chr(65 + (e.n % 4)),
       'Emergency ' || (e.n % 50),
       '8' || lpad(((s.emp_from + e.n) * 13)::text, 9, '0'),
       now() - make_interval(days => 900 - (e.n % 700)), now(), 0
FROM perf_school s, generate_series(1, s.n_emp) AS e(n);

-- ── 7. parents (1,500 total) — GLOBAL rows: the table has no school_id ─────
--     D-2: one person, one parent account, across schools, and uq_parents_email is
--     global (V3). parents.user_id is left NULL here and backfilled in section 22,
--     because users.parent_id -> parents.id and parents.user_id -> users.id is a
--     circular pair.
INSERT INTO parents (id, first_name, last_name, email, phone_number,
                     created_at, created_on, updated_at, updated_on, version)
SELECT s.parent_from + p.n - 1,
       'Parent' || (s.parent_from + p.n - 1),
       'Sharma' || ((s.parent_from + p.n) % 120),
       'parent' || (s.parent_from + p.n - 1) || '@family.example',
       '8' || lpad(((s.parent_from + p.n) * 7919)::text, 9, '0'),
       now() - interval '200 days', now(), now(), now(), 0
FROM perf_school s, generate_series(1, s.n_parents) AS p(n);

-- ── 8. students (101,800 across the 4 schools) ─────────────────────────────
--     classroom_id and school_id are both NOT NULL, section_id is nullable (V1) and is
--     populated. They come from the student's position inside its OWN school block, so
--     students, timetables and attendance all resolve back to one tenant. student_id
--     (the admission number) is unique per school (V3); email is nullable with a
--     partial per-school unique index, so it gets one distinct value each.
--     The student_from blocks this writes are 1..50000 (school 1), 50001..100000
--     (school 2), 100001..101200 (school 3), 101201..101800 (school 4) -- contiguous and
--     gapless, which is what lets every row_number()-keyed child table below be counted
--     as a multiple of 101,800 instead of being tallied by hand.
--     Widths at the new top of the range: student_id becomes 'STD020052000' (12 chars in
--     a varchar(20)), and email becomes 'student101800@pupil.school4.example', comfortably
--     inside varchar(150).
--     The admission-number padding is 7 wide, and not for a cosmetic reason: lpad
--     TRUNCATES. PostgreSQL's lpad(value, len) cuts value to len when value is already
--     longer than len, so lpad('100780', 5) is '10078' and not '100780' -- verified with
--     SELECT lpad('100780',5,'0'). This section padded to 5 on the strength of the belief
--     that padding "simply stops" once the id outgrows it, and the load died on
--     uq_students_school_student_id at (3, 'STD0310000'): every school-3 and school-4 pupil
--     with a six-digit id collapsed onto its first five characters, ten pupils per admission
--     number. Schools 1 and 2 never showed it because their ids stay under 100000.
--     'SU' || lpad(st.id, 6) in section 22 now sits at exactly zero headroom -- 101,800 is
--     six digits, so it is still unique but has no margin; widen it with the roll.
--     The grade-aligned classroom split puts 8,334 pupils in school 1's first classroom
--     and 8,333 in each of its others; the small tenants still hold 200 and 100.
--     The deactivated roll: status '0' where (student_from + n) % 19 = 0, i.e. id % 19 =
--     18, which is 5,357 of the 101,800 (2,631 in school 1, 2,632 in school 2). Neither
--     the API search nor GET /students serves those rows (StudentRepository.findStudents
--     and findAllBySchool_IdAndStatusNot both refuse status '0'), so the roll the harness
--     can see through HTTP is 96,443 across all schools and 47,369 in school 1 -- the two
--     numbers verify-perf-50k.sh quotes for the API side, while the SQL side stays 101,800.
WITH seq AS (
  SELECT s.school_id, s.n_students, s.student_from, s.classroom_from, s.section_from, n.n
  FROM perf_school s, generate_series(1, s.n_students) AS n(n)
)
INSERT INTO students (id, school_id, classroom_id, section_id, student_id,
                      first_name, last_name, status, gender, date_of_birth, email, phone_number,
                      created_at, updated_at, version)
SELECT q.student_from + q.n - 1,
       q.school_id,
       q.classroom_from + (((q.n - 1) * 6) / q.n_students),   -- grade-aligned blocks, G1..G6
       q.section_from  + (((q.n - 1) * 3) / q.n_students),    -- SEC-A..SEC-C
       'STD' || lpad(q.school_id::text, 2, '0') || lpad((q.student_from + q.n - 1)::text, 7, '0'),
       'Student' || (q.student_from + q.n - 1),
       'Verma' || ((q.student_from + q.n) % 200),
       CASE WHEN (q.student_from + q.n) % 19 = 0 THEN '0' ELSE '1' END,   -- ~5% inactive roll
       CASE WHEN q.n % 2 = 0 THEN 'FEMALE' ELSE 'MALE' END,
       DATE '2009-01-05' + (((q.student_from + q.n) * 11) % 1800),
       'student' || (q.student_from + q.n - 1) || '@pupil.school' || q.school_id || '.example',
       '7' || lpad(((q.student_from + q.n) * 101)::text, 9, '0'),
       now() - make_interval(days => ((q.student_from + q.n) % 900) + 30), now(), 0
FROM seq q;

-- ── 9. parent_student (3,000 = 2 children per parent) ──────────────────────
--     Placed here because invoices and the parent users below read it.
--     The parent count did not scale with the roll (1,500 for 101,800 pupils), so this
--     links 3,000 pupils: 1,200 in school 1, 900 in school 2, 600 and 300 in the small
--     tenants. The link stays inside one tenant because the offset is taken modulo that
--     school's own n_students, and it stays one-parent-per-child because 2 * n_parents
--     (1,200 at most) is far below n_students (50,000 in the volume tenants), so the
--     residues 2i and 2i+1 never wrap onto each other. Same property the 2,400-student
--     shape had; the wider block only makes the wrap margin bigger. The visible
--     consequence is in invoices: 98% of the volume tenants' pupils have no parent link,
--     so their invoice and receipt rows carry a NULL parent_id -- nullable in both
--     tables, and true to a roll that grew faster than its parent roster.
--     Hibernate writes this join table from @ManyToMany, so it cannot write a third
--     column (V6:76-97); the BEFORE trigger trg_parent_student_tenant_key (V6:115-117)
--     derives and validates school_id. It is supplied here as the student's own school,
--     which the trigger accepts, and V5's fk_parent_student_student_same_school (V5:59)
--     needs the same pair to agree.
WITH link AS (
  SELECT p.id AS parent_id,
         ps.student_from + (((p.id - ps.parent_from) * 2 + (k.n - 1)) % ps.n_students) AS student_id
  FROM parents p
  JOIN perf_school ps ON p.id >= ps.parent_from AND p.id < ps.parent_from + ps.n_parents
  CROSS JOIN generate_series(1, 2) AS k(n)
)
INSERT INTO parent_student (parent_id, student_id, school_id)
SELECT l.parent_id, st.id, st.school_id
FROM link l
JOIN students st ON st.id = l.student_id;

-- ── 10. timetables (48 = 24 classrooms x 2 academic years) ─────────────────
--     V3 replaced the global UNIQUE(classroom_id) with uq_timetable_classroom_year,
--     which is what makes a second academic year possible at all. school_id became
--     NOT NULL in V6:65-74.
INSERT INTO timetables (id, school_id, classroom_id, academic_year, created_on, updated_on, version)
SELECT (c.id - 1) * 2 + y.n,
       c.school_id,
       c.id,
       CASE WHEN y.n = 1 THEN '2025-2026' ELSE '2024-2025' END,
       now() - interval '120 days', now(), 0
FROM classrooms c, generate_series(1, 2) AS y(n);

-- ── 11. teacher_assignments (288 = 24 classrooms x 6 subjects x 2 years) ───
--     V5 keys teacher_id to the employee's school; V6 adds the same check for subject
--     and classroom (V6:139-142), so all three must name one tenant or the row cannot
--     be written. Teachers are drawn from the school's teaching prefix of its block.
INSERT INTO teacher_assignments (id, school_id, teacher_id, subject_id, classroom_id, academic_year,
                                 created_on, updated_on, version)
SELECT row_number() OVER (ORDER BY c.school_id, c.id, sub.n, y.n),
       c.school_id,
       ps.emp_from + (((c.id - ps.classroom_from) + sub.n - 1) % ps.n_teaching),
       ps.subject_from + sub.n - 1,
       c.id,
       CASE WHEN y.n = 1 THEN '2025-2026' ELSE '2024-2025' END,
       now() - interval '120 days', now(), 0
FROM classrooms c
JOIN perf_school ps ON ps.school_id = c.school_id
CROSS JOIN generate_series(1, 6) AS sub(n)
CROSS JOIN generate_series(1, 2) AS y(n);

-- ── 12. timetable_slots (1,440 = 48 timetables x 5 days x 6 periods) ───────
--     day_of_week is varchar(10) and the entity comment says MONDAY..FRIDAY, not 'MON'
--     (TimetableSlot.java:28-29). uq_slot_day_period (V3) forbids two slots on the same
--     timetable/day/period, so the period is taken from the assignment's subject index,
--     which is unique per classroom-year. V6 adds fk_slot_timetable_same_school.
INSERT INTO timetable_slots (id, school_id, timetable_id, assignment_id, day_of_week, period,
                             created_on, updated_on, version)
SELECT row_number() OVER (ORDER BY tt.id, ta.id, d.n),
       tt.school_id,
       tt.id,
       ta.id,
       (ARRAY['MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY'])[d.n],
       (ta.subject_id - ((tt.school_id - 1) * 6)),    -- 1..6, unique per timetable
       now() - interval '100 days', now(), 0
FROM timetables tt
JOIN teacher_assignments ta
  ON ta.classroom_id  = tt.classroom_id
 AND ta.academic_year = tt.academic_year
 AND ta.school_id     = tt.school_id
CROSS JOIN generate_series(1, 5) AS d(n);

-- ── 13. attendances (203,600 = 2 per student) ──────────────────────────────
--     Two composite keys must agree at once: fk_attendance_student_same_school (V5:25)
--     and fk_attendance_slot_same_school (V6:149). The slot is picked inside the
--     student's own classroom's current-year timetable, so both are satisfied by
--     construction. status is varchar(10): PRESENT / ABSENT / LATE.
--     uq_attendance_student_slot_date (V3) needs a distinct (student, slot, date) triple;
--     the two rows per student use different days AND different dates.
--     The fan-out is exactly two rows per student at any size: every classroom has one
--     2025-2026 timetable, every timetable has a slot for all six periods on both
--     MONDAY and TUESDAY, and period = (st.id % 6) + 1 lands inside 1..6 whichever block
--     the student id comes from. So 2 x 101,800 = 203,600, and the row_number() over
--     (st.id, d.n) that gives them ids runs 1..203,600 contiguously.
INSERT INTO attendances (id, school_id, student_id, timetable_slot_id, date, status, remarks,
                         created_on, updated_on, version)
SELECT row_number() OVER (ORDER BY st.id, d.n),
       st.school_id,
       st.id,
       ts.id,
       DATE '2026-01-05' + (d.n - 1),
       CASE WHEN (st.id * 7 + d.n) % 11 = 0 THEN 'LATE'
            WHEN (st.id * 7 + d.n) % 5  = 0 THEN 'ABSENT'
            ELSE 'PRESENT' END,
       CASE WHEN (st.id * 7 + d.n) % 5 = 0 THEN 'Marked absent by class teacher' ELSE NULL END,
       now() - interval '20 days', now(), 0
FROM students st
JOIN timetables tt
  ON tt.classroom_id  = st.classroom_id
 AND tt.academic_year = '2025-2026'
 AND tt.school_id     = st.school_id
CROSS JOIN generate_series(1, 2) AS d(n)
JOIN timetable_slots ts
  ON ts.timetable_id = tt.id
 AND ts.school_id    = st.school_id
 AND ts.period       = ((st.id % 6) + 1)
 AND ts.day_of_week  = (ARRAY['MONDAY','TUESDAY'])[d.n];

-- ── 14. exams (24) and exam_subjects (144) ─────────────────────────────────
INSERT INTO exams (id, school_id, name, exam_type, academic_year, status, max_marks, passing_marks,
                   exam_date, duration_minutes, description,
                   created_at, created_on, updated_at, updated_on, version)
SELECT s.exam_from + e.n - 1,
       s.school_id,
       'Terminal ' || e.n,
       CASE WHEN e.n <= 3 THEN 'MIDTERM' WHEN e.n = 4 THEN 'UNIT_TEST' ELSE 'FINAL' END,
       '2025-2026',
       CASE WHEN e.n = 6 THEN '0' ELSE '1' END,
       100, 35,
       DATE '2026-01-08' + (e.n * 9),
       90 + (e.n % 4) * 30,
       'Exam ' || e.n || ' for school ' || s.school_id,
       now() - interval '60 days', now(), now(), now(), 0
FROM perf_school s, generate_series(1, 6) AS e(n);

--     Both V6 composite keys apply here: exam and subject must be the SAME school.
INSERT INTO exam_subjects (id, school_id, exam_id, subject_id, status, max_marks, passing_marks,
                           exam_date, start_time, end_time, created_on, updated_on, version)
SELECT s.exam_subject_from + (e.n - 1) * 6 + sub.n - 1,
       s.school_id,
       s.exam_from + e.n - 1,
       s.subject_from + sub.n - 1,
       '1', 100, 35,
       DATE '2026-01-08' + (e.n * 9),
       '09:00', '12:00',
       now() - interval '60 days', now(), 0
FROM perf_school s, generate_series(1, 6) AS e(n), generate_series(1, 6) AS sub(n);

-- ── 15. student_exams (203,600 = 2 per student) and results (101,800) ──────
--     Two rows per pupil on two distinct exam_subjects of its own school: the id is
--     row_number() over (st.id, k), so se.id = 2*(st.id - 1) + k with k in {1,2} --
--     contiguous 1..203,600, and the odd/even split is exactly one attempt each.
--     (st.id % 30) + k picks offsets 1..31 inside the school's own 36 exam_subjects, so
--     both attempts are valid rows and V3's one-result-per-attempt key has work to do.
INSERT INTO student_exams (id, school_id, student_id, exam_subject_id, status, registration_date,
                           created_on, updated_on, version)
SELECT row_number() OVER (ORDER BY st.id, k.n),
       st.school_id,
       st.id,
       (st.school_id - 1) * 36 + ((st.id % 30) + k.n),   -- two distinct exam_subjects, same school
       '1',
       DATE '2026-01-02',
       now() - interval '40 days', now(), 0
FROM students st, generate_series(1, 2) AS k(n);

--     results is keyed to student_exams twice over: uq_result_student_exam (V3) means one
--     result per attempt, and fk_result_student_exam_same_school (V5:33) the tenant check,
--     which V6:40 had to backfill because the column did not exist in V1.
--     The WHERE clause below is se.id % 2 = 0 over a CONTIGUOUS 1..203,600 row_number, so
--     it selects exactly half -- 101,800 results, one per pupil (the k=2 attempt of each).
--     Nothing about that depends on the old 12,000-row size; it depends on the numbering
--     staying contiguous, which is why the harness re-evaluates the predicate instead of
--     trusting a constant.
INSERT INTO results (id, school_id, student_exam_id, obtained_marks, percentage, grade, status,
                     remarks, created_on, updated_on, version)
SELECT se.id, se.school_id, se.id,
       40 + (se.id % 56),
       round((40 + (se.id % 56))::numeric, 2),
       CASE WHEN se.id % 56 < 10 THEN 'A' WHEN se.id % 56 < 25 THEN 'B'
            WHEN se.id % 56 < 40 THEN 'C' ELSE 'D' END,
       '1', NULL,
       now() - interval '10 days', now(), 0
FROM student_exams se
WHERE se.id % 2 = 0;

-- ── 16. fees (144 = 6 fee_types x 6 class_grades, per school) ──────────────
--     NOTE: fees has NO `name` column (V1:429-446) -- the draft seeded one. The label
--     column is `description`. fees.status is a WORD here, and uq_fee_school_type_year_grade_category
--     (V3) makes (fee_type, academic_year, class_grade, student_category) unique in the school.
INSERT INTO fees (id, school_id, fee_type, academic_year, class_grade, student_category,
                  description, amount, due_date, status, is_mandatory,
                  created_on, updated_on, version)
SELECT s.fee_from + (g.n - 1) * 6 + t.n - 1,
       s.school_id,
       (ARRAY['TUITION','TRANSPORT','EXAM','LIBRARY','LABOUR','ACTIVITY'])[t.n],
       '2025-2026',
       'G' || g.n,
       'GENERAL',
       'Fee ' || t.n || ' for grade G' || g.n,
       (500 + t.n * 250 + g.n * 100)::numeric(10,2) + round((random() * 50)::numeric, 2),
       DATE '2026-06-30' - (g.n * 7),
       CASE WHEN t.n = 6 AND g.n = 6 THEN 'ARCHIVED' ELSE 'ACTIVE' END,
       (t.n <= 3),
       now() - interval '150 days', now(), 0
FROM perf_school s, generate_series(1, 6) AS g(n), generate_series(1, 6) AS t(n);

-- ── 17. fee_structures (48 = 12 per school) ────────────────────────────────
--     The financial module reads these per school; no FK children. Included so
--     idx_fee_structure_school has real rows behind it.
INSERT INTO fee_structures (id, school_id, name, fee_category, academic_year, class_grade,
                            student_category, amount, description, effective_from, effective_to,
                            is_active, is_mandatory, created_on, updated_on, version)
SELECT s.fee_structure_from + n.n - 1,
       s.school_id,
       'Structure ' || n.n,
       (ARRAY['ACADEMIC','ACADEMIC','TRANSPORT','EXAMINATION','ACTIVITY','LIBRARY',
              'LABORATORY','SPORTS','HOSTEL','CAUTION_DEPOSIT','ANNUAL','MISC'])[n.n],
       CASE WHEN n.n <= 6 THEN '2025-2026' ELSE '2024-2025' END,
       'G' || (((n.n - 1) % 6) + 1),
       CASE WHEN n.n % 4 = 0 THEN 'SCHOLARSHIP' ELSE 'GENERAL' END,
       (1000 + n.n * 300)::numeric(10,2),
       'Fee structure line ' || n.n,
       DATE '2025-04-01', DATE '2026-03-31',
       (n.n <= 9), (n.n <= 6),
       now() - interval '200 days', now(), 0
FROM perf_school s, generate_series(1, 12) AS n(n);

-- ── 18. student_fees (509,000 = 5 per student) ─────────────────────────────
--     The big table. uq_student_fee (student_id, fee_id) is why a student cannot hold the
--     same fee twice: five DIFFERENT fee types for the student's own grade. V6's
--     fk_student_fee_fee_same_school requires (fee_id, school_id) to name one tenant, so
--     the fee id is derived from the school the student already belongs to.
--     The id shape matters downstream: row_number() over (student_id, k) with five rows
--     per pupil over a contiguous roll gives sf.id = 5*(student_id - 1) + k, k in 1..5.
--     That is why section 19's `sf.id % 2 = 1` selects 254,500 of the 509,000 rows (odd
--     id <=> student_id and k have the same parity: three matching k values for an odd
--     pupil, two for an even one, and 50,900 pupils of each kind in this roll), and why
--     the % 5 = 1 cut below removes a clean 20% of those rather than a lopsided amount --
--     101,800 is a multiple of 10, so the untouched pupils split evenly between the odd
--     and even halves (10,180 each).
WITH need AS (
  SELECT st.id AS student_id, st.school_id,
         replace(cl.grade, 'G', '')::int AS grade_idx,  -- classroom grade, 1..6
         k.n AS k
  FROM students st
  JOIN classrooms cl ON cl.id = st.classroom_id
  CROSS JOIN generate_series(1, 5) AS k(n)
), calc AS (
  SELECT q.student_id, q.school_id, q.k, f.id AS fee_id, f.amount, f.due_date,
         CASE WHEN q.student_id % 5 = 1 THEN 0                       -- ~20% untouched
              WHEN q.student_id % 3 = 0 THEN f.amount                -- settled
              ELSE round(f.amount * 0.6, 2) END          AS paid      -- part-paid
  FROM need q
  JOIN fees f ON f.id = (q.school_id - 1) * 36 + (q.grade_idx - 1) * 6 + q.k
)
INSERT INTO student_fees (id, school_id, student_id, fee_id, total_amount, amount_paid,
                          balance_amount, fine_amount, waiver_amount, due_date, last_payment_date,
                          status, created_on, updated_on, version)
SELECT row_number() OVER (ORDER BY c.student_id, c.k),
       c.school_id, c.student_id, c.fee_id,
       c.amount, c.paid, round(c.amount - c.paid, 2),
       CASE WHEN c.paid = 0 AND c.student_id % 17 = 0 THEN 100.00 ELSE 0.00 END,
       0.00, c.due_date,
       CASE WHEN c.paid > 0 THEN DATE '2026-05-11' ELSE NULL END,
       CASE WHEN c.paid = 0            THEN (CASE WHEN c.student_id % 13 = 0 THEN 'OVERDUE' ELSE 'PENDING' END)
            WHEN c.paid >= c.amount    THEN 'PAID'
            ELSE 'PARTIAL' END,
       now() - interval '90 days', now(), 0
FROM calc c;

-- ── 19. fee_payments (203,600 = one per settled student_fee with a payment) ─
--     Rows are read back out of student_fees, so fk_fee_payment_student_fee_same_school
--     (V5:35) holds by construction rather than by arithmetic. status SUCCESS/CANCELLED
--     (FeePaymentServiceImpl:65,115). payment_reference has a PARTIAL unique index (V3),
--     so a cancelled payment leaves it NULL instead of duplicating a sentinel.
--     The count is the WHERE clause, not a constant: 254,500 odd-id rows minus the 50,900
--     whose pupil holds nothing (sf.id % 2 = 1 and paid > 0) = 203,600, which equals 2 x
--     the roll only because 101,800 is a multiple of 10 (see section 18).
--     The CANCELLED cut is sf.id % 40 = 1. Every such id is odd, so the predicate is
--     entirely inside the inserted set: id = 1 + 40j, j = 0..12724, is 12,725 candidates
--     up to 509,000, and the ones whose pupil is unpaid are those with j = 0 mod 5
--     (id = 1+40j puts k at 1 and the pupil at 8j+1, so 8j+1 % 5 = 1 <=> j % 5 = 0) --
--     2,545 of them. 12,725 - 2,545 = 10,180 CANCELLED, 193,420 SUCCESS, and section 21
--     turns exactly the SUCCESS rows into receipts.
INSERT INTO fee_payments (id, school_id, student_fee_id, amount, payment_date, payment_method,
                          status, transaction_id, payment_reference, bank_name, cheque_number,
                          remarks, created_on, updated_on, version)
SELECT sf.id, sf.school_id, sf.id, sf.amount_paid,
       DATE '2026-05-11',
       (ARRAY['ONLINE','CASH','CARD','UPI','CHEQUE'])[1 + (sf.id % 5)],
       CASE WHEN sf.id % 40 = 1 THEN 'CANCELLED' ELSE 'SUCCESS' END,
       'TXN' || lpad(sf.id::text, 9, '0'),
       CASE WHEN sf.id % 40 = 1 THEN NULL ELSE 'REF' || lpad(sf.id::text, 9, '0') END,
       CASE WHEN sf.id % 5 = 1 THEN 'Bank of Baroda' ELSE NULL END,
       CASE WHEN sf.id % 5 = 4 THEN 'CHQ' || sf.id ELSE NULL END,
       NULL,
       now() - interval '30 days', now(), 0
FROM student_fees sf
WHERE sf.id % 2 = 1 AND sf.amount_paid > 0;

-- ── 20. invoices (101,800 = one per student) ───────────────────────────────
--     invoice_number is unique PER SCHOOL now (V3 dropped the global key), invoice.id is
--     deliberately equal to student.id because both are row_number() over students ordered
--     by id, and V5's fk_invoice_student_same_school needs student_id + school_id to agree.
--     NOTE: invoices.student_id is nullable and the services never set it -- V6:152-156
--     records that this key is therefore vacuous for application rows (FIN-04). The seed
--     populates it so the tenant key is actually measurable.
INSERT INTO invoices (id, school_id, invoice_number, invoice_date, due_date, invoice_type,
                      total_amount, amount_paid, balance_amount, discount_amount, tax_amount,
                      status, student_id, parent_id, billing_address, notes,
                      created_on, updated_on, version)
SELECT st.id, st.school_id,
       'INV' || lpad(st.id::text, 7, '0'),
       DATE '2026-04-01' + (st.id % 20),
       DATE '2026-04-30' + (st.id % 20),
       'FEE',
       tot.total_amount, 0, tot.total_amount, 0, 0,
       CASE WHEN st.id % 23 = 0 THEN 'CANCELLED'
            WHEN st.id % 13 = 0 THEN 'OVERDUE'
            WHEN st.id %  4 = 0 THEN 'PAID'
            ELSE 'PENDING' END,
       st.id,
       link.parent_id,
       'Address line ' || (st.id % 400),
       NULL,
       now() - interval '45 days', now(), 0
FROM students st
LEFT JOIN LATERAL (SELECT ps.parent_id
                   FROM parent_student ps
                   WHERE ps.student_id = st.id
                   ORDER BY ps.parent_id
                   LIMIT 1) link ON true
JOIN LATERAL (SELECT sum(sf.total_amount) AS total_amount
              FROM student_fees sf
              WHERE sf.student_id = st.id) tot ON true;

-- ── 21. receipts (193,420 = one per successful fee payment) ────────────────
--     receipt_number unique per school (V3); invoice_id, fee_payment_id and parent_id are
--     plain FKs and only student_id is tenant-keyed (V5:43).
--     The WHERE clause is section 19's SUCCESS arm, so this is 203,600 payments minus the
--     10,180 cancelled = 193,420 rows, each keyed to a payment id that is also a
--     student_fee id (both are sf.id). The receipts' own status column cancels a further
--     slice with fp.id % 29 = 0 -- that is a status split, not a row filter, so it moves
--     no count. Neither JOIN drops a row: every payment's pupil has an invoice, because
--     section 20 writes one per student.
INSERT INTO receipts (id, school_id, receipt_number, receipt_date, amount, payment_method,
                      status, student_id, parent_id, invoice_id, fee_payment_id,
                      reference_number, received_by, description,
                      created_on, updated_on, version)
SELECT fp.id, sf.school_id,
       'RCT' || lpad(fp.id::text, 7, '0'),
       fp.payment_date, fp.amount, fp.payment_method,
       CASE WHEN fp.id % 29 = 0 THEN 'CANCELLED' ELSE 'ISSUED' END,
       sf.student_id, inv.parent_id, inv.id, fp.id,
       fp.payment_reference,
       'Counter clerk ' || (fp.id % 9),
       'Receipt for fee instalment',
       now() - interval '25 days', now(), 0
FROM fee_payments fp
JOIN student_fees sf ON sf.id = fp.student_fee_id
JOIN invoices inv    ON inv.id = sf.student_id AND inv.school_id = sf.school_id
WHERE fp.status = 'SUCCESS';

-- ── 22. users (103,480 = one per employee, one per student, one per parent) ─
--     Three disjoint id blocks in an `integer` column, and the disjointness is now a
--     matter of arithmetic rather than of luck:
--       * employees: 1..180, the employee's own id. It stays at the bottom of the range
--         because section 24's role split tests u.id % 9 on these rows -- move the block
--         and the ACCOUNTANT/ADMIN counts move with it, silently.
--       * students: 1000 + student.id, so 1001..102800. The 1,000 offset clears the 180
--         employee ids by 820 and reads as "a pupil's user row is its pupil id plus a
--         thousand". It is also what makes notifications' recipient_ids '1001,1002' below
--         name two real student accounts.
--       * parents: 200000 + parent.id, so 200001..201500. This block MOVED, and it had
--         to: the roll went from 6,000 to 101,800 pupils, so the student block now runs
--         to 102,800 and the old 20001..21500 range lay inside it -- the third INSERT
--         below would have collided with pupil 19,001's user row, whose id is exactly
--         1,000 + 19,001 = 20,001, and hit users_pkey, aborting the whole load. 200,000
--         sits above the tallest possible student user with 97,200 ids of headroom, so
--         the roll may triple before the blocks touch again. If SUM(n_students) ever
--         passes 199,000 this offset moves with it; the guard in section 28 refuses to
--         commit a load in which a student user reached 200,000, so the collision cannot
--         come back as a silent PK error.
--     Only users.id was re-based. The identity strings keep the row's own id
--     ('par' || p.id, 'PU' || lpad(p.id, 6)), so no username, email or user_id value
--     changed shape at the new scale, and `integer` is nowhere near its ceiling.
--     username is UNIQUE globally; (school_id, email) and (school_id, user_id) are UNIQUE
--     per school (V3); employee_id / student_id / parent_id are each UNIQUE. user_status is
--     the '1'/'0' CODE (User.java:77-78), and idx_users_school_status (V4:19) is the index
--     the tenant reads want. The bcrypt-shaped string is a PLACEHOLDER, not a credential.
--     Block 1: one account per employee, id = the employee's own id (1..180).
INSERT INTO users (id, school_id, username, email, user_id, full_name, password, phone_number,
                   user_status, enabled, account_locked, email_verified, phone_verified,
                   password_expired, require_password_change, login_attempts, last_login_at,
                   employee_id, student_id, parent_id, created_at, updated_at, version)
SELECT e.id, e.school_id,
       'emp' || e.id, 'empuser' || e.id || '@school' || e.school_id || '.example',
       'EU' || lpad(e.id::text, 6, '0'), e.first_name || ' ' || e.last_name,
       '$2a$10$00000000000000000000000000000000000000000000000000000', e.phone_number,
       e.employee_status, e.enabled, false, true, false, false, false, e.id % 3,
       CASE WHEN e.id % 4 = 0 THEN now() - interval '2 days' ELSE NULL END,
       e.id, NULL, NULL,
       now() - interval '400 days', now(), 0
FROM employees e;

--     Block 2: one account per pupil, ids 1001..102800 = 1000 + student.id. This is the
--     block whose growth moved everything else: it is 101,800 rows wide now, not 6,000,
--     which is what pushed block 3 out to 200,000.
INSERT INTO users (id, school_id, username, email, user_id, full_name, password, phone_number,
                   user_status, enabled, account_locked, email_verified, phone_verified,
                   password_expired, require_password_change, login_attempts,
                   employee_id, student_id, parent_id, created_at, updated_at, version)
SELECT 1000 + st.id, st.school_id,
       'stu' || st.id, 'stuser' || st.id || '@school' || st.school_id || '.example',
       'SU' || lpad(st.id::text, 6, '0'), st.first_name || ' ' || st.last_name,
       '$2a$10$00000000000000000000000000000000000000000000000000000', st.phone_number,
       st.status, (st.status = '1'), false, true, false, false, false, 0,
       NULL, st.id, NULL,
       now() - interval '350 days', now(), 0
FROM students st;

--     A parent is global but a user is not, so a parent account is anchored to the school of
--     its first child; MIN/order is stable because parent_student is keyed by (parent, student).
--     ids 200001..201500, i.e. 200000 + parent.id -- the offset that used to be 20000 and
--     moved for the reason given at the head of this section.
INSERT INTO users (id, school_id, username, email, user_id, full_name, password, phone_number,
                   user_status, enabled, account_locked, email_verified, phone_verified,
                   password_expired, require_password_change, login_attempts,
                   employee_id, student_id, parent_id, created_at, updated_at, version)
SELECT 200000 + p.id, child.school_id,
       'par' || p.id, 'paruser' || p.id || '@school' || child.school_id || '.example',
       'PU' || lpad(p.id::text, 6, '0'), p.first_name || ' ' || p.last_name,
       '$2a$10$00000000000000000000000000000000000000000000000000000', p.phone_number,
       '1', true, false, true, false, false, false, 0,
       NULL, NULL, p.id,
       now() - interval '300 days', now(), 0
FROM parents p
JOIN LATERAL (SELECT st2.school_id
              FROM parent_student ps
              JOIN students st2 ON st2.id = ps.student_id
              WHERE ps.parent_id = p.id
              ORDER BY ps.student_id
              LIMIT 1) child ON true;

-- ── 23. Close the circular parent <-> user pair ────────────────────────────
UPDATE parents p
SET    user_id = u.id, updated_at = now()
FROM   users u
WHERE  u.parent_id = p.id;

-- ── 24. user_roles (103,480) — roles are a collection table, not a column ──
--     User.java:48-51 maps Set<String> roles onto user_roles(user_id, role); V3 adds
--     uq_user_roles_user_role because the @ElementCollection table had no key at all.
--     The vocabulary is stored WITHOUT the ROLE_ prefix (RoleEnum.java:9-12,
--     SecurityConfig.java:119-133; AuthController.java:88 strips ROLE_ on the way in).
--     One row per user, so the count followed users: 103,480.
--     The split the CASE below produces, stated because a probe reads it and a reader
--     should not have to derive it: TEACHER 135 (the is_teaching employees), STUDENT
--     101,800 (one per pupil, at the roll's new size), PARENT 1,500, and of the 45
--     non-teaching employees ACCOUNTANT where u.id % 9 = 0 -- employee user ids 54, 99,
--     108, 144 and 180, so 5 -- and ADMIN for the other 40.
--     None of those staff numbers moved with the roll, and that is not an accident of the
--     CASE: the employee arm tests u.id % 9, and section 22 left employee user ids at
--     1..180 precisely so this split would keep meaning what it used to. Move block 1 and
--     the ACCOUNTANT/ADMIN counts move with it.
--     NOTE: user_roles carries no school_id, so V6's Rule I-1 does not cover it -- the
--     only tenant path into it is users.school_id -> user_roles by user_id.
INSERT INTO user_roles (user_id, role)
SELECT u.id,
       CASE WHEN u.employee_id IS NOT NULL THEN
              CASE WHEN e.is_teaching THEN 'TEACHER'
                   WHEN u.id % 9 = 0 THEN 'ACCOUNTANT'
                   ELSE 'ADMIN' END
            WHEN u.student_id IS NOT NULL THEN 'STUDENT'
            ELSE 'PARENT' END
FROM users u
LEFT JOIN employees e ON e.id = u.employee_id;

-- ── 25. notifications (4,000) and announcements (4,000) ────────────────────
--     Both read newest-first and V4 indexes them on (school_id, created_on DESC) and
--     (school_id, is_published, publish_date DESC), so those timestamps are populated.
--     1,000 per school is the tenant's own feed volume, not a function of its pupil
--     count, so these two did not move with the roll. The INDIVIDUAL rows' recipient_ids
--     '1001,1002' still name two real accounts: student users begin at 1001 (section 22,
--     block 2), and that offset was kept for exactly that reason.
INSERT INTO notifications (id, school_id, title, message, channel, notification_type,
                           recipient_type, recipient_ids, delivery_status, read_status,
                           priority, is_important, scheduled_at, sent_at, created_on, updated_on, version)
SELECT s.notice_from + n.n - 1, s.school_id,
       'Notice ' || n.n, 'Body of notification ' || n.n,
       (ARRAY['IN_APP','IN_APP','EMAIL','SMS'])[1 + (n.n % 4)],
       (ARRAY['ACADEMIC','FINANCIAL','EVENT','GENERAL','ATTENDANCE','HOLIDAY'])[1 + (n.n % 6)],
       CASE WHEN n.n % 3 = 0 THEN 'INDIVIDUAL' ELSE 'ALL' END,
       CASE WHEN n.n % 3 = 0 THEN '1001,1002' ELSE NULL END,
       (ARRAY['PENDING','SENT','SENT','FAILED'])[1 + (n.n % 4)],
       (ARRAY['UNREAD','READ','UNREAD'])[1 + (n.n % 3)],
       (ARRAY['LOW','MEDIUM','HIGH'])[1 + (n.n % 3)],
       (n.n % 17 = 0),
       CASE WHEN n.n % 4 = 0 THEN now() + interval '2 days' ELSE NULL END,
       CASE WHEN n.n % 4 = 0 THEN NULL ELSE now() - make_interval(days => n.n % 40) END,
       now() - make_interval(days => n.n % 60), now(), 0
FROM perf_school s, generate_series(1, 1000) AS n(n);

INSERT INTO announcements (id, school_id, title, content, announcement_type, target_audience,
                           category, priority, is_pinned, is_published, publish_date, published_at,
                           expiry_date, view_count, created_on, updated_on, version)
SELECT s.notice_from + n.n - 1, s.school_id,
       'Announcement ' || n.n, 'Content of announcement ' || n.n,
       (ARRAY['GENERAL','ACADEMIC','EVENT','HOLIDAY','EXAM','RESULT','URGENT'])[1 + (n.n % 7)],
       (ARRAY['ALL','STUDENTS','PARENTS','EMPLOYEES','TEACHERS'])[1 + (n.n % 5)],
       'INFO', (ARRAY['LOW','MEDIUM','HIGH'])[1 + (n.n % 3)],
       (n.n % 25 = 0), (n.n % 20 <> 0),
       now() - make_interval(days => n.n % 90),
       now() - make_interval(days => n.n % 90),
       now() + make_interval(days => (n.n % 30) + 1),
       n.n * 3,
       now() - make_interval(days => n.n % 90), now(), 0
FROM perf_school s, generate_series(1, 1000) AS n(n);

-- ── 26. Resync identity sequences past the explicit ids ────────────────────
--     Every id column in this schema is GENERATED BY DEFAULT AS IDENTITY (e.g. V1:1054 for
--     students, V1:447 for fees), so the application's next INSERT takes the next sequence
--     value; without this it would collide with a seeded id. The loop covers exactly the
--     tables that have one -- user_roles (V1:1167) and parent_student (V1:677) do not.
--     The values that now matter are students at 101,800, student_fees at 509,000 (its
--     last row is pupil 101,800's fee 5), fee_payments and receipts at 508,999, and users
--     at 201,500 -- the top of the PARENT block, not of the student block. Taking max(id)
--     rather than a per-table constant is what keeps that three-block layout safe:
--     whichever block ends up tallest is the one the application has to clear.
DO $$
DECLARE r record;
BEGIN
  FOR r IN
    SELECT n.nspname AS sch, c.relname AS tbl
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    JOIN pg_attribute a ON a.attrelid = c.oid AND a.attname = 'id'
    WHERE n.nspname = 'public' AND c.relkind = 'r' AND a.attidentity <> ''
  LOOP
    EXECUTE format('SELECT setval(pg_get_serial_sequence(%L, %L), COALESCE((SELECT max(id) FROM %I.%I), 1))',
                   r.sch || '.' || r.tbl, 'id', r.sch, r.tbl);
  END LOOP;
END $$;

-- ── 27. NEGATIVE TESTS: the V5/V6 tenant invariants must reject a mismatch ─
-- Caught inside the transaction, so they do not abort the load; they prove the constraints bite.

DO $$ BEGIN
  BEGIN
    -- Student 1 belongs to school 1, and school 1 already bills it fee 1..5. So the pair
    -- (student_id, fee_id) is left untouched and only the TENANT lies: the row claims to be
    -- school 2's, and names fee 37, which really is school 2's first fee. That makes
    -- fk_student_fee_student_same_school the constraint that refuses it, rather than
    -- uq_student_fee firing first on an already-seeded pair.
    -- The sentinel id 900001 is still clear of the seeded block, which now runs to 509,000
    -- (five fees per pupil across 101,800 pupils); a roll past 180,000 pupils moves it.
    INSERT INTO student_fees (id, school_id, student_id, fee_id, total_amount, amount_paid, status)
    VALUES (900001, 2, 1, 37, 100, 0, 'PENDING');
    RAISE WARNING 'NEGATIVE TEST (a) FAILED: cross-tenant student_fees row was accepted';
  EXCEPTION WHEN foreign_key_violation THEN
    RAISE NOTICE 'PASS (a) fk_student_fee_student_same_school rejected a cross-tenant child: %', SQLERRM;
  WHEN unique_violation THEN
    RAISE WARNING 'NEGATIVE TEST (a) INCONCLUSIVE: rejected as a duplicate pair, not as a tenant mismatch';
  END;
END $$;

DO $$ BEGIN
  BEGIN
    -- student 1 is in school 1; name school 2 on the parent link.
    INSERT INTO parent_student (parent_id, student_id, school_id) VALUES (1, 1, 2);
    RAISE WARNING 'NEGATIVE TEST (b) FAILED: cross-tenant parent_student link was accepted';
  EXCEPTION WHEN raise_exception THEN
    RAISE NOTICE 'PASS (b) trg_parent_student_tenant_key rejected a cross-tenant link: %', SQLERRM;
  END;
END $$;

-- ── 28. Seed integrity: no child row may name a school its parent does not ─
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM students st JOIN classrooms c ON c.id = st.classroom_id WHERE c.school_id <> st.school_id)
    THEN RAISE EXCEPTION 'seed integrity: a student names another school''s classroom'; END IF;
  IF EXISTS (SELECT 1 FROM students st JOIN sections sec ON sec.id = st.section_id WHERE sec.school_id <> st.school_id)
    THEN RAISE EXCEPTION 'seed integrity: a student names another school''s section'; END IF;
  IF EXISTS (SELECT 1 FROM student_fees sf JOIN students st ON st.id = sf.student_id WHERE st.school_id <> sf.school_id)
    THEN RAISE EXCEPTION 'seed integrity: a student_fee disagrees with its student'; END IF;
  IF EXISTS (SELECT 1 FROM student_fees sf JOIN fees f ON f.id = sf.fee_id WHERE f.school_id <> sf.school_id)
    THEN RAISE EXCEPTION 'seed integrity: a student_fee disagrees with its fee'; END IF;
  IF EXISTS (SELECT 1 FROM attendances a JOIN students st ON st.id = a.student_id WHERE st.school_id <> a.school_id)
    THEN RAISE EXCEPTION 'seed integrity: an attendance disagrees with its student'; END IF;
  IF EXISTS (SELECT 1 FROM attendances a JOIN timetable_slots ts ON ts.id = a.timetable_slot_id WHERE ts.school_id <> a.school_id)
    THEN RAISE EXCEPTION 'seed integrity: an attendance disagrees with its slot'; END IF;
  IF EXISTS (SELECT 1 FROM parent_student ps JOIN students st ON st.id = ps.student_id WHERE st.school_id <> ps.school_id)
    THEN RAISE EXCEPTION 'seed integrity: a parent link disagrees with its student'; END IF;
  IF (SELECT count(*) FROM students) <> 101800
    THEN RAISE EXCEPTION 'seed integrity: expected 101800 students, got %', (SELECT count(*) FROM students); END IF;
  IF (SELECT count(*) FROM student_fees) <> 509000
    THEN RAISE EXCEPTION 'seed integrity: expected 509000 student_fees, got %', (SELECT count(*) FROM student_fees); END IF;
  IF (SELECT count(*) FROM attendances) <> 203600
    THEN RAISE EXCEPTION 'seed integrity: expected 203600 attendances, got %', (SELECT count(*) FROM attendances); END IF;
  IF (SELECT count(*) FROM users) <> 103480
    THEN RAISE EXCEPTION 'seed integrity: expected 103480 users (180 employees + 101800 pupils + 1500 parents), got %', (SELECT count(*) FROM users); END IF;
  -- NFR-01 is a PER-SCHOOL clause, so the totals above are not the promise; these two
  -- are. A dataset can hold a six-figure roll and still fail it by spreading it thin,
  -- which is how the previous shape read as a pass: 6,000 pupils over four tenants, 2,400
  -- in the largest, and only a cross-table sum tested.
  IF (SELECT count(*) FROM students WHERE school_id = 1) < 50000
    THEN RAISE EXCEPTION 'seed integrity: NFR-01 wants >= 50000 pupils PER SCHOOL, school 1 holds %', (SELECT count(*) FROM students WHERE school_id = 1); END IF;
  IF (SELECT count(*) FROM students WHERE school_id = 2) < 50000
    THEN RAISE EXCEPTION 'seed integrity: NFR-01 wants >= 50000 pupils PER SCHOOL, school 2 holds %', (SELECT count(*) FROM students WHERE school_id = 2); END IF;
  -- The three user id blocks must not touch (section 22): employee users end at 180,
  -- student users run 1001..102800, parent users start at 200000. A PK clash would abort
  -- the load either way; naming the block turns the symptom into the cause, and turns a
  -- future roll past 199,000 pupils from a crash into a sentence.
  IF (SELECT count(*) FROM users WHERE student_id IS NOT NULL AND (id < 1001 OR id >= 200000)) <> 0
    THEN RAISE EXCEPTION 'seed integrity: a student user left its 1001..199999 block'; END IF;
  IF (SELECT count(*) FROM users WHERE parent_id IS NOT NULL AND id < 200000) <> 0
    THEN RAISE EXCEPTION 'seed integrity: a parent user is inside the student id block'; END IF;
END $$;

COMMIT;

-- ── 29. Exact per-table counts (printed when this file runs) ───────────────
WITH counts AS (
  SELECT 'students'::text AS table_name, count(*) AS rows FROM students UNION ALL
  SELECT 'student_fees',            count(*) FROM student_fees UNION ALL
  SELECT 'attendances',             count(*) FROM attendances UNION ALL
  SELECT 'student_exams',           count(*) FROM student_exams UNION ALL
  SELECT 'results',                 count(*) FROM results UNION ALL
  SELECT 'fee_payments',            count(*) FROM fee_payments UNION ALL
  SELECT 'users',                   count(*) FROM users UNION ALL
  SELECT 'user_roles',              count(*) FROM user_roles UNION ALL
  SELECT 'parent_student',          count(*) FROM parent_student UNION ALL
  SELECT 'invoices',                count(*) FROM invoices UNION ALL
  SELECT 'receipts',                count(*) FROM receipts UNION ALL
  SELECT 'parents',                 count(*) FROM parents UNION ALL
  SELECT 'employees',               count(*) FROM employees UNION ALL
  SELECT 'notifications',           count(*) FROM notifications UNION ALL
  SELECT 'announcements',           count(*) FROM announcements UNION ALL
  SELECT 'timetable_slots',         count(*) FROM timetable_slots UNION ALL
  SELECT 'fees',                    count(*) FROM fees UNION ALL
  SELECT 'teacher_assignments',     count(*) FROM teacher_assignments UNION ALL
  SELECT 'exam_subjects',           count(*) FROM exam_subjects UNION ALL
  SELECT 'fee_structures',          count(*) FROM fee_structures UNION ALL
  SELECT 'timetables',              count(*) FROM timetables UNION ALL
  SELECT 'exams',                   count(*) FROM exams UNION ALL
  SELECT 'classrooms',              count(*) FROM classrooms UNION ALL
  SELECT 'subjects',                count(*) FROM subjects UNION ALL
  SELECT 'sections',                count(*) FROM sections UNION ALL
  SELECT 'academic_years',          count(*) FROM academic_years UNION ALL
  SELECT 'schools',                 count(*) FROM schools
)
SELECT table_name, rows FROM counts ORDER BY rows DESC;

-- The aggregate, which is the number that gets quoted; the per-school split printed below
-- it is the one that decides NFR-01, because that clause is per school.
WITH counts AS (
  SELECT count(*) AS rows FROM students UNION ALL SELECT count(*) FROM student_fees UNION ALL
  SELECT count(*) FROM attendances UNION ALL SELECT count(*) FROM student_exams UNION ALL
  SELECT count(*) FROM results UNION ALL SELECT count(*) FROM fee_payments UNION ALL
  SELECT count(*) FROM users UNION ALL SELECT count(*) FROM user_roles UNION ALL
  SELECT count(*) FROM parent_student UNION ALL SELECT count(*) FROM invoices UNION ALL
  SELECT count(*) FROM receipts UNION ALL SELECT count(*) FROM parents UNION ALL
  SELECT count(*) FROM employees UNION ALL SELECT count(*) FROM notifications UNION ALL
  SELECT count(*) FROM announcements UNION ALL SELECT count(*) FROM timetable_slots UNION ALL
  SELECT count(*) FROM fees UNION ALL SELECT count(*) FROM teacher_assignments UNION ALL
  SELECT count(*) FROM exam_subjects UNION ALL SELECT count(*) FROM fee_structures UNION ALL
  SELECT count(*) FROM timetables UNION ALL SELECT count(*) FROM exams UNION ALL
  SELECT count(*) FROM classrooms UNION ALL SELECT count(*) FROM subjects UNION ALL
  SELECT count(*) FROM sections UNION ALL SELECT count(*) FROM academic_years UNION ALL
  SELECT count(*) FROM schools
)
SELECT sum(rows) AS total_seed_rows, count(*) AS tables_seeded FROM counts;

-- The tenant split, which is what makes these probes a multi-tenant measurement:
SELECT st.school_id, count(*) AS students,
       count(*) FILTER (WHERE st.status = '0') AS inactive_students,
       count(DISTINCT st.classroom_id) AS classrooms
FROM students st GROUP BY st.school_id ORDER BY st.school_id;

-- ============================================================================
-- MEASUREMENT PROBES (run after `ANALYZE;`, each with EXPLAIN (ANALYZE, BUFFERS)
-- prefixed; schools 1 and 2 are the 50,000-pupil volume tenants NFR-01 names, so
-- probes P1..P6 measure a tenant the clause actually describes):
--
--  P1  deep page of the roster. OFFSET 40000 is 40,000 rows into school 1's 47,369
--      active pupils -- a real deep walk, not a page past the end, and the same
--      position verify-perf-50k.sh drives over HTTP (page 4000 at size 20). The old
--      OFFSET 40 was written when the tenant held 2,400 pupils; at 50,000 it stopped
--      being deep, so it is moved with the shape.
--      SELECT id, first_name, last_name, status FROM students
--       WHERE school_id = 1 AND status = '1' ORDER BY id LIMIT 20 OFFSET 40000;
--  P2  head-count
--      SELECT count(*) FROM students WHERE school_id = 1;
--  P3  fee ledger joined to the roll
--      SELECT sf.id, sf.status, sf.balance_amount FROM student_fees sf
--      JOIN students st ON st.id = sf.student_id
--       WHERE st.school_id = 1 AND st.status = '1' LIMIT 20;
--  P4  one student's attendance history
--      SELECT id, date, status FROM attendances
--       WHERE school_id = 1 AND student_id = 5 ORDER BY date DESC LIMIT 30;
--  P5  a tenant's users with their roles (roles live in user_roles, not on users)
--      SELECT u.id, u.username, ur.role FROM users u JOIN user_roles ur ON ur.user_id = u.id
--       WHERE u.school_id = 1 AND ur.role = 'TEACHER';
--  P6  the two dashboard fee sums, in both shapes, because comparing them is the point. The
--      `old` row is the statement as the JPQL wrote it before Stage 5 — tenant reached by joining
--      through the roll; the `new` row is what StudentFeeRepository now sends. They select the same
--      rows: `fk_student_fee_student_same_school` ties (student_id, school_id) to
--      students(id, school_id), so no tenant can leak through the shorter predicate. Run each shape
--      twice and read the second: the first run of the joined shape took 186.3 ms because 2,447 of
--      its 7,894 blocks came from disk, and the same statement warm took 88.9 ms.
--      old  SELECT SUM(sf.amount_paid) FROM student_fees sf JOIN students st ON st.id = sf.student_id
--            WHERE st.school_id = 1 AND sf.status IN ('PAID','PARTIAL');
--      new  SELECT SUM(sf.amount_paid) FROM student_fees sf
--            WHERE sf.school_id = 1 AND sf.status IN ('PAID','PARTIAL');
--      old  SELECT SUM(sf.balance_amount) FROM student_fees sf JOIN students st ON st.id = sf.student_id
--            WHERE st.school_id = 1 AND sf.status IN ('PENDING','PARTIAL','OVERDUE');
--      new  SELECT SUM(sf.balance_amount) FROM student_fees sf
--            WHERE sf.school_id = 1 AND sf.status IN ('PENDING','PARTIAL','OVERDUE');
-- ============================================================================
