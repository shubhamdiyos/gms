-- Daily attendance register: a school must be able to record one attendance mark per
-- student per day (the morning roll call) without a timetable period existing for that
-- weekday. Period attendance stays exactly as it was; a daily record is simply an
-- attendance row whose timetable_slot_id is NULL.
--
-- The existing unique key uq_attendance_student_slot_date (student_id, timetable_slot_id,
-- date) cannot guard daily rows — NULL slots never conflict in PostgreSQL — so daily
-- uniqueness gets its own partial index over live rows only (a soft-deleted daily row
-- must not block re-marking the day).

ALTER TABLE attendances ALTER COLUMN timetable_slot_id DROP NOT NULL;

CREATE UNIQUE INDEX uq_attendance_daily_student_date
    ON attendances (student_id, date)
    WHERE timetable_slot_id IS NULL AND deleted = false;
