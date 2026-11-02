-- V5__composite_tenant_keys.sql
--
-- ADR-020 level 3. A child row that names a parent from another school becomes impossible to
-- write, not merely unlikely: the reference is to the pair `(id, school_id)`, so a row whose
-- tenant disagrees with its parent's tenant is rejected by the database before any service sees it.
-- This is the constraint that makes the cross-tenant denial in the MVP definition-of-done a
-- property of the schema rather than of a predicate in a repository method.
--
-- The candidate keys below are redundant by design. `(id, school_id)` is unique because `id`
-- alone already is; the point of the second column is to make the pair referenceable, and a
-- foreign key in PostgreSQL can only reference a unique index. The cost is one narrow index per
-- parent table, paid at write time, in exchange for the tenant invariant holding without
-- cooperation from application code.
--
-- schools needs no candidate key: its primary key is already (id), and the tenant is itself.
ALTER TABLE students       ADD CONSTRAINT uq_students_id_school      UNIQUE (id, school_id);
ALTER TABLE employees      ADD CONSTRAINT uq_employees_id_school     UNIQUE (id, school_id);
ALTER TABLE exams          ADD CONSTRAINT uq_exams_id_school         UNIQUE (id, school_id);
ALTER TABLE student_exams  ADD CONSTRAINT uq_student_exams_id_school UNIQUE (id, school_id);
ALTER TABLE exam_subjects  ADD CONSTRAINT uq_exam_subjects_id_school UNIQUE (id, school_id);
ALTER TABLE student_fees   ADD CONSTRAINT uq_student_fees_id_school  UNIQUE (id, school_id);
ALTER TABLE timetables     ADD CONSTRAINT uq_timetables_id_school    UNIQUE (id, school_id);

-- Same dependency order as V2: a dependent's composite key is referenced by its own dependents.
ALTER TABLE attendances         ADD CONSTRAINT fk_attendance_student_same_school
    FOREIGN KEY (student_id, school_id) REFERENCES students (id, school_id);
ALTER TABLE student_exams       ADD CONSTRAINT fk_student_exam_student_same_school
    FOREIGN KEY (student_id, school_id) REFERENCES students (id, school_id);
ALTER TABLE exam_subjects       ADD CONSTRAINT fk_exam_subject_exam_same_school
    FOREIGN KEY (exam_id, school_id) REFERENCES exams (id, school_id);
ALTER TABLE student_fees        ADD CONSTRAINT fk_student_fee_student_same_school
    FOREIGN KEY (student_id, school_id) REFERENCES students (id, school_id);
ALTER TABLE results             ADD CONSTRAINT fk_result_student_exam_same_school
    FOREIGN KEY (student_exam_id, school_id) REFERENCES student_exams (id, school_id);
ALTER TABLE fee_payments        ADD CONSTRAINT fk_fee_payment_student_fee_same_school
    FOREIGN KEY (student_fee_id, school_id) REFERENCES student_fees (id, school_id);
ALTER TABLE timetable_slots     ADD CONSTRAINT fk_slot_timetable_same_school
    FOREIGN KEY (timetable_id, school_id) REFERENCES timetables (id, school_id);
ALTER TABLE teacher_assignments ADD CONSTRAINT fk_assignment_teacher_same_school
    FOREIGN KEY (teacher_id, school_id) REFERENCES employees (id, school_id);
ALTER TABLE invoices            ADD CONSTRAINT fk_invoice_student_same_school
    FOREIGN KEY (student_id, school_id) REFERENCES students (id, school_id);
ALTER TABLE receipts            ADD CONSTRAINT fk_receipt_student_same_school
    FOREIGN KEY (student_id, school_id) REFERENCES students (id, school_id);

-- The link table is the one place a tenant is *added to a relationship* rather than owned by a
-- row, and it cannot be done in one statement: parent_student already has rows, so NOT NULL on a
-- fresh column fails on contact. Here it is added and backfilled; the SET NOT NULL travels with
-- Stage 3's change to the write path, for the reason recorded in V2 -- Parent.java:41-47 declares
-- this table as a @ManyToMany @JoinTable, so Hibernate inserts (parent_id, student_id) and
-- nothing else, and a NOT NULL today would fail on the first parent linked to a student.
ALTER TABLE parent_student ADD COLUMN school_id integer;
UPDATE parent_student ps SET school_id = s.school_id FROM students s WHERE s.id = ps.student_id;
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM parent_student WHERE school_id IS NULL) THEN
    RAISE EXCEPTION 'V5: a parent link points at a student with no school';
  END IF;
END $$;
ALTER TABLE parent_student ADD CONSTRAINT fk_parent_student_student_same_school
    FOREIGN KEY (student_id, school_id) REFERENCES students (id, school_id);
-- Rule I-1 re-run here for the one table whose tenant column did not exist when V4 checked it.
CREATE INDEX idx_parent_student_school ON parent_student (school_id);

-- Rule I-2 again, at the end rather than only in V4: this file introduces eleven new foreign
-- keys, and an invariant that is only checked at the moment it happens to be true is not an
-- invariant. The audit-column exemption documented in V4 (`created_by` / `updated_by`, 62 of 69
-- unindexed references measured against the real baseline) is repeated verbatim so the two
-- guards cannot drift apart. The indexes that satisfy it are all in V4, which is why this file
-- adds none except the one above for the column this file creates.
DO $$ BEGIN
  IF EXISTS (
      SELECT 1 FROM pg_constraint c
      JOIN LATERAL unnest(c.conkey) WITH ORDINALITY AS k(attnum, ord) ON true
      JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = k.attnum
      WHERE c.contype = 'f' AND k.ord = 1
        AND a.attname NOT IN ('created_by', 'updated_by')
        AND NOT EXISTS (SELECT 1 FROM pg_index i
                        WHERE i.indrelid = c.conrelid AND i.indkey[0] = k.attnum)
  ) THEN RAISE EXCEPTION 'V5: a foreign key added here has no index leading on its first column'; END IF;
END $$;

-- Rule I-1 again, for the same reason: parent_student only carries the tenant as of this file.
DO $$ BEGIN
  IF EXISTS (
      SELECT 1 FROM pg_attribute a
      JOIN pg_class t ON t.oid = a.attrelid
      JOIN pg_namespace n ON n.oid = t.relnamespace
      WHERE n.nspname = 'public' AND t.relkind = 'r' AND a.attname = 'school_id'
        AND NOT a.attisdropped AND t.relname <> 'schools'
        AND NOT EXISTS (SELECT 1 FROM pg_index i
                        WHERE i.indrelid = t.oid AND i.indkey[0] = a.attnum)
  ) THEN RAISE EXCEPTION 'V5: Rule I-1 violated -- a table carrying school_id has no index leading on it'; END IF;
END $$;
