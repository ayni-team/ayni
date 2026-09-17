-- wallet: credit accounts, credit groups and ledger entries
CREATE SCHEMA IF NOT EXISTS wallet;

CREATE TABLE wallet.credit_accounts (
  id          uuid         PRIMARY KEY,
  tenant_id   varchar(32)  NOT NULL,
  user_id     uuid         NOT NULL,
  created_at  timestamptz  NOT NULL DEFAULT now(),
  CONSTRAINT uq_credit_accounts_user UNIQUE (tenant_id, user_id)
);

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
CREATE INDEX ix_credit_lots_spendable ON wallet.credit_lots (tenant_id, account_id, expires_at) WHERE remaining_amount > 0;

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
CREATE INDEX ix_ledger_entries_account ON wallet.ledger_entries (tenant_id, account_id, occurred_at DESC);

-- The ledger is append only: corrections are new ADJUSTMENT entries.
CREATE FUNCTION wallet.reject_ledger_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'wallet.ledger_entries is append only';
END;
$$;
CREATE TRIGGER tr_ledger_entries_append_only
  BEFORE UPDATE OR DELETE ON wallet.ledger_entries
  FOR EACH ROW EXECUTE FUNCTION wallet.reject_ledger_change();
