-- =============================================================================
-- Audit: the activity log, chained (US48, detecting tampering)
-- =============================================================================
-- The triggers of V202610122133 refuse UPDATE, DELETE and TRUNCATE, but whoever
-- owns the table can drop them, and a restore from a doctored backup never meets
-- them. From now on every entry carries the hash of the one before it in its
-- university, the same mechanism as wallet.ledger_entries, so an entry changed,
-- removed or slipped in behind the application no longer fits the sequence.
--
-- sequence_number is the order in which the entries were written, which is the
-- order of the chain. occurred_at cannot be: it is the moment the event says the
-- fact happened, and an event is written down after its publisher commits, so a
-- later entry may carry an earlier moment. The application assigns it under a
-- lock held per university, never with a database sequence, because a sequence
-- leaves gaps after a rollback and a gap looks exactly like a removed entry.
--
-- entry_hash is the SHA-256, in hexadecimal, of the fields of the entry plus
-- previous_hash. previous_hash is NULL for the first entry of a chain.
--
-- The three columns are nullable on purpose. The entries written before this
-- migration have no hash and keep none: nothing may edit them, and inventing a
-- hash for them now would vouch for rows nobody checked when they were written.
-- The chain of a university starts at its first entry with a hash. The check
-- keeps an entry either chained as a whole or not chained at all.
--
-- The append only triggers do not change.
-- =============================================================================

ALTER TABLE audit.activity_log
    ADD COLUMN sequence_number bigint,
    ADD COLUMN previous_hash   varchar(64),
    ADD COLUMN entry_hash      varchar(64);

ALTER TABLE audit.activity_log
    ADD CONSTRAINT ck_activity_log_chained_whole
        CHECK ((sequence_number IS NULL) = (entry_hash IS NULL));

-- One place per position of a university's chain. NULLs do not collide, so the
-- entries from before the chain are left alone. It is also the index the
-- verification walks, a page at a time, and the one the writer reads the tail of
-- the chain from.
CREATE UNIQUE INDEX uq_activity_log_sequence
    ON audit.activity_log (tenant_id, sequence_number);
