#!/usr/bin/env bash
# Moves the stack of the backend machine to a version. Run it from a checkout of that same version, on the
# virtual machine:
#
#     git fetch --tags origin && git checkout --detach refs/tags/v1.2.0 && ./deploy/backend/update.sh v1.2.0
#
# (the deploy workflow does exactly that over SSH). It
#
#   1. builds the web application into web/dist, with a throwaway Node container,
#   2. writes the version into deploy/backend/.env (AYNI_IMAGE_TAG),
#   3. pulls the images and restarts what changed, and waits until it is healthy.
#
# It never touches the volumes. Take a database backup before you run it (deploy/README.md,
# "Backup and restore"): migrations only go forward, and going back to an older image after a newer
# migration ran may need that backup.
#
# The video machine (deploy/jitsi) is not touched: its images are pinned in its own compose file
# and change only when that file does.

set -euo pipefail

cd "$(dirname "$0")"
export COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"

tag="${1:-}"
if [[ ! "$tag" =~ ^v[0-9]+\.[0-9]+\.[0-9]+([-.][0-9A-Za-z.-]+)?$ ]]; then
  echo "usage: deploy/backend/update.sh <tag>, a release tag such as v1.2.0" >&2
  exit 2
fi

if [[ ! -f .env ]]; then
  echo "deploy/backend/.env does not exist: copy .env.example to .env and fill it in first" >&2
  exit 1
fi

previous="$(sed -n 's/^AYNI_IMAGE_TAG=//p' .env | tail -n 1)"

echo "==> Building the web application (web/dist)"
docker run --rm -v "$PWD/../../web:/app" -w /app node:22-alpine sh -c "npm ci && npm run build"

echo "==> Setting AYNI_IMAGE_TAG=$tag (was ${previous:-unset})"
if grep -q '^AYNI_IMAGE_TAG=' .env; then
  sed -i.bak "s|^AYNI_IMAGE_TAG=.*|AYNI_IMAGE_TAG=$tag|" .env
  rm -f .env.bak
else
  echo "AYNI_IMAGE_TAG=$tag" >> .env
fi

echo "==> Pulling the images"
docker compose pull

echo "==> Starting"
if ! docker compose up -d --remove-orphans --wait --wait-timeout 300; then
  echo "The stack did not become healthy. See: docker compose logs --tail 200 backend" >&2
  echo "To go back to ${previous:-the previous version}, see \"Rolling back\" in deploy/README.md." >&2
  exit 1
fi

docker compose ps
echo "==> Done: $tag is running"
