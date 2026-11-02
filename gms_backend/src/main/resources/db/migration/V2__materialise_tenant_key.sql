-- V2__materialise_tenant_key.sql
--
-- ADR-020: the tenant is a column on the row, not a predicate somebody remembers to write.
-- Ten tables do not carry it. Nine of them are given it here; `parent_student` is handled in V5
-- because it is a join table whose insert path is Hibernate's collection persistence, not an entity.
--
-- What this migration deliberately does NOT do is set the column NOT NULL. That is not timidity,
-- it is a measurement taken on the real baseline:
--
--   grep -in "school" \
--     model/entity/{Attendance,ExamSubject,FeePayment,Result,StudentExam,StudentFee,\
--     TeacherAssignment,TimetableSlot,Timetable,Parent}.java
--
-- returns nothing for all ten files. No entity maps the column and no service sets it, so a
-- NOT NULL here would be a production outage on the first attendance record created after the
-- deploy. `SET NOT NULL` is therefore a Stage 3 deliverable that ships in the same PR as the
-- entity field and the service assignment that make it true; until then the composite keys in V5
-- enforce the tenant for every row that has one, which is every row created by this migration,
-- and are permissive about the ones the old write path cannot fill.
--
-- Order matters and the reason is data, not style: results needs student_exams.school_id,
-- fee_payments needs student_fees.school_id and timetable_slots needs timetables.school_id,
-- so running the dependents first would backfill NULL into the next table.

ALTER TABLE attendances         ADD COLUMN school_id integer;
ALTER TABLE student_exams       ADD COLUMN school_id integer;
ALTER TABLE exam_subjects       ADD COLUMN school_id integer;
ALTER TABLE results             ADD COLUMN school_id integer;
ALTER TABLE student_fees        ADD COLUMN school_id integer;
ALTER TABLE fee_payments        ADD COLUMN school_id integer;
ALTER TABLE teacher_assignments ADD COLUMN school_id integer;
ALTER TABLE timetables          ADD COLUMN school_id integer;
ALTER TABLE timetable_slots     ADD COLUMN school_id integer;

UPDATE attendances       a  SET school_id = s.school_id  FROM students s       WHERE s.id  = a.student_id;
UPDATE student_exams     se SET school_id = s.school_id  FROM students s       WHERE s.id  = se.student_id;
UPDATE exam_subjects     es SET school_id = e.school_id  FROM exams e          WHERE e.id  = es.exam_id;
UPDATE results           r  SET school_id = se.school_id FROM student_exams se WHERE se.id = r.student_exam_id;
UPDATE student_fees      sf SET school_id = s.school_id  FROM students s       WHERE s.id  = sf.student_id;
UPDATE fee_payments      fp SET school_id = sf.school_id FROM student_fees sf  WHERE sf.id = fp.student_fee_id;
UPDATE teacher_assignments ta SET school_id = e.school_id FROM employees e     WHERE e.id  = ta.teacher_id;
UPDATE timetables        t  SET school_id = c.school_id  FROM classrooms c     WHERE c.id  = t.classroom_id;
UPDATE timetable_slots   ts SET school_id = t.school_id  FROM timetables t     WHERE t.id  = ts.timetable_id;

-- The assertion, not a comment. Written as nine clauses because a loop that cannot be read
-- is not a guard: an unowned row must abort the migration, not become a NULL that SET NOT NULL
-- turns into an error three statements later with no table named. On a database that already
-- has rows, this is the statement that tells the operator whether the data is tenant-clean
-- before the application is pointed at it.
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM attendances WHERE school_id IS NULL)         THEN RAISE EXCEPTION 'V2: attendances has rows with no tenant'; END IF;
  IF EXISTS (SELECT 1 FROM student_exams WHERE school_id IS NULL)       THEN RAISE EXCEPTION 'V2: student_exams has rows with no tenant'; END IF;
  IF EXISTS (SELECT 1 FROM exam_subjects WHERE school_id IS NULL)       THEN RAISE EXCEPTION 'V2: exam_subjects has rows with no tenant'; END IF;
  IF EXISTS (SELECT 1 FROM results WHERE school_id IS NULL)             THEN RAISE EXCEPTION 'V2: results has rows with no tenant'; END IF;
  IF EXISTS (SELECT 1 FROM student_fees WHERE school_id IS NULL)        THEN RAISE EXCEPTION 'V2: student_fees has rows with no tenant'; END IF;
  IF EXISTS (SELECT 1 FROM fee_payments WHERE school_id IS NULL)        THEN RAISE EXCEPTION 'V2: fee_payments has rows with no tenant'; END IF;
  IF EXISTS (SELECT 1 FROM teacher_assignments WHERE school_id IS NULL) THEN RAISE EXCEPTION 'V2: teacher_assignments has rows with no tenant'; END IF;
  IF EXISTS (SELECT 1 FROM timetables WHERE school_id IS NULL)          THEN RAISE EXCEPTION 'V2: timetables has rows with no tenant'; END IF;
  IF EXISTS (SELECT 1 FROM timetable_slots WHERE school_id IS NULL)     THEN RAISE EXCEPTION 'V2: timetable_slots has rows with no tenant'; END IF;
END $$;
