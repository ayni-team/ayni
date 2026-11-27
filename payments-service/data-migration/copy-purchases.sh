#!/usr/bin/env bash
# Copies the credit purchases the monolith kept (payments.purchases of the database ayni) into the
# database of the payments service (payments.purchases of ayni_payments), ADR 0011.
#
#   SOURCE_DATABASE_URL='postgresql://ayni@host:5432/ayni?sslmode=require' \
#   TARGET_DATABASE_URL='postgresql://ayni_payments@host:5432/ayni_payments?sslmode=require' \
#   payments-service/data-migration/copy-purchases.sh
#
# Both are libpq URLs (psql), not JDBC ones; PGPASSWORD or ~/.pgpass supplies the passwords. The
# source role must be able to read every university's rows: the owner of the monolith's schemas
# (AYNI_DB_USER), which row level security does not restrict. The target role must be the owner
# of ayni_payments (AYNI_PAYMENTS_DB_USER), for the same reason. The service must have started
# once against the target, so its migrations created the table.
#
# It is safe to run again: a purchase already copied is left as it is (on conflict do nothing),
# and the counts at the end compare both sides by university and status. It never writes to the
# source. Run it with the backend stopped, so no purchase changes while it copies (deploy/README.md,
# "Moving the purchases to the payments service").
#
# Confirmed purchases were already credited by the monolith, so nothing is added to the outbox for
# them. Pending ones are resolved by the service's reconciliation, whose confirmation goes through
# the outbox and is credited once like any other.

set -euo pipefail

: "${SOURCE_DATABASE_URL:?Set SOURCE_DATABASE_URL to the monolith database (ayni)}"
: "${TARGET_DATABASE_URL:?Set TARGET_DATABASE_URL to the payments service database (ayni_payments)}"

columns="id, tenant_id, student_id, credits, amount, currency, status, provider_reference, idempotency_key, confirmed_at, expires_at, created_at"

counts() {
  psql "$1" -X -q -At -F ' ' -v ON_ERROR_STOP=1 -c "
    select tenant_id, status, count(*), coalesce(sum(credits), 0)
    from payments.purchases group by tenant_id, status order by tenant_id, status"
}

echo "==> Purchases in the monolith"
source_counts="$(counts "$SOURCE_DATABASE_URL")"
echo "${source_counts:-(none)}"

echo "==> Copying"
psql "$SOURCE_DATABASE_URL" -X -q -v ON_ERROR_STOP=1 \
  -c "\\copy (select $columns from payments.purchases order by created_at) to stdout with (format csv)" |
  psql "$TARGET_DATABASE_URL" -X -q -v ON_ERROR_STOP=1 \
    -c "create temporary table incoming (like payments.purchases including defaults)" \
    -c "\\copy incoming ($columns) from stdin with (format csv)" \
    -c "insert into payments.purchases ($columns)
          select $columns from incoming on conflict (id) do nothing"

echo "==> Purchases in the payments service"
target_counts="$(counts "$TARGET_DATABASE_URL")"
echo "${target_counts:-(none)}"

# Every row of the monolith is in the service. The service may have more, its own new purchases,
# so the comparison is by purchase, not by total.
missing="$(
  psql "$SOURCE_DATABASE_URL" -X -q -At -v ON_ERROR_STOP=1 \
    -c "\\copy (select id from payments.purchases) to stdout" |
  psql "$TARGET_DATABASE_URL" -X -q -At -v ON_ERROR_STOP=1 \
    -c "create temporary table expected (id uuid primary key)" \
    -c "\\copy expected from stdin" \
    -c "select count(*) from expected e
          where not exists (select 1 from payments.purchases p where p.id = e.id)" | tail -n 1
)"
if [[ "$missing" != "0" ]]; then
  echo "!! $missing purchases of the monolith are not in the payments service" >&2
  exit 1
fi
echo "==> Done: every purchase of the monolith is in the payments service"
