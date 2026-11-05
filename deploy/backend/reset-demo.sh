#!/usr/bin/env bash
# Empties the database of a demonstration installation and seeds it again with the people of the
# demo profile, the same data a laptop gets with SPRING_PROFILES_ACTIVE=demo (docs/demo.md). Every
# person is invented, so nothing real is lost, and the dates are those of the day it runs: the last
# four weeks of history end today and the upcoming tutorings start in two to five days.
#
# The reset demo data workflow runs it every three days through az vm run-command; by hand, from
# this folder of a checkout on the backend machine:
#
#     ./deploy/backend/reset-demo.sh
#
# It
#
#   1. refuses to run unless deploy/backend/.env says AYNI_DEMO_DATA_RESET=true and the emails go to
#      Mailpit (the people of the documents have addresses of real university domains),
#   2. takes a pg_dump of the database into AYNI_BACKUP_DIR, keeping the last three,
#   3. stops the backend, so the jobs never run twice over the same database,
#   4. drops, as the owner role, every schema and table the migrations created,
#   5. starts a one-off container of the same image with the profiles prod,demo, which migrates
#      and seeds, and stops it once the history is played and every event is delivered,
#   6. starts the backend again with prod alone, whatever happened before.
#
# The one-off container publishes no port and is not reachable under the name of the backend, so
# the proxy never sends it a request: the routes of the demo profile (/api/v1/admin/demo/*) and its
# movable clock never face the Internet. Never set AYNI_DEMO_DATA_RESET on an installation for real
# people.

set -euo pipefail

cd "${AYNI_STACK_DIR:-$(dirname "$0")}"
export COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"

backups="${AYNI_BACKUP_DIR:-$HOME/ayni-backups}"
keep=3
seed=ayni-seed
network=ayni-edge
client=postgres:16-alpine

if [[ ! -f .env ]]; then
  echo "deploy/backend/.env does not exist: run this on the backend machine" >&2
  exit 1
fi

# One value of .env, without the quotes around it. The file is not sourced: unquoted values such as
# AYNI_MAIL_FROM=Ayni <no-reply@...> are not shell.
env_value() {
  sed -n "s/^$1=//p" .env | tail -n 1 | sed -e "s/^'\(.*\)'\$/\1/" -e 's/^"\(.*\)"$/\1/'
}

if [[ "$(env_value AYNI_DEMO_DATA_RESET)" != "true" ]]; then
  echo "AYNI_DEMO_DATA_RESET=true is not in deploy/backend/.env: this installation keeps its data" >&2
  exit 2
fi
if [[ "$(env_value AYNI_MAIL_HOST)" != "mailpit" ]]; then
  echo "AYNI_MAIL_HOST is not mailpit: the seeding would send email to real university domains" >&2
  exit 2
fi

url="$(env_value AYNI_DB_URL)"
if [[ ! "$url" =~ ^jdbc:postgresql://([^:/?]+)(:([0-9]+))?/([^?]+)(\?(.*))?$ ]]; then
  echo "AYNI_DB_URL is not a jdbc:postgresql:// address" >&2
  exit 1
fi
host="${BASH_REMATCH[1]}"
port="${BASH_REMATCH[3]:-5432}"
database="${BASH_REMATCH[4]}"
sslmode=prefer
if [[ "${BASH_REMATCH[6]}" =~ (^|&)sslmode=([a-z-]+) ]]; then
  sslmode="${BASH_REMATCH[2]}"
fi
connection="host=$host port=$port dbname=$database user=$(env_value AYNI_DB_USER) sslmode=$sslmode"

# The owner's password reaches each client container by name (-e PGPASSWORD), never on a command
# line where ps or the log would show it.
PGPASSWORD="$(env_value AYNI_DB_PASSWORD)"
export PGPASSWORD

psql_owner() {
  docker run --rm -i -e PGPASSWORD --network "$network" "$client" \
    psql "$connection" -v ON_ERROR_STOP=1 -q -At "$@"
}

# Deletes all but the newest $keep files of a pattern in the folder of the backups. The pattern is
# expanded on purpose, and the names are this script's own stamps, with no spaces.
# shellcheck disable=SC2012,SC2086
rotate() {
  ls -1t "$backups"/$1 2>/dev/null | tail -n +$((keep + 1)) | while read -r old; do
    rm -f -- "$old"
  done
}

restart_backend() {
  # After a failure too, the log of the seeding is kept before its container goes.
  if [[ ! -f "$backups/seed-$stamp.log" ]] && docker inspect "$seed" >/dev/null 2>&1; then
    docker logs "$seed" > "$backups/seed-$stamp.log" 2>&1 || true
    echo "The log of the seeding: $backups/seed-$stamp.log" >&2
  fi
  docker rm -f "$seed" >/dev/null 2>&1 || true
  echo "==> Starting the backend with the prod profile"
  docker compose up -d --wait --wait-timeout 300 backend
}

stamp="$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$backups"
# Azure keeps only the last 4 KB of what a run command prints: no progress bars of a pull.
docker pull -q "$client" >/dev/null

echo "==> Backing up the database to $backups/ayni-$stamp-before-reset.dump"
docker run --rm -e PGPASSWORD --network "$network" --user "$(id -u):$(id -g)" \
  -v "$backups:/backups" "$client" \
  pg_dump "$connection" --format=custom --file="/backups/ayni-$stamp-before-reset.dump"
rotate 'ayni-*-before-reset.dump'

docker rm -f "$seed" >/dev/null 2>&1 || true
echo "==> Stopping the backend"
docker compose stop backend
trap restart_backend EXIT

# One statement, so one transaction: either everything goes or nothing does. The triggers that keep
# the ledger and the activity log append only refuse UPDATE and DELETE, not DROP. The roles and the
# database stay, and the schema public with them; the first migration grants everything again.
echo "==> Emptying the database"
psql_owner <<'SQL'
SET client_min_messages = warning;
DO $$
DECLARE
  item record;
BEGIN
  FOR item IN
    SELECT nspname FROM pg_namespace
    WHERE nspowner = (SELECT oid FROM pg_roles WHERE rolname = current_user)
      AND nspname <> 'public'
  LOOP
    EXECUTE format('DROP SCHEMA %I CASCADE', item.nspname);
  END LOOP;
  FOR item IN
    SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p')
      AND pg_get_userbyid(c.relowner) = current_user
  LOOP
    EXECUTE format('DROP TABLE IF EXISTS public.%I CASCADE', item.relname);
  END LOOP;
END
$$;
SQL

echo "==> Seeding with the profiles prod,demo"
docker compose run -d --no-deps --name "$seed" -e SPRING_PROFILES_ACTIVE=prod,demo backend >/dev/null

# The last thing the demo profile does at startup is play the history; it logs this line at the end,
# also when part of it failed. The first start migrates everything, hence the long wait.
deadline=$((SECONDS + 900))
until docker logs "$seed" 2>&1 | grep -q 'Demo history done'; do
  if [[ "$(docker inspect -f '{{.State.Running}}' "$seed" 2>/dev/null)" != "true" ]]; then
    docker logs --tail 80 "$seed" 2>&1 || true
    echo "The seeding container stopped before the history was played" >&2
    exit 1
  fi
  if (( SECONDS > deadline )); then
    echo "The history was not played within 15 minutes" >&2
    exit 1
  fi
  sleep 5
done

# Every delivery to a listener is stored until it completes (completion-mode: delete), so an empty
# registry means the emails, the audit and the indicators have heard everything. A delivery left
# there would be handed back to the backend of prod, which lacks the listeners of the demo profile.
deadline=$((SECONDS + 180))
until [[ "$(psql_owner -c 'SELECT count(*) FROM public.event_publication')" == "0" ]]; do
  if (( SECONDS > deadline )); then
    echo "Events were still undelivered after 3 minutes" >&2
    exit 1
  fi
  sleep 5
done

docker logs "$seed" > "$backups/seed-$stamp.log" 2>&1
rotate 'seed-*.log'
echo "==> Stopping the seeding container (its log: $backups/seed-$stamp.log)"
docker stop -t 40 "$seed" >/dev/null

trap - EXIT
restart_backend

# The seeders log what they could not write and carry on, so the application starts anyway; here
# that is a failed reset.
problems="$(grep -E ' ERROR |Could not' "$backups/seed-$stamp.log" || true)"
if [[ -n "$problems" ]]; then
  printf '%s\n' "$problems" | tail -n 20
  echo "The seeding logged problems: read $backups/seed-$stamp.log" >&2
  exit 1
fi

echo "==> Done: the demonstration data of $(date -u +%F) is in place"
