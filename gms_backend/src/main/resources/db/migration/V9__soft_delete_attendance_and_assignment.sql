-- ─────────────────────────────────────────────────────────────────────────────
-- V9: soft-delete columns for the two hard-deleting endpoints (findings AUD-01..05)
-- ─────────────────────────────────────────────────────────────────────────────
-- Five DELETE endpoints removed their row outright, destroying the authorship and
-- timestamps the audit trail reads. Three of them (subjects, classrooms, users)
-- already carried a lifecycle flag and now use it. These two did not: attendance's
-- status column is a domain value (PRESENT/ABSENT/LATE), and teacher_assignments
-- has no status at all. The flag is its own column, and the entities pair it with
-- @SQLDelete / @SQLRestriction so repository.delete() writes the flag and every
-- JPA read skips the row.

ALTER TABLE attendances
    ADD COLUMN deleted boolean NOT NULL DEFAULT false;

ALTER TABLE teacher_assignments
    ADD COLUMN deleted boolean NOT NULL DEFAULT false;
