-- V3__natural_keys.sql
--
-- DB-02 (natural keys that are not unique) and DB-07 (four numbered keys that are unique in the
-- wrong scope). Each line here is a race a service can no longer lose: today two requests can
-- mint the same admission number in the same school, and the second one wins.
--
-- The eight DROP/ADD pairs are the ones where V1 already carries a Hibernate-generated
-- `uk…` constraint. They are dropped and re-added rather than left alone for two reasons: the
-- name is the interface a later migration and an operator's `psql` session have to refer to,
-- and four of them change scope from global to per-school, which is the whole of DB-07.
-- Where V3 only *adds* a key that V1 never had, there is no DROP to read.

-- ── Keys that exist globally and must exist per school (DB-07) ────────────────────────────
-- `invoices.invoice_number` unique across every school means school 2 cannot issue an invoice
-- numbered like school 1's. Same for receipts, designation codes and template codes.
ALTER TABLE invoices               DROP CONSTRAINT ukl1x55mfsay7co0r3m9ynvipd5;
ALTER TABLE invoices               ADD  CONSTRAINT uq_invoice_school_number UNIQUE (school_id, invoice_number);
ALTER TABLE receipts               DROP CONSTRAINT uksehtr7fmqdjw4f4n3xys7x19l;
ALTER TABLE receipts               ADD  CONSTRAINT uq_receipt_school_number UNIQUE (school_id, receipt_number);
ALTER TABLE designations           DROP CONSTRAINT ukt3niify060s1kmfdfrrtfd189;
ALTER TABLE designations           ADD  CONSTRAINT uq_designation_school_code UNIQUE (school_id, designation_id);
ALTER TABLE communication_templates DROP CONSTRAINT uk9okiep55ukktbyv9x8hy9t0ib;
ALTER TABLE communication_templates ADD  CONSTRAINT uq_communication_templates_school_code UNIQUE (school_id, template_code);
-- `timetables.classroom_id` unique globally means a classroom can have exactly one timetable for
-- the lifetime of the database: no second academic year, ever. This is the one that would have
-- been discovered in production as a 500 on the third September.
ALTER TABLE timetables             DROP CONSTRAINT ukd3rqs46ljb6fnrh0ie24otgih;
ALTER TABLE timetables             ADD  CONSTRAINT uq_timetable_classroom_year UNIQUE (classroom_id, academic_year);

-- ── Keys whose name is the only change (semantics already correct in V1) ──────────────────
ALTER TABLE schools                DROP CONSTRAINT ukouqsuih3yotj0anrhnc4rsbbv;
ALTER TABLE schools                ADD  CONSTRAINT uq_schools_school_code UNIQUE (school_code);
ALTER TABLE users                  DROP CONSTRAINT ukr43af9ap4edm43mmtq01oddj6;
ALTER TABLE users                  ADD  CONSTRAINT uq_users_username UNIQUE (username);
-- Global by owner decision D-2 (one person, one parent account, across schools) -- not an
-- oversight, so it is written down as a named constraint rather than left as a hash.
ALTER TABLE parents                DROP CONSTRAINT ukisg8o11v7k7i1vko77g2bv4mu;
ALTER TABLE parents                ADD  CONSTRAINT uq_parents_email UNIQUE (email);

-- ── Keys that did not exist at all ────────────────────────────────────────────────────────
ALTER TABLE users     ADD CONSTRAINT uq_users_school_email   UNIQUE (school_id, email);
ALTER TABLE users     ADD CONSTRAINT uq_users_school_user_id UNIQUE (school_id, user_id);
ALTER TABLE students  ADD CONSTRAINT uq_students_school_student_id UNIQUE (school_id, student_id);
-- DB-02's fifth key. Partial, because `students.email` is nullable and the registration form
-- leaves it empty for a child without a mailbox; two blank emails in one class are not a
-- duplicate. `createUserForStudent` (`StudentServiceImpl.java:83-87`) already refuses a repeat
-- of *any* email globally via `existsByEmail`, so this constraint is looser than the code, not
-- stricter -- it closes the hole the code leaves when it is bypassed, and does not add a rule
-- the app has never enforced.
CREATE UNIQUE INDEX uq_students_school_email ON students (school_id, email)
    WHERE email IS NOT NULL AND email <> '';
ALTER TABLE employees ADD CONSTRAINT uq_employees_school_employee_id UNIQUE (school_id, employee_id);
ALTER TABLE employees ADD CONSTRAINT uq_employees_school_email UNIQUE (school_id, email);
ALTER TABLE classrooms ADD CONSTRAINT uq_classrooms_school_name_grade_section UNIQUE (school_id, name, grade, section);
ALTER TABLE sections  ADD CONSTRAINT uq_sections_school_name UNIQUE (school_id, name);
ALTER TABLE subjects  ADD CONSTRAINT uq_subjects_school_code UNIQUE (school_id, code);
ALTER TABLE academic_years ADD CONSTRAINT uq_academic_years_school_name UNIQUE (school_id, name);
CREATE UNIQUE INDEX uq_academic_years_one_current ON academic_years (school_id) WHERE is_current;
ALTER TABLE attendances ADD CONSTRAINT uq_attendance_student_slot_date UNIQUE (student_id, timetable_slot_id, date);
ALTER TABLE timetable_slots ADD CONSTRAINT uq_slot_day_period UNIQUE (timetable_id, day_of_week, period);
ALTER TABLE teacher_assignments ADD CONSTRAINT uq_assignment_teacher_subject_class_year
    UNIQUE (teacher_id, subject_id, classroom_id, academic_year);
ALTER TABLE exams ADD CONSTRAINT uq_exam_school_name_year UNIQUE (school_id, name, academic_year);
ALTER TABLE exam_subjects ADD CONSTRAINT uq_exam_subject UNIQUE (exam_id, subject_id);
ALTER TABLE student_exams ADD CONSTRAINT uq_student_exam_subject UNIQUE (student_id, exam_subject_id);
ALTER TABLE results  ADD CONSTRAINT uq_result_student_exam UNIQUE (student_exam_id);
ALTER TABLE grading_schemes ADD CONSTRAINT uq_grading_scheme_school_name_grade UNIQUE (school_id, name, grade);
ALTER TABLE grading_schemes ADD CONSTRAINT ck_grading_band_order CHECK (min_percentage <= max_percentage);
ALTER TABLE fees ADD CONSTRAINT uq_fee_school_type_year_grade_category
    UNIQUE (school_id, fee_type, academic_year, class_grade, student_category);
ALTER TABLE student_fees ADD CONSTRAINT uq_student_fee UNIQUE (student_id, fee_id);
CREATE UNIQUE INDEX uq_fee_payment_reference ON fee_payments (payment_reference)
    WHERE payment_reference IS NOT NULL;
-- The role vocabulary lives in a Hibernate @ElementCollection table with no key at all, so "the
-- same user is a TEACHER twice" is insertable today; this is the constraint that makes SEC-17's
-- vocabulary fix have a floor.
ALTER TABLE user_roles ADD CONSTRAINT uq_user_roles_user_role UNIQUE (user_id, role);
ALTER TABLE parent_student ADD CONSTRAINT pk_parent_student PRIMARY KEY (parent_id, student_id);
