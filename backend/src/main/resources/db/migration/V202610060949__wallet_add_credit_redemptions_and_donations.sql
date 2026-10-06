-- Campus benefits and confirmed earned-credit uses.
CREATE TABLE wallet.campus_benefits (
  id           uuid         PRIMARY KEY,
  tenant_id    varchar(32)  NOT NULL,
  name         varchar(120) NOT NULL,
  description  varchar(500) NOT NULL,
  credits_cost integer      NOT NULL,
  active       boolean      NOT NULL DEFAULT true,
  created_at   timestamptz  NOT NULL DEFAULT now(),
  updated_at   timestamptz  NOT NULL DEFAULT now(),
  CONSTRAINT ck_campus_benefits_name CHECK (length(trim(name)) > 0),
  CONSTRAINT ck_campus_benefits_description CHECK (length(trim(description)) > 0),
  CONSTRAINT ck_campus_benefits_credits_cost CHECK (credits_cost > 0)
);

CREATE INDEX ix_campus_benefits_available
  ON wallet.campus_benefits (tenant_id, name)
  WHERE active = true;

CREATE TABLE wallet.credit_uses (
  id              uuid         PRIMARY KEY,
  tenant_id       varchar(32)  NOT NULL,
  student_id      uuid         NOT NULL,
  kind            varchar(32)  NOT NULL,
  benefit_id      uuid REFERENCES wallet.campus_benefits(id),
  benefit_name    varchar(120),
  credits         integer      NOT NULL,
  idempotency_key varchar(64)  NOT NULL,
  created_at      timestamptz  NOT NULL DEFAULT now(),
  CONSTRAINT uq_credit_uses_idempotency
    UNIQUE (tenant_id, student_id, idempotency_key),
  CONSTRAINT ck_credit_uses_kind
    CHECK (kind IN ('CAMPUS_BENEFIT_REDEMPTION','INCOMING_STUDENT_DONATION')),
  CONSTRAINT ck_credit_uses_credits CHECK (credits > 0),
  CONSTRAINT ck_credit_uses_idempotency_key CHECK (length(trim(idempotency_key)) > 0),
  CONSTRAINT ck_credit_uses_benefit
    CHECK (
      (kind = 'CAMPUS_BENEFIT_REDEMPTION' AND benefit_id IS NOT NULL AND benefit_name IS NOT NULL)
      OR
      (kind = 'INCOMING_STUDENT_DONATION' AND benefit_id IS NULL AND benefit_name IS NULL)
    )
);

CREATE INDEX ix_credit_uses_donation_pool
  ON wallet.credit_uses (tenant_id, created_at)
  WHERE kind = 'INCOMING_STUDENT_DONATION';

CREATE INDEX ix_credit_uses_student_history
  ON wallet.credit_uses (tenant_id, student_id, created_at DESC);

ALTER TABLE wallet.ledger_entries
  ALTER COLUMN reference_type TYPE varchar(40);

ALTER TABLE wallet.ledger_entries
  DROP CONSTRAINT ck_ledger_entries_reason,
  ADD CONSTRAINT ck_ledger_entries_reason
    CHECK (
      reason IN (
        'GRANT',
        'BOOKING_CHARGE',
        'BOOKING_REFUND',
        'SESSION_EARNING',
        'EXPIRY',
        'PURCHASE',
        'CAMPUS_BENEFIT_REDEMPTION',
        'INCOMING_STUDENT_DONATION',
        'ADJUSTMENT'
      )
    );

ALTER TABLE wallet.ledger_entries
  DROP CONSTRAINT ck_ledger_entries_reference,
  ADD CONSTRAINT ck_ledger_entries_reference
    CHECK (
      reference_type IN (
        'BOOKING',
        'SESSION',
        'PURCHASE',
        'POLICY',
        'CAMPUS_BENEFIT_REDEMPTION',
        'INCOMING_STUDENT_DONATION'
      )
    );
