ALTER TABLE payments.purchases
  ADD COLUMN expires_at timestamptz;

UPDATE payments.purchases
SET expires_at = created_at + interval '24 hours'
WHERE expires_at IS NULL;

ALTER TABLE payments.purchases
  ALTER COLUMN expires_at SET NOT NULL;

CREATE INDEX ix_purchases_pending
  ON payments.purchases (tenant_id, status, created_at)
  WHERE status = 'PENDING';
