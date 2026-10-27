-- =============================================================================
-- V202609201030 - Wallet: credit accounts, credit groups and the ledger
-- =============================================================================
-- Three tables that together answer two different questions:
--
--   How much can this student spend?  ->  the sum of what is left in the groups
--   What happened to their credits?   ->  the ledger, one row per movement
--
-- The balance is never stored. A stored balance and a history of movements are
-- two truths that drift apart on the first failed transaction, and the one that
-- can be recomputed is the one that has to go.
--
-- Column for column as docs/database/data-model.md specifies them, checked
-- against PostgreSQL 16. The schema itself is created by V1.
-- =============================================================================

CREATE TABLE wallet.credit_accounts (
  id          uuid         PRIMARY KEY,
  tenant_id   varchar(32)  NOT NULL,
  user_id     uuid         NOT NULL,
  created_at  timestamptz  NOT NULL DEFAULT now(),
  CONSTRAINT uq_credit_accounts_user UNIQUE (tenant_id, user_id)
);

COMMENT ON TABLE wallet.credit_accounts IS
  'One account per student and university. No balance column: it is derived from the groups.';

-- -----------------------------------------------------------------------------
-- Credits arrive in groups, because the group is what carries the origin and
-- the expiry. Spending takes from the group closest to expiring; a refund
-- returns the credits to the group they came from, with the expiry they had.
-- -----------------------------------------------------------------------------
CREATE TABLE wallet.credit_lots (
  id                uuid         PRIMARY KEY,
  tenant_id         varchar(32)  NOT NULL,
  account_id        uuid         NOT NULL REFERENCES wallet.credit_accounts(id),
  credit_type       varchar(16)  NOT NULL,
  original_amount   integer      NOT NULL,
  remaining_amount  integer      NOT NULL,
  expires_at        timestamptz,
  source_type       varchar(24)  NOT NULL,
  source_id         uuid,
  created_at        timestamptz  NOT NULL DEFAULT now(),
  CONSTRAINT ck_credit_lots_type CHECK (credit_type IN ('SEED','ALLOCATED','EARNED','PURCHASED')),
  CONSTRAINT ck_credit_lots_original CHECK (original_amount > 0),
  CONSTRAINT ck_credit_lots_remaining CHECK (remaining_amount BETWEEN 0 AND original_amount),
  CONSTRAINT ck_credit_lots_expiry CHECK ((expires_at IS NULL) = (credit_type IN ('EARNED','PURCHASED'))),
  CONSTRAINT ck_credit_lots_source CHECK (source_type IN ('POLICY','SESSION','PURCHASE','REFUND'))
);

-- The expiry rule of the product, written where it cannot be broken: only what
-- the university grants expires. What you earned and what you paid for are yours.
COMMENT ON CONSTRAINT ck_credit_lots_expiry ON wallet.credit_lots IS
  'Only SEED and ALLOCATED expire. EARNED and PURCHASED never do.';

-- Covers the query every charge makes: the spendable groups of one account,
-- in expiry order. Partial, because a spent group is never looked at again.
CREATE INDEX ix_credit_lots_spendable
  ON wallet.credit_lots (tenant_id, account_id, expires_at)
  WHERE remaining_amount > 0;

-- -----------------------------------------------------------------------------
-- Every movement, in order, immutable.
-- -----------------------------------------------------------------------------
CREATE TABLE wallet.ledger_entries (
  id               uuid         PRIMARY KEY,
  tenant_id        varchar(32)  NOT NULL,
  account_id       uuid         NOT NULL REFERENCES wallet.credit_accounts(id),
  lot_id           uuid         REFERENCES wallet.credit_lots(id),
  sequence_number  bigint       NOT NULL,
  direction        varchar(8)   NOT NULL,
  amount           integer      NOT NULL,
  reason           varchar(32)  NOT NULL,
  reference_type   varchar(24),
  reference_id     uuid,
  previous_hash    varchar(64),
  entry_hash       varchar(64)  NOT NULL,
  occurred_at      timestamptz  NOT NULL DEFAULT now(),
  CONSTRAINT uq_ledger_entries_sequence UNIQUE (tenant_id, sequence_number),
  CONSTRAINT ck_ledger_entries_amount CHECK (amount > 0),
  CONSTRAINT ck_ledger_entries_direction CHECK (direction IN ('DEBIT','CREDIT')),
  CONSTRAINT ck_ledger_entries_reason CHECK (reason IN ('GRANT','BOOKING_CHARGE','BOOKING_REFUND','SESSION_EARNING','EXPIRY','PURCHASE','ADJUSTMENT')),
  CONSTRAINT ck_ledger_entries_reference CHECK (reference_type IN ('BOOKING','SESSION','PURCHASE','POLICY'))
);

COMMENT ON COLUMN wallet.ledger_entries.sequence_number IS
  'Assigned by the application inside the transaction, not by a sequence: a sequence leaves gaps after a rollback and a gap looks exactly like a deleted row.';
COMMENT ON COLUMN wallet.ledger_entries.entry_hash IS
  'SHA-256 of the entry fields plus previous_hash, chained per university. A row changed behind the application breaks the chain from that point on.';

CREATE INDEX ix_ledger_entries_account
  ON wallet.ledger_entries (tenant_id, account_id, occurred_at DESC);

-- -----------------------------------------------------------------------------
-- The ledger is append only: corrections are new ADJUSTMENT entries.
--
-- The trigger is what makes that a property of the database rather than a habit
-- of the code. Anyone with a psql prompt is refused too, which is the point.
-- -----------------------------------------------------------------------------
CREATE FUNCTION wallet.reject_ledger_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'wallet.ledger_entries is append only';
END;
$$;

CREATE TRIGGER tr_ledger_entries_append_only
  BEFORE UPDATE OR DELETE ON wallet.ledger_entries
  FOR EACH ROW EXECUTE FUNCTION wallet.reject_ledger_change();
