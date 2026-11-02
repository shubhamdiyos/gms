#!/usr/bin/env bash
# Stage 2 schema verification (ARCHITECTURE.md §5, ENGINEERING_ROADMAP.md Stage 2 B-3).
#
# Applies the Flyway chain to a throwaway PostgreSQL 16 database in the Compose container and
# asserts the six things Stage 2 claims about it: determinism, the two index invariants, the
# cross-tenant write, a migration that must fail, and DB-07/DB-02 each in both directions. It is
# psql-level on purpose: the chain, the invariants and the adversarial inserts are checked in
# seconds with nothing but the container running, so a migration that breaks is caught before a
# JAR is built. The application boot against the migrated schema (`ddl-auto=validate`) is a
# separate gate and is not this script.
#
#   usage: scripts/verify-schema.sh [probe-db-name]
#   requires: `docker compose up -d postgres`, and a DB_PASSWORD the container already knows.
#
# Nothing here touches the `gms` development database, and nothing here deletes a volume.
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MIG="$REPO/gms_backend/src/main/resources/db/migration"
PROBE="${1:-gms_schema_probe}"
CONTAINER=gms-postgres

psql_q() { docker exec -i "$CONTAINER" psql -U gms_user -d "$1" -v ON_ERROR_STOP=1 -qtA "${@:2}"; }
psql_f() { docker exec -i "$CONTAINER" psql -U gms_user -d "$1" -v ON_ERROR_STOP="${2:-1}" -q; }

fail() { echo "FAIL: $*" >&2; exit 1; }
pass() { printf '  ok   %s\n' "$*"; }

echo "== 1. migrate from empty, twice, and compare"
for run in a b; do
  DB="$PROBE$run"
  docker exec "$CONTAINER" psql -U gms_user -d postgres -q \
    -c "DROP DATABASE IF EXISTS $DB;" -c "CREATE DATABASE $DB OWNER gms_user;" >/dev/null
  for f in "$MIG"/V*__*.sql; do
    psql_f "$DB" 1 < "$f" || fail "$(basename "$f") did not apply cleanly to an empty database"
  done
  # `\restrict` is a per-invocation nonce pg_dump 16.15 emits around the dump, not schema
  # content: without filtering it, two provably identical databases diff as different.
  docker exec "$CONTAINER" pg_dump -U gms_user -d "$DB" --schema-only --no-owner --no-privileges \
    | grep -vE '^--|^$|^\\' > "/tmp/schema_$run.sql"
done
if diff -q "/tmp/schema_a.sql" "/tmp/schema_b.sql" >/dev/null; then
  pass "two independent runs of the same chain produced identical schemas ($(wc -l < /tmp/schema_a.sql | tr -d ' ') lines)"
else
  fail "the chain is not deterministic; diff /tmp/schema_a.sql /tmp/schema_b.sql"
fi

echo "== 2. the invariants that the migrations assert about themselves"
TABLES=$(psql_q "${PROBE}a" -c "select count(*) from pg_tables where schemaname='public' and tablename not like 'flyway%';")
INDEXES=$(psql_q "${PROBE}a" -c "select count(*) from pg_indexes where schemaname='public' and indexname like 'idx%';")
UNIQUES=$(psql_q "${PROBE}a" -c "select count(*) from pg_constraint where contype='u' and conname like 'uq%';")
CFKS=$(psql_q "${PROBE}a" -c "select count(*) from pg_constraint where contype='f' and cardinality(conkey)>1;")
pass "tables=$TABLES secondary indexes=$INDEXES named uniques=$UNIQUES composite foreign keys=$CFKS"

I1=$(psql_q "${PROBE}a" -c "
SELECT string_agg(t.relname, ', ' ORDER BY t.relname)
FROM pg_attribute a JOIN pg_class t ON t.oid=a.attrelid JOIN pg_namespace n ON n.oid=t.relnamespace
WHERE n.nspname='public' AND t.relkind='r' AND a.attname='school_id' AND NOT a.attisdropped
  AND t.relname <> 'schools'
  AND NOT EXISTS (SELECT 1 FROM pg_index i WHERE i.indrelid=t.oid AND i.indkey[0]=a.attnum);")
[ -z "$I1" ] && pass "Rule I-1: every table carrying the tenant key has an index leading on it" \
            || fail "Rule I-1 violated by: $I1"

I2=$(psql_q "${PROBE}a" -c "
SELECT string_agg(c.conrelid::regclass::text || '.' || a.attname, ', ' ORDER BY 1)
FROM pg_constraint c
JOIN LATERAL unnest(c.conkey) WITH ORDINALITY AS k(attnum, ord) ON true
JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attnum=k.attnum
WHERE c.contype='f' AND k.ord=1 AND a.attname NOT IN ('created_by','updated_by')
  AND NOT EXISTS (SELECT 1 FROM pg_index i WHERE i.indrelid=c.conrelid AND i.indkey[0]=k.attnum);")
[ -z "$I2" ] && pass "Rule I-2: every navigated foreign key is indexed on its first column" \
            || fail "Rule I-2 violated by: $I2"

AUDIT=$(psql_q "${PROBE}a" -c "
SELECT count(*) FROM pg_constraint c
JOIN LATERAL unnest(c.conkey) WITH ORDINALITY AS k(attnum, ord) ON true
JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attnum=k.attnum
WHERE c.contype='f' AND k.ord=1 AND a.attname IN ('created_by','updated_by')
  AND NOT EXISTS (SELECT 1 FROM pg_index i WHERE i.indrelid=c.conrelid AND i.indkey[0]=k.attnum);")
pass "Rule I-2 exemption in force: $AUDIT audit-bookkeeping references (created_by/updated_by) left unindexed by design"

echo "== 3. the cross-tenant write, attempted"
# A row whose tenant disagrees with its parent's tenant must be refused by the database.
# Written as one transaction that is expected to abort, on a chain seeded in a second one.
docker exec -i "$CONTAINER" psql -U gms_user -d "${PROBE}a" -v ON_ERROR_STOP=1 -q <<'CHAIN' >/dev/null
INSERT INTO schools (id, school_code, school_name, enabled, status) VALUES (1,'SCH-A','School A',true,'ACTIVE');
INSERT INTO classrooms (id, school_id, name, grade, section, status) VALUES (10,1,'A-1','1','A','ACTIVE');
INSERT INTO students (id, school_id, student_id, first_name, last_name, status, classroom_id) VALUES (100,1,'A-001','Ann','Lee','ACTIVE',10);
INSERT INTO employees (id, school_id, first_name, last_name, email, employee_status, enabled, is_teaching) VALUES (20,1,'T','Eacher','t@sch-a.test','ACTIVE',true,true);
INSERT INTO subjects (id, school_id, code, name, status) VALUES (30,1,'MATH','Mathematics','ACTIVE');
INSERT INTO teacher_assignments (id, teacher_id, subject_id, classroom_id, academic_year) VALUES (40,20,30,10,'2026');
INSERT INTO timetables (id, classroom_id, academic_year) VALUES (50,10,'2026');
UPDATE timetables SET school_id = 1 WHERE id = 50;
INSERT INTO timetable_slots (id, timetable_id, assignment_id, day_of_week, period) VALUES (60,50,40,'MON',1);
UPDATE timetable_slots SET school_id = 1 WHERE id = 60;
CHAIN
pass "seeded one school with a student, a teacher, a timetable and a slot"; docker exec "$CONTAINER" psql -U gms_user -d "${PROBE}a" -tAc "select count(*) from timetable_slots;" | grep -q "^1$" || fail "the seed chain did not persist (docker exec without -i swallows a heredoc)"

SAME=$(docker exec "$CONTAINER" psql -U gms_user -d "${PROBE}a" -v ON_ERROR_STOP=0 -qtA \
  -c "INSERT INTO attendances (date, status, student_id, timetable_slot_id, school_id) VALUES ('2026-11-01','PRESENT',100,60,1);" 2>&1 || true)
[ -z "$SAME" ] && pass "control: same-tenant attendance written" \
              || fail "the legitimate write was rejected: $SAME"

X=$(docker exec "$CONTAINER" psql -U gms_user -d "${PROBE}a" -v ON_ERROR_STOP=0 -qtA \
  -c "INSERT INTO schools (id, school_code, school_name, enabled, status) VALUES (2,'SCH-B','School B',true,'ACTIVE');" \
  -c "INSERT INTO attendances (date, status, student_id, timetable_slot_id, school_id) VALUES ('2026-11-02','PRESENT',100,60,2);" 2>&1 | grep -i error || true)
if echo "$X" | grep -q "fk_attendance_student_same_school"; then
  pass "cross-tenant attendance refused by the composite key: $(echo "$X" | head -1 | sed 's/^ERROR: *//')"
else
  fail "the cross-tenant row was NOT refused -- ADR-020 is unproven. Output: $X"
fi

echo "== 4. the negative control: a constraint migration that cannot fail has not been tested"
# The duplicate has to be one V1 permits and V3 refuses. It cannot be a repeated invoice number:
# V1's Hibernate-generated global UNIQUE on invoices.invoice_number (finding DB-07) already
# rejects that at seed time, which is the defect, not the test. students.student_id carries no
# unique at all in V1, so two pupils in one school can hold the same admission number today.
DB=gms_v3_negative
docker exec "$CONTAINER" psql -U gms_user -d postgres -q \
  -c "DROP DATABASE IF EXISTS $DB;" -c "CREATE DATABASE $DB OWNER gms_user;" >/dev/null
psql_f "$DB" 1 < "$MIG/V1__baseline_current_schema.sql" >/dev/null
psql_f "$DB" 1 < "$MIG/V2__materialise_tenant_key.sql" >/dev/null
docker exec -i "$CONTAINER" psql -U gms_user -d "$DB" -v ON_ERROR_STOP=1 -q <<'DUP' >/dev/null
INSERT INTO schools (id, school_code, school_name, enabled, status) VALUES (1,'SCH-A','School A',true,'ACTIVE');
INSERT INTO classrooms (id, school_id, name, grade, section, status) VALUES (10,1,'A-1','1','A','ACTIVE');
INSERT INTO students (id, school_id, student_id, first_name, last_name, status, classroom_id) VALUES (100,1,'A-001','Ann','Lee','ACTIVE',10),(101,1,'A-001','Bob','Ng','ACTIVE',10);
DUP
# PostgreSQL phrases a failing ALTER TABLE ... ADD CONSTRAINT UNIQUE as "could not create unique
# index", not "duplicate key", so the match is on the constraint name in the whole error stream.
NEG=$(docker exec -i "$CONTAINER" psql -U gms_user -d "$DB" -v ON_ERROR_STOP=0 -qtA < "$MIG/V3__natural_keys.sql" 2>&1 | grep -iE "^ERROR|DETAIL" || true)
if echo "$NEG" | grep -q "uq_students_school_student_id"; then
  pass "V3 failed where it must: $(echo "$NEG" | tr '\n' ' ' | sed 's/ERROR: *//')"
else
  fail "V3 did not reject the seeded duplicate admission number -- the guard is decorative. Output: $NEG"
fi

echo "== 5. DB-07 in both directions: relaxed across tenants, still tight inside one"
# V3 drops the global UNIQUE on invoices.invoice_number. The consequence has to be measured on
# both sides: two schools may now issue the same number (that was the defect), and one school may
# still not issue it twice (that was never up for revision).
OK1=$(docker exec "$CONTAINER" psql -U gms_user -d "${PROBE}a" -v ON_ERROR_STOP=0 -qtA \
  -c "INSERT INTO invoices (id, school_id, invoice_number, status, invoice_date, due_date, total_amount) VALUES (1,1,'INV-0001','PENDING','2026-11-01','2026-11-30',100);" 2>&1 || true)
OK2=$(docker exec "$CONTAINER" psql -U gms_user -d "${PROBE}a" -v ON_ERROR_STOP=0 -qtA \
  -c "INSERT INTO invoices (id, school_id, invoice_number, status, invoice_date, due_date, total_amount) VALUES (2,2,'INV-0001','PENDING','2026-11-01','2026-11-30',200);" 2>&1 || true)
if [ -z "$OK1" ] && [ -z "$OK2" ]; then
  pass "the same invoice number accepted in two schools -- DB-07's global unique is gone"
else
  fail "per-school invoice numbers still collide: ${OK1}${OK2}"
fi
DUP2=$(docker exec "$CONTAINER" psql -U gms_user -d "${PROBE}a" -v ON_ERROR_STOP=0 -qtA \
  -c "INSERT INTO invoices (id, school_id, invoice_number, status, invoice_date, due_date, total_amount) VALUES (3,1,'INV-0001','PENDING','2026-11-01','2026-11-30',300);" 2>&1 || true)
if echo "$DUP2" | grep -q "uq_invoice_school_number"; then
  pass "a duplicate inside one school refused: $(echo "$DUP2" | head -1 | sed 's/^ERROR: *//')"
else
  fail "the per-school unique did not fire -- Output: $DUP2"
fi

echo "== 6. DB-02's fifth key: students.email, and the two directions a partial unique can fail in"
# `students.email` is nullable and the registration form leaves it blank for a child without a
# mailbox, so the key is partial. A partial unique fails in two opposite ways -- too tight (it
# treats blank as a value and refuses a second sibling with no email) and too loose (it stops
# binding real addresses). Both directions are asserted, on three rows and one insert.
docker exec -i "$CONTAINER" psql -U gms_user -d "${PROBE}a" -v ON_ERROR_STOP=1 -q <<'MAIL' >/dev/null
INSERT INTO classrooms (id, school_id, name, grade, section, status) VALUES (11,2,'B-1','1','A','ACTIVE');
INSERT INTO students (id, school_id, student_id, first_name, last_name, status, classroom_id, email) VALUES
  (101,1,'A-101','No','Email','ACTIVE',10,''),
  (102,1,'A-102','Also','None','ACTIVE',10,''),
  (103,1,'A-103','Null','Email','ACTIVE',10,NULL),
  (104,1,'A-104','Shared','Address','ACTIVE',10,'pupil@sch-a.test'),
  (105,2,'B-105','Other','School','ACTIVE',11,'pupil@sch-a.test');
MAIL
N=$(psql_q "${PROBE}a" -c "SELECT count(*) FROM students WHERE id BETWEEN 101 AND 105;")
[ "$N" = "5" ] || fail "the legitimate mail rows did not all insert (got $N of 5) -- the partial predicate is too tight or the seed aborted"
pass "two blank emails, one NULL and the same address in a different school all accepted"
DUPMAIL=$(docker exec "$CONTAINER" psql -U gms_user -d "${PROBE}a" -v ON_ERROR_STOP=0 -qtA \
  -c "INSERT INTO students (id, school_id, student_id, first_name, last_name, status, classroom_id, email) VALUES (106,1,'A-106','Second','Owner','ACTIVE',10,'pupil@sch-a.test');" 2>&1 || true)
if echo "$DUPMAIL" | grep -q "uq_students_school_email"; then
  pass "a repeated address inside one school refused: $(echo "$DUPMAIL" | head -1 | sed 's/^ERROR: *//')"
else
  fail "students.email is still unenforced -- Output: $DUPMAIL"
fi

echo
echo "ALL CHECKS PASSED"
echo "probe databases left in place for inspection: ${PROBE}a, ${PROBE}b, gms_v3_negative"
echo "drop them with: docker exec $CONTAINER psql -U gms_user -d postgres -c 'DROP DATABASE ...'"
