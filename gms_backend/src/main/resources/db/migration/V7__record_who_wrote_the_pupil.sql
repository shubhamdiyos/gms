-- V7__record_who_wrote_the_pupil.sql
--
-- MVP feature 11 is "audit logging: the write records WHO wrote it". The application has the mechanism
-- since the JPA-auditing card — `AuditConfiguration`'s `AuditorAware` plus `@CreatedBy` /
-- `@LastModifiedBy` on `BaseAuditEntity` — and 31 of V1's 38 tables already had the columns. Five of the
-- busiest were not fully shaped: `schools` carried `created_by` / `updated_by` in the baseline but no entity
-- annotation wrote them, and `students`, `classrooms`, `subjects` and `student_admissions` had no such column
-- at all, so the
-- question "who registered this pupil" had no answer in the database to give. Measured by
-- `scripts/verify-mvp-e2e.sh` before this file, over the four tables the MVP journey writes through:
--
--     SELECT count(*) FROM information_schema.columns
--      WHERE column_name = 'created_by'
--        AND table_name IN ('schools','students','classrooms','subjects');   -- 1 of 4
--
-- The types are the baseline's, exactly: `integer`, nullable, foreign key to `users(id)`. Nothing about an
-- author is wider than the account that is one, and `users.id` is `integer`.

-- ── 1. The columns ───────────────────────────────────────────────────────────────────────────────

ALTER TABLE public.students           ADD COLUMN IF NOT EXISTS created_by integer;
ALTER TABLE public.students           ADD COLUMN IF NOT EXISTS updated_by integer;
ALTER TABLE public.classrooms         ADD COLUMN IF NOT EXISTS created_by integer;
ALTER TABLE public.classrooms         ADD COLUMN IF NOT EXISTS updated_by integer;
ALTER TABLE public.subjects           ADD COLUMN IF NOT EXISTS created_by integer;
ALTER TABLE public.subjects           ADD COLUMN IF NOT EXISTS updated_by integer;
ALTER TABLE public.student_admissions ADD COLUMN IF NOT EXISTS created_by integer;
ALTER TABLE public.student_admissions ADD COLUMN IF NOT EXISTS updated_by integer;

-- ── 2. The keys ───────────────────────────────────────────────────────────────────────────────────
-- The same shape as the 62 audit keys already in `V1`, on those 31 tables: `REFERENCES public.users(id)` with the default
-- `NO ACTION`, so an account that has written rows cannot be deleted out from under them. That is a
-- deliberate difference from `ON DELETE SET NULL`, which would let a deletion erase the trail while leaving
-- the rows standing. The baseline's rule is that history outranks the account that made it; these four
-- tables join that rule rather than inventing a second one.

ALTER TABLE public.students
    ADD CONSTRAINT fk_students_created_by FOREIGN KEY (created_by) REFERENCES public.users(id);
ALTER TABLE public.students
    ADD CONSTRAINT fk_students_updated_by FOREIGN KEY (updated_by) REFERENCES public.users(id);
ALTER TABLE public.classrooms
    ADD CONSTRAINT fk_classrooms_created_by FOREIGN KEY (created_by) REFERENCES public.users(id);
ALTER TABLE public.classrooms
    ADD CONSTRAINT fk_classrooms_updated_by FOREIGN KEY (updated_by) REFERENCES public.users(id);
ALTER TABLE public.subjects
    ADD CONSTRAINT fk_subjects_created_by FOREIGN KEY (created_by) REFERENCES public.users(id);
ALTER TABLE public.subjects
    ADD CONSTRAINT fk_subjects_updated_by FOREIGN KEY (updated_by) REFERENCES public.users(id);
ALTER TABLE public.student_admissions
    ADD CONSTRAINT fk_admissions_created_by FOREIGN KEY (created_by) REFERENCES public.users(id);
ALTER TABLE public.student_admissions
    ADD CONSTRAINT fk_admissions_updated_by FOREIGN KEY (updated_by) REFERENCES public.users(id);

-- ── 3. Why nothing is backfilled ──────────────────────────────────────────────────────────────────
-- Every row this file touches predates it, and no column on those tables records who wrote them — not
-- `created_by` (absent, which is why this file exists) and not any actor column that could stand in for
-- it. An `UPDATE ... SET created_by = 1` would have manufactured an author for a write nobody observed,
-- which is the one thing an audit trail must never do: it turns a gap into a false statement, and the
-- false statement is indistinguishable from the real one later. So the columns stay NULL for history and
-- become non-NULL for everything written after this deploy, by `AuditingEntityListener`, from the verified
-- subject of the request. A query over the trail should read a NULL author as "unknown", which is the truth.
--
-- The Java half of this card is `AuthorshipEntity`: `Student`, `Classroom`, `Subject`,
-- `StudentAdmission` and `School` extend it and inherit `@CreatedBy` / `@LastModifiedBy`. They cannot
-- extend `BaseAuditEntity`, because that class also maps `created_on` / `updated_on` and these five tables
-- put their timestamps in `created_at` / `updated_at` — `ddl-auto=validate` would have failed startup on a
-- column with no counterpart, and adding the second pair would have given one event two owners.
-- `BaseAuditEntity` now extends `AuthorshipEntity`, so "who" is declared exactly once for all thirty-nine
-- audited tables.
