-- Remember which expiring credit groups have already produced an advance notice.
-- The timestamp is committed together with the CreditsExpiring event publication,
-- preventing repeated daily or concurrent job runs from announcing a group twice.
ALTER TABLE wallet.credit_lots
    ADD COLUMN expiry_notice_sent_at timestamptz;

CREATE INDEX ix_credit_lots_expiry_notice
    ON wallet.credit_lots (tenant_id, expires_at)
    WHERE remaining_amount > 0 AND expiry_notice_sent_at IS NULL;
