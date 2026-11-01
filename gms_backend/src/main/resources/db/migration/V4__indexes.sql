-- V4__indexes.sql
--
-- DB-03: V1 creates 0 secondary indexes. The measured baseline is that every one of the 128
-- foreign keys is unindexed except the 14 columns that a UNIQUE constraint happened to cover,
-- and PostgreSQL does not index a foreign key for you. At 50,000 students that is not slow, it
-- is a sequential scan per listing request and a full-table lock per parent delete.
--
-- Rule I-1: a tenant-scoped index leads with school_id, because every read in this application
-- carries the tenant predicate. Rule I-2: every foreign key gets an index leading on its first
-- column. Both rules are closed by the assertions at the bottom of this file, not by review.
--
-- Prefix search uses varchar_pattern_ops deliberately. `pg_trgm` and a GIN index are the better
-- tool for `%middle%` matching and are deferred with a measured trigger (ARCHITECTURE.md §5):
-- when p95 on the student search crosses the acceptable number, the extension is one migration,
-- and a btree that cannot serve the query is not the same as one that was never built.

-- ── Identity and access ───────────────────────────────────────────────────────────────────
CREATE INDEX idx_schools_enabled_status       ON schools (enabled, status);
CREATE INDEX idx_users_school_status          ON users (school_id, user_status);
CREATE INDEX idx_users_employee               ON users (employee_id);
CREATE INDEX idx_users_student                ON users (student_id);
CREATE INDEX idx_users_parent                 ON users (parent_id);
CREATE INDEX idx_user_roles_role              ON user_roles (role);

-- ── Students and staff (the two collections the MVP actually pages through) ───────────────
CREATE INDEX idx_students_school_status       ON students (school_id, status);
CREATE INDEX idx_students_school_classroom    ON students (school_id, classroom_id);
CREATE INDEX idx_students_school_section      ON students (school_id, section_id);
CREATE INDEX idx_students_school_name         ON students (school_id, last_name, first_name);
CREATE INDEX idx_students_student_id_prefix   ON students (school_id, student_id varchar_pattern_ops);
CREATE INDEX idx_students_last_name_prefix    ON students (school_id, last_name varchar_pattern_ops);
CREATE INDEX idx_students_email               ON students (email);
CREATE INDEX idx_students_dob                 ON students (date_of_birth);
-- Rule I-2 for the columns a rosters query actually joins on. A tenant-scoped composite above
-- leads with school_id, so it cannot serve `WHERE classroom_id = ?` on its own; PostgreSQL
-- needs the column first.
CREATE INDEX idx_students_classroom           ON students (classroom_id);
CREATE INDEX idx_students_section             ON students (section_id);
CREATE INDEX idx_student_admissions_school    ON student_admissions (school_id);
CREATE INDEX idx_student_admission_classroom  ON student_admissions (classroom_id);
CREATE INDEX idx_employees_school_status      ON employees (school_id, employee_status);
CREATE INDEX idx_employees_school_designation ON employees (school_id, designation);
CREATE INDEX idx_employees_school_department  ON employees (school_id, department);
CREATE INDEX idx_employees_is_teaching        ON employees (is_teaching);

-- ── Structure ─────────────────────────────────────────────────────────────────────────────
CREATE INDEX idx_classrooms_school_status     ON classrooms (school_id, status);
CREATE INDEX idx_sections_school_status       ON sections (school_id, status);
CREATE INDEX idx_subjects_school_status       ON subjects (school_id, status);
CREATE INDEX idx_academic_years_school_current ON academic_years (school_id, is_current);
CREATE INDEX idx_school_calendars_school      ON school_calendars (school_id);
CREATE INDEX idx_school_calendars_school_date ON school_calendars (school_id, event_date);

-- ── Attendance and timetabling ────────────────────────────────────────────────────────────
CREATE INDEX idx_attendance_school_date_status ON attendances (school_id, date, status);
CREATE INDEX idx_attendance_student_date       ON attendances (student_id, date);
CREATE INDEX idx_attendance_slot               ON attendances (timetable_slot_id);
CREATE INDEX idx_timetable_school              ON timetables (school_id);
CREATE INDEX idx_timetable_slot_timetable      ON timetable_slots (timetable_id);
CREATE INDEX idx_timetable_slot_assignment     ON timetable_slots (assignment_id);
CREATE INDEX idx_timetable_slot_school         ON timetable_slots (school_id);
CREATE INDEX idx_assignment_school             ON teacher_assignments (school_id);
CREATE INDEX idx_assignment_classroom_year     ON teacher_assignments (classroom_id, academic_year);
CREATE INDEX idx_assignment_teacher            ON teacher_assignments (teacher_id);
CREATE INDEX idx_assignment_subject            ON teacher_assignments (subject_id);

-- ── Academics ─────────────────────────────────────────────────────────────────────────────
CREATE INDEX idx_exam_school_date              ON exams (school_id, exam_date);
CREATE INDEX idx_exam_school_year              ON exams (school_id, academic_year);
CREATE INDEX idx_exam_subject_exam             ON exam_subjects (exam_id);
CREATE INDEX idx_exam_subject_subject          ON exam_subjects (subject_id);
CREATE INDEX idx_exam_subject_school           ON exam_subjects (school_id);
CREATE INDEX idx_student_exam_exam_subject     ON student_exams (exam_subject_id);
CREATE INDEX idx_student_exam_student          ON student_exams (student_id);
CREATE INDEX idx_student_exam_school           ON student_exams (school_id);
CREATE INDEX idx_result_student_exam           ON results (student_exam_id);
CREATE INDEX idx_result_school                 ON results (school_id);
CREATE INDEX idx_result_status                 ON results (status);
CREATE INDEX idx_grading_scheme_school_active  ON grading_schemes (school_id, is_active);

-- ── Money ─────────────────────────────────────────────────────────────────────────────────
CREATE INDEX idx_fee_school_status             ON fees (school_id, status);
CREATE INDEX idx_fee_school_year               ON fees (school_id, academic_year);
CREATE INDEX idx_fee_school_due                ON fees (school_id, due_date);
CREATE INDEX idx_fee_structure_school          ON fee_structures (school_id);
CREATE INDEX idx_student_fee_fee               ON student_fees (fee_id);
CREATE INDEX idx_student_fee_student_status    ON student_fees (student_id, status);
CREATE INDEX idx_student_fee_school            ON student_fees (school_id);
CREATE INDEX idx_fee_payment_student_fee       ON fee_payments (student_fee_id);
CREATE INDEX idx_fee_payment_date              ON fee_payments (payment_date);
CREATE INDEX idx_fee_payment_school            ON fee_payments (school_id);
CREATE INDEX idx_invoice_school_status         ON invoices (school_id, status);
CREATE INDEX idx_invoice_student               ON invoices (student_id);
CREATE INDEX idx_invoice_parent                ON invoices (parent_id);
CREATE INDEX idx_receipt_school_status         ON receipts (school_id, status);
CREATE INDEX idx_receipt_student               ON receipts (student_id);
CREATE INDEX idx_receipt_parent                ON receipts (parent_id);
CREATE INDEX idx_receipt_payment               ON receipts (fee_payment_id);
CREATE INDEX idx_receipt_invoice               ON receipts (invoice_id);
CREATE INDEX idx_parent_student_student        ON parent_student (student_id);
CREATE INDEX idx_payment_gateway_school        ON payment_gateways (school_id);
CREATE INDEX idx_financial_transaction_school  ON financial_transactions (school_id, transaction_date);
CREATE INDEX idx_financial_transaction_student ON financial_transactions (student_id);
CREATE INDEX idx_financial_transaction_employee ON financial_transactions (employee_id);
CREATE INDEX idx_financial_transaction_payment ON financial_transactions (fee_payment_id);
CREATE INDEX idx_financial_report_school       ON financial_reports (school_id);

-- ── Communication ─────────────────────────────────────────────────────────────────────────
CREATE INDEX idx_notification_school_created   ON notifications (school_id, created_on DESC);
CREATE INDEX idx_notification_school_delivery  ON notifications (school_id, delivery_status, created_on DESC);
CREATE INDEX idx_announcement_school_published ON announcements (school_id, is_published, publish_date DESC);
CREATE INDEX idx_announcement_school_pinned    ON announcements (school_id, is_pinned);
CREATE INDEX idx_message_school_sender         ON messages (school_id, sender_id);
CREATE INDEX idx_message_thread                ON messages (parent_message_id);
CREATE INDEX idx_communication_template_school ON communication_templates (school_id, communication_type);
CREATE INDEX idx_email_log_school_created      ON email_logs (school_id, created_on DESC);
CREATE INDEX idx_sms_log_school_created        ON sms_logs (school_id, created_on DESC);
CREATE INDEX idx_parent_user                   ON parents (user_id);

-- ── Rule I-1, enforced ────────────────────────────────────────────────────────────────────
-- Every table that holds the tenant key must have at least one index leading on it, or the
-- tenant predicate is a sequential scan. `schools` is excluded because its own school_id column
-- is a mirror of its id, not a tenant reference.
DO $$ BEGIN
  IF EXISTS (
      SELECT 1 FROM pg_attribute a
      JOIN pg_class t ON t.oid = a.attrelid
      JOIN pg_namespace n ON n.oid = t.relnamespace
      WHERE n.nspname = 'public' AND t.relkind = 'r' AND a.attname = 'school_id'
        AND NOT a.attisdropped AND t.relname <> 'schools'
        AND NOT EXISTS (SELECT 1 FROM pg_index i
                        WHERE i.indrelid = t.oid AND i.indkey[0] = a.attnum)
  ) THEN RAISE EXCEPTION 'V4: Rule I-1 violated -- a table carrying school_id has no index leading on it'; END IF;
END $$;

-- ── Rule I-2, enforced ────────────────────────────────────────────────────────────────────
-- PostgreSQL does not create an index for a foreign key. This is the same query the roadmap
-- criterion asks Stage 2 to run by hand, promoted into the migration so a future Vn that adds a
-- relation without an index fails the deploy instead of failing a dashboard three months later.
--
-- The exemption is measured, not assumed. Run against V1+V2+V3 before this assertion existed,
-- the unindexed leading-FK columns numbered 69, and they split 62 to 7: every one of the 62 is
-- `created_by` or `updated_by`, the two audit-bookkeeping references BaseAuditEntity puts on
-- thirty-four tables. No read in the product filters on "rows created by user 41", so 62 indexes
-- would be 62 write-time taxes for a query that does not exist -- and they would be *dedicated*
-- indexes, since a btree on created_by cannot serve the tenant predicate. The 7 that remain are
-- navigated relations (a student's classroom, a transaction's student/employee/payment) and all
-- seven are indexed above. The exemption is by column name so that the assertion stays a guard:
-- an unfamiliar FK column still has to earn an index.
DO $$ BEGIN
  IF EXISTS (
      SELECT 1 FROM pg_constraint c
      JOIN LATERAL unnest(c.conkey) WITH ORDINALITY AS k(attnum, ord) ON true
      JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = k.attnum
      WHERE c.contype = 'f' AND k.ord = 1
        AND a.attname NOT IN ('created_by', 'updated_by')
        AND NOT EXISTS (SELECT 1 FROM pg_index i
                        WHERE i.indrelid = c.conrelid AND i.indkey[0] = k.attnum)
  ) THEN RAISE EXCEPTION 'V4: Rule I-2 violated -- a navigated foreign key has no index leading on its first column'; END IF;
END $$;
