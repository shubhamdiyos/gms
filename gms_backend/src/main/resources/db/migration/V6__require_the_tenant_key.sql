-- V6__require_the_tenant_key.sql
--
-- ADR-020 level 3, finished. Stage 2 materialised the tenant key and Stage 3 `C-3` put it in every
-- declared query, but neither could *require* it, and V2 said why in its own header: no entity mapped
-- the column, so `SET NOT NULL` would have been an outage on the first attendance record created after
-- the deploy. That deferral was measured, not guessed, and it was still open when `C-3` verified itself:
-- on the verification database, with all nine `V2` columns present, the `timetables` row the running
-- application had just created through its own service carried `school_id = NULL`.
--
--     SELECT school_id FROM timetables WHERE classroom_id = 2;   -- (NULL), against V2's own column
--
-- PostgreSQL checks a composite foreign key with MATCH SIMPLE, so that NULL did not violate
-- `fk_slot_timetable_same_school` — it *satisfied* it. Every same-school guarantee in V5 was therefore
-- enforcing the rows a migration inserted and none of the rows the application inserts, which is the
-- difference between a control and a comment about a control.
--
-- What makes this file possible is the Java side of the same card, shipped together with it: nine
-- entities now map the column (`Attendance`, `StudentExam`, `ExamSubject`, `Result`, `StudentFee`,
-- `FeePayment`, `TeacherAssignment`, `Timetable`, `TimetableSlot`) the way `Invoice` and `Receipt` have
-- mapped it since the baseline, and every write path sets it — from the caller's tenant, never copied
-- from the parent row. That choice is deliberate and it is the reason the constraints below have teeth:
-- a service that copies `student.school` into `attendance.school_id` produces a row that always agrees
-- with its parent and can therefore never be refused, so the invariant would be enforced by the least
-- trustworthy code in the system. Taking it from the claim instead means a caller who names another
-- school's student is rejected *by the database*, whatever the service forgot.
--
-- Three sections, in this order, because each depends on the one before it.

-- ── 1. Backfill, then require ─────────────────────────────────────────────────────────────────────
-- The same nine statements V2 ran, repeated rather than trusted to history. Rows written between that
-- migration and this card's code carry NULL, and an operator with a live database must be able to
-- upgrade it rather than rebuild it; re-deriving from the parent is idempotent, so this is a no-op on a
-- database the new application wrote into and a repair on one it did not. Same dependency order as V2
-- for the same reason: `results` reads `student_exams`, `fee_payments` reads `student_fees`,
-- `timetable_slots` reads `timetables`.

UPDATE attendances       a  SET school_id = s.school_id  FROM students s       WHERE s.id  = a.student_id AND a.school_id IS NULL;
UPDATE student_exams     se SET school_id = s.school_id  FROM students s       WHERE s.id  = se.student_id AND se.school_id IS NULL;
UPDATE exam_subjects     es SET school_id = e.school_id  FROM exams e          WHERE e.id  = es.exam_id AND es.school_id IS NULL;
UPDATE results           r  SET school_id = se.school_id FROM student_exams se WHERE se.id = r.student_exam_id AND r.school_id IS NULL;
UPDATE student_fees      sf SET school_id = s.school_id  FROM students s       WHERE s.id  = sf.student_id AND sf.school_id IS NULL;
UPDATE fee_payments      fp SET school_id = sf.school_id FROM student_fees sf  WHERE sf.id = fp.student_fee_id AND fp.school_id IS NULL;
UPDATE teacher_assignments ta SET school_id = e.school_id FROM employees e     WHERE e.id  = ta.teacher_id AND ta.school_id IS NULL;
UPDATE timetables        t  SET school_id = c.school_id  FROM classrooms c     WHERE c.id  = t.classroom_id AND t.school_id IS NULL;
UPDATE timetable_slots   ts SET school_id = t.school_id  FROM timetables t     WHERE t.id  = ts.timetable_id AND ts.school_id IS NULL;
UPDATE parent_student    ps SET school_id = s.school_id  FROM students s       WHERE s.id  = ps.student_id AND ps.school_id IS NULL;

-- The assertion, still here rather than replaced by the `SET NOT NULL` below it, because a NULL that
-- survives the backfill is not the same problem as a NULL that never had one. A row whose parent no
-- longer exists cannot be given a tenant by anybody, and `SET NOT NULL` would report that as
-- "column contains null" — true, and useless to an operator. Ten named tables, ten named failures.
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM attendances       WHERE school_id IS NULL) THEN RAISE EXCEPTION 'V6: attendances has a row no student can account for'; END IF;
  IF EXISTS (SELECT 1 FROM student_exams     WHERE school_id IS NULL) THEN RAISE EXCEPTION 'V6: student_exams has a row no student can account for'; END IF;
  IF EXISTS (SELECT 1 FROM exam_subjects     WHERE school_id IS NULL) THEN RAISE EXCEPTION 'V6: exam_subjects has a row no exam can account for'; END IF;
  IF EXISTS (SELECT 1 FROM results           WHERE school_id IS NULL) THEN RAISE EXCEPTION 'V6: results has a row no student exam can account for'; END IF;
  IF EXISTS (SELECT 1 FROM student_fees      WHERE school_id IS NULL) THEN RAISE EXCEPTION 'V6: student_fees has a row no student can account for'; END IF;
  IF EXISTS (SELECT 1 FROM fee_payments      WHERE school_id IS NULL) THEN RAISE EXCEPTION 'V6: fee_payments has a row no student fee can account for'; END IF;
  IF EXISTS (SELECT 1 FROM teacher_assignments WHERE school_id IS NULL) THEN RAISE EXCEPTION 'V6: teacher_assignments has a row no employee can account for'; END IF;
  IF EXISTS (SELECT 1 FROM timetables        WHERE school_id IS NULL) THEN RAISE EXCEPTION 'V6: timetables has a row no classroom can account for'; END IF;
  IF EXISTS (SELECT 1 FROM timetable_slots   WHERE school_id IS NULL) THEN RAISE EXCEPTION 'V6: timetable_slots has a row no timetable can account for'; END IF;
  IF EXISTS (SELECT 1 FROM parent_student    WHERE school_id IS NULL) THEN RAISE EXCEPTION 'V6: parent_student has a link no student can account for'; END IF;
END $$;

ALTER TABLE attendances         ALTER COLUMN school_id SET NOT NULL;
ALTER TABLE student_exams       ALTER COLUMN school_id SET NOT NULL;
ALTER TABLE exam_subjects       ALTER COLUMN school_id SET NOT NULL;
ALTER TABLE results             ALTER COLUMN school_id SET NOT NULL;
ALTER TABLE student_fees        ALTER COLUMN school_id SET NOT NULL;
ALTER TABLE fee_payments        ALTER COLUMN school_id SET NOT NULL;
ALTER TABLE teacher_assignments ALTER COLUMN school_id SET NOT NULL;
ALTER TABLE timetables          ALTER COLUMN school_id SET NOT NULL;
ALTER TABLE timetable_slots     ALTER COLUMN school_id SET NOT NULL;
ALTER TABLE parent_student      ALTER COLUMN school_id SET NOT NULL;

-- ── 2. The one tenant key the ORM cannot write ───────────────────────────────────────────────────
-- `Parent.java:41-47` declares the link as `@ManyToMany @JoinTable(name = "parent_student", ...)`, and
-- a join-table row written by Hibernate's collection persistence contains exactly `parent_id` and
-- `student_id` — there is no field on a `@JoinTable` for a third column and no entity to set it on. So
-- `NOT NULL` above would fail on the first `parent.getStudents().add(student)`, which is
-- `StudentAdmissionServiceImpl.java:134` — the admission path, i.e. the MVP.
--
-- The alternative is an explicit `ParentStudent` entity. It is not free: `parent.getStudents()` is read
-- at fourteen sites in three services, one of them the authorisation gate behind six child-data
-- endpoints (`ParentServiceImpl.java:273`) and one of them returned straight to the client as a
-- `List<Student>` (`ParentStudentServiceImpl.java:120`), so changing its element type changes a
-- response contract in the middle of Stage 3. The card that owns the join tables — `C-8`, which cuts
-- `notification_recipients`, `announcement_targets` and `message_recipients` the same way — is the
-- place to do that deliberately, and this trigger will still be correct afterwards as a backstop.
--
-- So the database derives the value, from the only thing that can be right about it: the school of the
-- student being linked. Under D-2 a parent is global and a link has no tenant of its own to disagree
-- with, which is why derivation — normally the thing this project refuses to let happen invisibly — is
-- the honest mechanism here rather than a convenience. It is not a default: a caller that supplies a
-- school which is not the student's is refused instead of quietly corrected, so the constraint in
-- section 3 still has something to enforce.

CREATE OR REPLACE FUNCTION parent_student_tenant_key() RETURNS trigger AS $$
DECLARE
    student_school integer;
BEGIN
    SELECT school_id INTO student_school FROM students WHERE id = NEW.student_id;
    IF student_school IS NULL THEN
        RAISE EXCEPTION 'parent_student link points at student % which does not exist', NEW.student_id;
    END IF;
    IF NEW.school_id IS NOT NULL AND NEW.school_id <> student_school THEN
        RAISE EXCEPTION 'parent_student link for student % names school %, which is not the student''s school',
            NEW.student_id, NEW.school_id;
    END IF;
    NEW.school_id := student_school;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_parent_student_tenant_key
    BEFORE INSERT OR UPDATE OF student_id, school_id ON parent_student
    FOR EACH ROW EXECUTE FUNCTION parent_student_tenant_key();

-- ── 3. The second parents ─────────────────────────────────────────────────────────────────────────
-- V5 keyed each child to *one* parent: `teacher_assignments` was checked against the employee, but not
-- against the subject or the classroom it also names, so a row could still join a school-7 teacher to a
-- school-9 classroom — the same class of row V5 was written to make impossible, arriving through the
-- second column instead of the first. `timetables.classroom_id` had no same-school check at all, which
-- is the one that mattered: `C-3` measured school B reading school A's timetable through
-- `GET /timetables/classroom/{id}`, and the read is fixed while the write was still free to straddle.
--
-- These seven are expressible only because the column now exists and is required, which is why they
-- are here rather than in V5. Four candidate keys first — redundant on `(id, school_id)` for the same
-- reason V5 recorded: `id` alone is already unique, and the second column is there to make the pair
-- referenceable, because a PostgreSQL foreign key can only point at a unique index.

ALTER TABLE classrooms      ADD CONSTRAINT uq_classrooms_id_school UNIQUE (id, school_id);
ALTER TABLE subjects        ADD CONSTRAINT uq_subjects_id_school   UNIQUE (id, school_id);
ALTER TABLE fees            ADD CONSTRAINT uq_fees_id_school       UNIQUE (id, school_id);
ALTER TABLE timetable_slots ADD CONSTRAINT uq_timetable_slots_id_school UNIQUE (id, school_id);

ALTER TABLE timetables        ADD CONSTRAINT fk_timetable_classroom_same_school
    FOREIGN KEY (classroom_id, school_id) REFERENCES classrooms (id, school_id);
ALTER TABLE teacher_assignments ADD CONSTRAINT fk_assignment_subject_same_school
    FOREIGN KEY (subject_id, school_id) REFERENCES subjects (id, school_id);
ALTER TABLE teacher_assignments ADD CONSTRAINT fk_assignment_classroom_same_school
    FOREIGN KEY (classroom_id, school_id) REFERENCES classrooms (id, school_id);
ALTER TABLE student_fees      ADD CONSTRAINT fk_student_fee_fee_same_school
    FOREIGN KEY (fee_id, school_id) REFERENCES fees (id, school_id);
ALTER TABLE exam_subjects     ADD CONSTRAINT fk_exam_subject_subject_same_school
    FOREIGN KEY (subject_id, school_id) REFERENCES subjects (id, school_id);
ALTER TABLE student_exams     ADD CONSTRAINT fk_student_exam_exam_subject_same_school
    FOREIGN KEY (exam_subject_id, school_id) REFERENCES exam_subjects (id, school_id);
ALTER TABLE attendances       ADD CONSTRAINT fk_attendance_slot_same_school
    FOREIGN KEY (timetable_slot_id, school_id) REFERENCES timetable_slots (id, school_id);

-- Not closed by this file, and named so the next reader does not mistake the pattern for completeness:
-- `invoices`/`receipts` map and write `school_id` but never assign `student`
-- (`InvoiceServiceImpl.createInvoice`, `ReceiptServiceImpl.createReceipt`, although both requests carry
-- a `studentId`), so their V5 keys stay vacuous for a reason that has nothing to do with the tenant
-- column — it is FIN-04's missing linkage, and it belongs to the financial package, not here.

-- ── 4. The two index rules, re-run ────────────────────────────────────────────────────────────────
-- Same guards as V4 and V5, copied rather than shared because a guard that is only checked at the
-- moment it happens to be true is not an invariant, and this file adds four unique indexes and seven
-- foreign keys.

DO $$ BEGIN
  IF EXISTS (
      SELECT 1 FROM pg_constraint c
      JOIN LATERAL unnest(c.conkey) WITH ORDINALITY AS k(attnum, ord) ON true
      JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = k.attnum
      WHERE c.contype = 'f' AND k.ord = 1
        AND a.attname NOT IN ('created_by', 'updated_by')
        AND NOT EXISTS (SELECT 1 FROM pg_index i
                        WHERE i.indrelid = c.conrelid AND i.indkey[0] = k.attnum)
  ) THEN RAISE EXCEPTION 'V6: a foreign key added here has no index leading on its first column'; END IF;
END $$;

DO $$ BEGIN
  IF EXISTS (
      SELECT 1 FROM pg_attribute a
      JOIN pg_class t ON t.oid = a.attrelid
      JOIN pg_namespace n ON n.oid = t.relnamespace
      WHERE n.nspname = 'public' AND t.relkind = 'r' AND a.attname = 'school_id'
        AND NOT a.attisdropped AND t.relname <> 'schools'
        AND NOT EXISTS (SELECT 1 FROM pg_index i
                        WHERE i.indrelid = t.oid AND i.indkey[0] = a.attnum)
  ) THEN RAISE EXCEPTION 'V6: Rule I-1 violated -- a table carrying school_id has no index leading on it'; END IF;
END $$;

-- And the assertion this whole file exists for: after V6 no row of any tenant table can be written
-- without a tenant, by software or by hand. Three tables are exempt, each for a reason that is a fact
-- about the code rather than a convenience — `schools` because its `school_id` is a varchar business
-- key (the tenant is the row, so there is no parent to name), and `email_logs`/`sms_logs` because
-- nothing in this application writes them at all: `EmailLogService` and `SmsLogService` are interfaces
-- with no implementation and no injection point, their repositories have no reader, and the sender that
-- would fill the column is the communication feature that has not been built. A NOT NULL on a column no
-- code path can fill is not a control either; it is a future outage waiting for the first writer.
DO $$ BEGIN
  IF EXISTS (
      SELECT 1 FROM pg_attribute a
      JOIN pg_class t ON t.oid = a.attrelid
      JOIN pg_namespace n ON n.oid = t.relnamespace
      WHERE n.nspname = 'public' AND t.relkind = 'r' AND a.attname = 'school_id'
        AND NOT a.attisdropped
        AND t.relname NOT IN ('schools', 'email_logs', 'sms_logs')
        AND a.attnotnull = false
  ) THEN RAISE EXCEPTION 'V6: a table still carries a nullable school_id'; END IF;
END $$;
