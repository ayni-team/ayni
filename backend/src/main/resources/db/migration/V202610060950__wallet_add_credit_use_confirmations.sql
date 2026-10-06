CREATE TABLE wallet.credit_use_confirmations (
  id          uuid         PRIMARY KEY,
  tenant_id   varchar(32)  NOT NULL,
  student_id  uuid         NOT NULL,
  kind        varchar(32)  NOT NULL,
  benefit_id  uuid REFERENCES wallet.campus_benefits(id),
  benefit_name varchar(120),
  credits     integer      NOT NULL,
  expires_at  timestamptz  NOT NULL,
  confirmed_at timestamptz,
  CONSTRAINT ck_credit_use_confirmations_kind
    CHECK (kind IN ('CAMPUS_BENEFIT_REDEMPTION','INCOMING_STUDENT_DONATION')),
  CONSTRAINT ck_credit_use_confirmations_credits CHECK (credits > 0),
  CONSTRAINT ck_credit_use_confirmations_benefit
    CHECK (
      (kind = 'CAMPUS_BENEFIT_REDEMPTION' AND benefit_id IS NOT NULL AND benefit_name IS NOT NULL)
      OR
      (kind = 'INCOMING_STUDENT_DONATION' AND benefit_id IS NULL AND benefit_name IS NULL)
    )
);

CREATE INDEX ix_credit_use_confirmations_owner
  ON wallet.credit_use_confirmations (tenant_id, student_id, expires_at);
