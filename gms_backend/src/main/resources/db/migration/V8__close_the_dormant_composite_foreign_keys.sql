-- ─────────────────────────────────────────────────────────────────────────────
-- V8: close the dormant composite foreign keys (finding DATA-01..05)
-- ─────────────────────────────────────────────────────────────────────────────
-- Eleven columns carried a foreign key on the referenced row's bare id, so the
-- pair (referenced id, school) was never checked together: a student could point
-- at another school's classroom while keeping its own school_id, and every
-- same-school guarantee downstream of the column was vacuous. Eleven composite
-- keys close the eleven; the four referenced tables that had no (id, school_id)
-- candidate key get one, because a PostgreSQL foreign key can only point at a
-- unique index (the reason V5 and V6 recorded before this).
--
-- A composite key with MATCH SIMPLE semantics — PostgreSQL's default — skips
-- enforcement only when a referencing column is NULL. Every child table's
-- school_id has been NOT NULL since V6, so a row escapes the pair check exactly
-- when it has no reference to escape with.

-- The candidate keys the new foreign keys point at.
ALTER TABLE sections            ADD CONSTRAINT uq_sections_id_school            UNIQUE (id, school_id);
ALTER TABLE teacher_assignments ADD CONSTRAINT uq_teacher_assignments_id_school UNIQUE (id, school_id);
ALTER TABLE invoices            ADD CONSTRAINT uq_invoices_id_school            UNIQUE (id, school_id);
ALTER TABLE fee_payments        ADD CONSTRAINT uq_fee_payments_id_school        UNIQUE (id, school_id);

-- Refuse to constrain a database that already contains a straddled row: the
-- assertion names every table it refuses, in the V6 precedent's shape, instead of
-- letting a generic foreign-key violation name nothing. Rows whose FK column is
-- NULL have no reference and are not offences.
DO $$
DECLARE
    pair record;
    offenders integer;
    refused text := '';
BEGIN
    FOR pair IN
        SELECT * FROM (VALUES
            ('students',               'classroom_id',   'classrooms'),
            ('students',               'section_id',     'sections'),
            ('users',                  'employee_id',    'employees'),
            ('users',                  'student_id',     'students'),
            ('timetable_slots',        'assignment_id',  'teacher_assignments'),
            ('receipts',               'invoice_id',     'invoices'),
            ('receipts',               'fee_payment_id', 'fee_payments'),
            ('financial_transactions', 'fee_payment_id', 'fee_payments'),
            ('financial_transactions', 'student_id',     'students'),
            ('student_admissions',     'classroom_id',   'classrooms'),
            ('student_admissions',     'student_id',     'students')
        ) AS t(child, col, parent)
    LOOP
        EXECUTE format(
            'SELECT count(*) FROM %I c LEFT JOIN %I p ' ||
            'ON p.id = c.%I AND p.school_id = c.school_id ' ||
            'WHERE c.%I IS NOT NULL AND p.id IS NULL',
            pair.child, pair.parent, pair.col, pair.col)
        INTO offenders;
        IF offenders > 0 THEN
            refused := refused || format('%s.%s -> %s (%s straddled rows); ',
                pair.child, pair.col, pair.parent, offenders);
        END IF;
    END LOOP;
    IF refused <> '' THEN
        RAISE EXCEPTION 'straddled references present; repair before migrating: %', refused;
    END IF;
END $$;

-- The V1-era single-column foreign keys are dropped by the names V1 itself
-- generated, then re-created as pairs. A fresh database replays V1 verbatim and
-- therefore produces these exact names.
ALTER TABLE students DROP CONSTRAINT fkn4i882kjg6fdyg2e641yh3jmk;
ALTER TABLE students ADD CONSTRAINT fk_students_classroom_same_school
    FOREIGN KEY (classroom_id, school_id) REFERENCES classrooms (id, school_id);

ALTER TABLE students DROP CONSTRAINT fkbu72kq4xd8qjcemytgfxel71l;
ALTER TABLE students ADD CONSTRAINT fk_students_section_same_school
    FOREIGN KEY (section_id, school_id) REFERENCES sections (id, school_id);

ALTER TABLE users DROP CONSTRAINT fk6p2ib82uai0pj9yk1iassppgq;
ALTER TABLE users ADD CONSTRAINT fk_users_employee_same_school
    FOREIGN KEY (employee_id, school_id) REFERENCES employees (id, school_id);

ALTER TABLE users DROP CONSTRAINT fkc8nfkx91xbh5fv7a02092q1ip;
ALTER TABLE users ADD CONSTRAINT fk_users_student_same_school
    FOREIGN KEY (student_id, school_id) REFERENCES students (id, school_id);

ALTER TABLE timetable_slots DROP CONSTRAINT fkb8t1vrkwq85o22laayslj3u1d;
ALTER TABLE timetable_slots ADD CONSTRAINT fk_slot_assignment_same_school
    FOREIGN KEY (assignment_id, school_id) REFERENCES teacher_assignments (id, school_id);

ALTER TABLE receipts DROP CONSTRAINT fk3hmid8b40s5yd0jo2s36684ql;
ALTER TABLE receipts ADD CONSTRAINT fk_receipt_invoice_same_school
    FOREIGN KEY (invoice_id, school_id) REFERENCES invoices (id, school_id);

ALTER TABLE receipts DROP CONSTRAINT fkiu5u47yfk7jnngociyi8e79ct;
ALTER TABLE receipts ADD CONSTRAINT fk_receipt_fee_payment_same_school
    FOREIGN KEY (fee_payment_id, school_id) REFERENCES fee_payments (id, school_id);

ALTER TABLE financial_transactions DROP CONSTRAINT fkj4ikaup5q40fimq2c5f3mp122;
ALTER TABLE financial_transactions ADD CONSTRAINT fk_fin_tx_fee_payment_same_school
    FOREIGN KEY (fee_payment_id, school_id) REFERENCES fee_payments (id, school_id);

ALTER TABLE financial_transactions DROP CONSTRAINT fk8uaqkwaltxslh6xjlbidytqy5;
ALTER TABLE financial_transactions ADD CONSTRAINT fk_fin_tx_student_same_school
    FOREIGN KEY (student_id, school_id) REFERENCES students (id, school_id);

ALTER TABLE student_admissions DROP CONSTRAINT fkt0w19x26sowme4rh44xtjwo7b;
ALTER TABLE student_admissions ADD CONSTRAINT fk_admission_classroom_same_school
    FOREIGN KEY (classroom_id, school_id) REFERENCES classrooms (id, school_id);

ALTER TABLE student_admissions DROP CONSTRAINT fkc3l5tvg8357u60dtyfn4ve2ou;
ALTER TABLE student_admissions ADD CONSTRAINT fk_admission_student_same_school
    FOREIGN KEY (student_id, school_id) REFERENCES students (id, school_id);
