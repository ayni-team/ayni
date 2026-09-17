-- payments: credit purchases
CREATE SCHEMA IF NOT EXISTS payments;

CREATE TABLE payments.purchases (
  id                  uuid           PRIMARY KEY,
  tenant_id           varchar(32)    NOT NULL,
  student_id          uuid           NOT NULL,
  credits             integer        NOT NULL,
  amount              numeric(10,2)  NOT NULL,
  currency            varchar(3)     NOT NULL DEFAULT 'PEN',
  status              varchar(16)    NOT NULL,
  provider_reference  varchar(128),
  idempotency_key     varchar(64)    NOT NULL UNIQUE,
  confirmed_at        timestamptz,
  created_at          timestamptz    NOT NULL DEFAULT now(),
  CONSTRAINT ck_purchases_amounts CHECK (credits > 0 AND amount > 0),
  CONSTRAINT ck_purchases_status CHECK (status IN ('PENDING','CONFIRMED','FAILED','EXPIRED'))
);
CREATE INDEX ix_purchases_student ON payments.purchases (tenant_id, student_id, created_at DESC);
