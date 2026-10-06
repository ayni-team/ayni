-- Credit purchases belong to payments; the wallet receives only PurchaseConfirmed.
CREATE TABLE payments.purchases (
  id                  uuid          PRIMARY KEY,
  tenant_id           varchar(32)   NOT NULL,
  student_id          uuid          NOT NULL,
  credits             integer       NOT NULL,
  amount              numeric(10,2) NOT NULL,
  currency            varchar(3)    NOT NULL DEFAULT 'PEN',
  status              varchar(16)   NOT NULL,
  provider_reference  varchar(128),
  idempotency_key     varchar(64)   NOT NULL,
  confirmed_at        timestamptz,
  created_at          timestamptz   NOT NULL DEFAULT now(),
  CONSTRAINT uq_purchases_idempotency
    UNIQUE (tenant_id, student_id, idempotency_key),
  CONSTRAINT ck_purchases_credits_amount
    CHECK (credits > 0 AND amount > 0),
  CONSTRAINT ck_purchases_status
    CHECK (status IN ('PENDING','CONFIRMED','FAILED','EXPIRED')),
  CONSTRAINT ck_purchases_confirmation
    CHECK ((status = 'CONFIRMED') = (confirmed_at IS NOT NULL))
);

CREATE INDEX ix_purchases_student_created
  ON payments.purchases (tenant_id, student_id, created_at DESC);

CREATE INDEX ix_purchases_confirmed_cycle
  ON payments.purchases (tenant_id, student_id, confirmed_at)
  WHERE status = 'CONFIRMED';
