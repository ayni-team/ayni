-- =============================================================================
-- ADR 0006, amended 2026-10-23 - The cycle a student is in
-- =============================================================================
-- The academic system now says the cycle of the career each student is in,
-- next to the career and the term. It is the university's data, read only in
-- Ayni like the other two: written when the student is activated and every
-- time the record is imported again.
--
-- NULL for a coordinator, and for a student activated before this column
-- existed until they import their record again. The table already has row
-- level security; a new column needs nothing more.
-- =============================================================================

ALTER TABLE identity.users
    ADD COLUMN current_cycle smallint NULL;
