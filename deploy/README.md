# Deploying Ayni

A runbook for putting Ayni on one Linux virtual machine, from an empty machine to a healthy backend
with HTTPS and video. The decisions behind it are short:

- **One virtual machine** runs everything with `docker compose`: the backend, a Caddy proxy and the
  four containers of Jitsi.
- **PostgreSQL is a managed server**, not a container. The stack never holds the data of the
  universities; the evidence files and the academic directory are the only state on the machine.
- **Caddy** ends TLS, and gets and renews its certificates by itself.
- **Jitsi is self hosted** (US08). A browser only gives a page the camera and the microphone over
  HTTPS, and the media travels over UDP port 10000 straight to the videobridge, so both must be
  reachable from the Internet.

```
Internet ── 80, 443/tcp ──▶ proxy (Caddy) ──▶ backend :8080 ──▶ managed PostgreSQL (sslmode=require)
   │                           │  api.<domain>   backend, WebSocket included
   │                           │  app.<domain>   web/dist, and /api to the backend (one origin)
   │                           └─ meet.<domain>  jitsi-web ─ prosody ─ jicofo
   └──── 10000/udp ───────────────────────────────────────▶ jitsi-jvb (the media of every call)
```

| File | What it is |
|---|---|
| `docker-compose.prod.yml` | The stack. Mailpit is optional, under the `mailpit` profile |
| `.env.example` | Every variable, commented. Copy it to `.env` |
| `Caddyfile` | The three hostnames (and the optional fourth, for Mailpit) |
| `update.sh` | Builds the web application, sets the version, pulls and restarts |
| `../.github/workflows/release.yml` | A `v*` tag publishes `ghcr.io/<owner>/ayni-backend:<tag>` |
| `../.github/workflows/deploy.yml` | Manual: runs `update.sh` on the machine over SSH |

Nothing here states what the machine or the database will cost, or what their limits are: those
change, so read them from your provider when you choose.

---

## Before you start

You need:

- A domain you control, and access to its DNS.
- A Linux virtual machine with a public IPv4 address that does not change, and SSH access to it. Any
  current Ubuntu LTS works; the commands below assume it. The backend alone fits in 1 GB with the
  settings of [Memory](#memory); the four Jitsi containers do not fit next to it in that much.
- A managed PostgreSQL server (version 16 is what the project is developed against), a server
  administrator login for it, and the ability to allow the machine's address through its firewall.
- A way to send email (see [Mail](#mail)).
- A release tag. The image the stack runs is published by pushing a tag (see
  [Publishing a version](#publishing-a-version)).

Use `example.org` below for your domain.

---

## 1. The machine and the DNS

1. Create the virtual machine and give it a static public IP. Note the address: it is
   `AYNI_PUBLIC_IP`.
2. Create these records at your DNS provider, all pointing at that address:

   | Name | Type | Value |
   |---|---|---|
   | `api.example.org` | A | the public IP |
   | `app.example.org` | A | the public IP |
   | `meet.example.org` | A | the public IP |
   | `mail.example.org` | A | the public IP, only for the [protected Mailpit](#option-b-mailpit-behind-a-password) |

3. Wait until they resolve, from your own computer: `dig +short app.example.org` must print the
   address. Caddy cannot get a certificate before this is true.

### Firewall

Open these in the cloud firewall of the machine (on Azure, the network security group), and nothing
else towards the Internet except SSH, which should be allowed only from where you work:

| Port | Protocol | Why |
|---|---|---|
| 80 | TCP | Certificate authority validation and the redirect to HTTPS |
| 443 | TCP | The three sites |
| **10000** | **UDP** | **The media of every video call. Closed, a call opens and nobody sees or hears anyone** |
| 4443 | TCP | Optional: media over TCP for people whose network blocks UDP. Off in this stack |

Docker publishes ports by writing its own firewall rules, which skip `ufw`: do not rely on `ufw`
to close a port that compose publishes. The stack publishes only 80, 443 and 10000/udp. The backend
(8080), Jitsi's internal ports and Mailpit are not published at all.

To turn on the TCP fallback, add `JVB_TCP_HARVESTER_DISABLED: "false"` to the environment of
`jitsi-jvb` and `"4443:4443"` to its `ports`, and open 4443/tcp. It is untested here.

## 2. Docker

Install Docker Engine and the Compose plugin from Docker's own repository, following
<https://docs.docker.com/engine/install/ubuntu/>, then let your user run it and check:

```bash
sudo usermod -aG docker "$USER"     # log out and back in afterwards
docker compose version
docker run --rm hello-world
```

On a machine of 1 GB, add a swap file as well. The backend container never swaps (see
[Memory](#memory)), but the system, Docker and the proxy get room when memory runs short, instead
of the kernel killing a process:

```bash
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
free -h                             # Swap: 2.0Gi
```

### Memory

`docker-compose.prod.yml` limits the backend to `AYNI_BACKEND_MEMORY_LIMIT` (650 MB) and gives the
JVM a fixed heap instead of a share of the container. Past the limit the container is killed and
started again (`restart: unless-stopped`); it does not swap.

Measured on 2026-10-25 with the production image against an empty PostgreSQL 16, two rounds of
4000 authenticated requests, 20 at a time:

| JVM options | Container limit | Memory after start | Peak | Result |
|---|---|---|---|---|
| `-XX:MaxRAMPercentage=75` (the image's own) | 700 MB | 635 MiB | | Killed for lack of memory after about 880 requests |
| `-XX:+UseSerialGC -Xmx300m`, bounded metaspace and code cache | 700 MB | 575 MiB | 651 MiB | 8000 of 8000 answered, 95th percentile 47 to 88 ms |
| The default of the stack (below) | 600 MB | 491 MiB | 533 MiB | 8000 of 8000 answered, 95th percentile 46 to 53 ms, the same peak in both rounds |

The default of the stack:

```
-XX:+UseSerialGC -Xms128m -Xmx256m -XX:MaxMetaspaceSize=192m -XX:ReservedCodeCacheSize=48m
-XX:MaxDirectMemorySize=32m -Xss512k -XX:TieredStopAtLevel=1
```

with at most 50 request threads (`AYNI_TOMCAT_THREADS_MAX`). The limit of 650 MB leaves about
100 MB above the peak measured. The measurement ran on an arm64 laptop and did not exercise the
whiteboard, evidence uploads or a database with data in it: measure again on the machine with
`docker stats` and `grep anon /sys/fs/cgroup/memory.stat` inside the container. On a larger machine
raise `-Xmx` and the limit together, keeping the same distance between them.

Every container keeps at most three log files of 10 MB (`x-logging` in the compose file), so a
machine that stays on does not fill its disk with logs.

## 3. The database

The application connects as one role and migrates as another (TS01, ADR 0007). That is what makes
row level security bind the application, so build it exactly:

| Role | Purpose | Must be |
|---|---|---|
| `ayni` | The owner. Flyway migrates as it, and it owns every schema and table | Not a superuser |
| `ayni_app` | What the application connects as | **No `BYPASSRLS`, not a superuser, owns nothing** |

1. On the managed server, allow the machine's public IP through the server's firewall. Allow nothing
   else.
2. Connect as the server's administrator. Any PostgreSQL client works; this one needs only Docker
   (use the same major version as the server):

   ```bash
   docker run --rm -it postgres:16-alpine \
     psql "host=<server-name> port=5432 dbname=postgres user=<admin-login> sslmode=require"
   ```

3. Create the roles and the database. Use two long, different passwords; they are
   `AYNI_DB_PASSWORD` and `AYNI_DB_APP_PASSWORD` below.

   ```sql
   CREATE ROLE ayni LOGIN PASSWORD '<owner password>'
       NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE;
   CREATE ROLE ayni_app LOGIN PASSWORD '<application password>'
       NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE;

   -- Some managed servers only let the administrator hand a database to a role it belongs to.
   -- Run this one only if the next statement is refused.
   -- GRANT ayni TO <admin-login>;

   CREATE DATABASE ayni OWNER ayni;
   REVOKE ALL ON DATABASE ayni FROM PUBLIC;
   GRANT CONNECT ON DATABASE ayni TO ayni_app;
   ```

   Creating `ayni_app` yourself is what keeps the owner free of `CREATEROLE`. If you skip it, the
   first migration creates the role with `AYNI_DB_APP_PASSWORD`, which then needs `CREATEROLE` on
   `ayni` for that first start. A role that already exists keeps its own password, so
   `AYNI_DB_APP_PASSWORD` must be that one.

4. The connection string always asks for TLS:
   `jdbc:postgresql://<server-name>:5432/ayni?sslmode=require`. Without it the passwords and the
   data of the universities cross the network in the clear.

You do not create any table or schema: the first start does. After it, check the roles (see
[Verifying](#6-verifying)).

## 4. The code and the configuration

On the machine:

```bash
sudo mkdir -p /opt/ayni && sudo chown "$USER" /opt/ayni
git clone https://github.com/ayni-team/ayni.git /opt/ayni
cd /opt/ayni/deploy
cp .env.example .env && chmod 600 .env
```

If the repository is private, give the machine read access first, with a read-only deploy key of the
repository (Settings, Deploy keys) or a fine-grained token.

Edit `.env`. Every variable is explained there, with whether it is a secret. The ones to set now:

- `AYNI_DOMAIN`, `AYNI_PUBLIC_IP`, `ACME_EMAIL`.
- `AYNI_DB_URL`, the two users and the two passwords from step 3.
- `AYNI_JITSI_JWT_SECRET`, `JICOFO_AUTH_PASSWORD`, `JICOFO_COMPONENT_SECRET`, `JVB_AUTH_PASSWORD`.
- `AYNI_BOOTSTRAP_ADMIN_EMAIL`: a mailbox you can read.
- The mail variables (see [Mail](#mail)).

Generate each secret on its own, never reuse one:

```bash
openssl rand -base64 48
```

`.env` is ignored by git and holds every secret of the installation: keep it out of chats, tickets
and backups that other people can read. The backend refuses to start, naming what is missing, if a
required variable is blank, and compose refuses before that.

Check the file without starting anything:

```bash
docker compose config > /dev/null && echo valid
```

Run every `docker compose` command from `/opt/ayni/deploy`. `.env` sets `COMPOSE_FILE` there so that
the production file is the one compose uses; from the root of the repository the same command would
pick the development `docker-compose.yml` instead.

## 5. First start

### Publishing a version

The machine runs an image built by GitHub, never one built on the machine. A maintainer cuts a
release from `main`:

```bash
git tag v1.0.0 && git push origin v1.0.0
```

The **Release** workflow builds `backend/Dockerfile.prod` and publishes
`ghcr.io/<owner>/ayni-backend:v1.0.0`. Wait for it to finish (Actions tab). The first publication
makes a private package, so the machine needs to be able to pull it, in one of two ways:

- Make the package public (the package's settings on GitHub), or
- Sign the machine in once, with a personal access token that can only read packages:
  `echo <token> | docker login ghcr.io -u <github-user> --password-stdin`.

### Starting

From the checkout of that tag, on the machine:

```bash
cd /opt/ayni
git fetch --tags && git checkout --detach refs/tags/v1.0.0
./deploy/update.sh v1.0.0
```

`update.sh` builds the web application into `web/dist` (with a throwaway Node container), writes the
tag into `.env`, pulls the images and starts the stack, waiting until the backend is healthy. The
first start runs every migration and can take a couple of minutes. Caddy gets its three certificates
the first time each name is asked for.

While you are still testing the setup, you can ask Let's Encrypt for its staging certificates, which
have no usage limit and are not trusted by browsers, by adding this line to the global block at the
top of `Caddyfile` and running `docker compose restart proxy`. Remove it, and restart again, before
real people use the site:

```
acme_ca https://acme-staging-v02.api.letsencrypt.org/directory
```

## 6. Verifying

From your own computer:

```bash
curl -sS https://api.example.org/actuator/health/readiness     # {"status":"UP"}
curl -sS https://app.example.org/actuator/health               # {"status":"UP"}
curl -sSI https://app.example.org/ | head -n 1                 # HTTP/2 200
curl -sSI https://meet.example.org/ | head -n 1                # HTTP/2 200
```

On the machine:

```bash
cd /opt/ayni/deploy
docker compose ps                         # backend (healthy), proxy, the four jitsi containers
docker compose logs --tail 100 backend
```

Then check that the database is what the isolation of the universities assumes. As the
administrator, against the `ayni` database:

```sql
-- ayni_app: all three false, and ayni: rolsuper and rolbypassrls false as well
SELECT rolname, rolsuper, rolbypassrls, rolcreaterole
  FROM pg_roles WHERE rolname IN ('ayni', 'ayni_app');

-- must print a single row, ayni: the application owns nothing
SELECT DISTINCT tableowner FROM pg_tables
 WHERE schemaname NOT IN ('pg_catalog', 'information_schema');
```

If something fails:

| What you see | Look at |
|---|---|
| `docker compose` complains about a variable | The named variable is missing or blank in `.env` |
| The backend exits at once, `Missing required environment variables: ...` | The variables it names; the list is in `.env.example` |
| The backend stays unhealthy, the log says the connection was refused or timed out | The database server's firewall does not allow the machine's IP |
| The log says authentication failed | A wrong password, or `AYNI_DB_APP_PASSWORD` differs from the password of a role that already existed |
| The log says SSL is required | `sslmode=require` is missing from `AYNI_DB_URL` |
| `502` from Caddy | The backend is not healthy yet: `docker compose logs backend` |
| The browser warns about the certificate, or `curl` fails to connect | DNS does not point here yet, 80 or 443 is closed, or the staging line is still in the `Caddyfile`; `docker compose logs proxy` says which |
| Swagger UI answers 404 | It is off in production unless `AYNI_API_DOCS_ENABLED=true` |

## 7. The first administrator and the first university

An empty installation has no platform administrator, and nothing but one can register a university.
`AYNI_BOOTSTRAP_ADMIN_EMAIL` made the first start create that administrator.

1. Ask for a sign in link with that address:

   ```bash
   curl -sS -X POST https://api.example.org/api/v1/access/request \
     -H 'Content-Type: application/json' \
     -d '{"email": "admin@example.org"}'          # 202
   ```

2. Open the email. The link looks like `https://app.example.org/access/confirm?token=...`. Open it,
   or send its `token` yourself, and keep the `sessionToken` of the answer:

   ```bash
   curl -sS -X POST https://api.example.org/api/v1/access/confirm \
     -H 'Content-Type: application/json' -d '{"token": "<token from the link>"}'
   ```

3. Register the first university:

   ```bash
   curl -sS -X POST https://api.example.org/api/v1/admin/universities \
     -H "Authorization: Bearer <sessionToken>" -H 'Content-Type: application/json' \
     -d '{
           "code": "UPC",
           "name": "Universidad Peruana de Ciencias Aplicadas",
           "emailDomains": ["upc.edu.pe"],
           "minimumTeachingGrade": 14.00,
           "timezone": "America/Lima",
           "initialCredits": 5,
           "initialCreditValidityDays": 90
         }'                                          # 201
   ```

4. Give it its academic directory (ADR 0006): one `<CODE>.json` per university, in the format of
   `backend/academic-directory/README.md`. The backend reads it each time a student is looked up, so
   no restart is needed:

   ```bash
   cd /opt/ayni/deploy
   chmod 644 UPC.json
   docker compose cp UPC.json backend:/var/lib/ayni/academic-directory/UPC.json
   ```

   The folder is a volume, so the file survives updates. Do not copy the demonstration students of
   the repository into a real installation.

5. Invite the university's coordinator with `POST /api/v1/admin/universities/{code}/coordinators`
   (TS10); their students then sign in with the link of their own institutional address.

## 8. Backup and restore

What holds state, and how to copy it:

| State | Where | How |
|---|---|---|
| Everything of the universities | The managed PostgreSQL | `pg_dump`, below (and whatever automatic backup your provider offers: check its retention yourself) |
| Evidence files (US16) | Volume `ayni-prod-evidence` | `tar`, below |
| Academic directory | Volume `ayni-prod-academic-directory` | `tar`, below |
| Certificates | Volume `ayni-prod-caddy-data` | Not needed: they are issued again, within the authority's limits |
| Secrets | `deploy/.env` | Your password manager, never the same place as the dumps |

**Back up** before every update, and on a schedule. On the machine, with the owner's password:

```bash
mkdir -p ~/ayni-backups && cd ~/ayni-backups
stamp=$(date -u +%Y%m%dT%H%M%SZ)

docker run --rm -e PGPASSWORD='<owner password>' -v "$PWD:/backups" postgres:16-alpine \
  pg_dump "host=<server-name> port=5432 dbname=ayni user=ayni sslmode=require" \
  --format=custom --file="/backups/ayni-$stamp.dump"

for v in evidence academic-directory; do
  docker run --rm -v "ayni-prod-$v:/data:ro" -v "$PWD:/backups" alpine \
    tar czf "/backups/$v-$stamp.tgz" -C /data .
done
```

Copy the files off the machine. A backup that lives only on the machine it protects is not one.

**Restore** into an empty database. This replaces everything that is there now:

```bash
cd /opt/ayni/deploy
docker compose stop backend
```

As the server administrator, recreate the database:

```sql
DROP DATABASE ayni;
CREATE DATABASE ayni OWNER ayni;
REVOKE ALL ON DATABASE ayni FROM PUBLIC;
GRANT CONNECT ON DATABASE ayni TO ayni_app;
```

Then load the dump as the owner. Keep the privileges in it (do not pass `--no-acl`): they are what
gives `ayni_app` its access. The roles themselves are not in a dump, so they must exist already:

```bash
docker run --rm -e PGPASSWORD='<owner password>' -v "$HOME/ayni-backups:/backups" postgres:16-alpine \
  pg_restore --dbname="host=<server-name> port=5432 dbname=ayni user=ayni sslmode=require" \
  --no-owner --exit-on-error "/backups/ayni-<stamp>.dump"

docker run --rm -v ayni-prod-evidence:/data -v "$HOME/ayni-backups:/backups" alpine \
  tar xzf /backups/evidence-<stamp>.tgz -C /data

docker compose start backend
```

Do the same with `academic-directory` if it was lost. Afterwards, run the checks of
[Verifying](#6-verifying).

Practise a restore into a spare database before you need one.

## 9. Updating

1. Take a [backup](#8-backup-and-restore).
2. A maintainer pushes the new tag; wait for the **Release** workflow to publish its image.
3. Deploy it, either way:
   - **From GitHub:** Actions, **Deploy**, Run workflow, with the tag. This needs the `production`
     environment configured once (below).
   - **By hand, on the machine:**

     ```bash
     cd /opt/ayni
     git fetch --tags --force origin && git checkout --detach refs/tags/v1.1.0
     ./deploy/update.sh v1.1.0
     ```
4. Check [Verifying](#6-verifying).

Flyway applies the new migrations when the new backend starts. The backend replaces itself in a few
seconds, and the other containers stay up.

### Configuring the deploy workflow, once

In the repository: Settings, Environments, New environment, named exactly `production` (add required
reviewers if more than one person can run workflows). In it:

| Name | Kind | Value |
|---|---|---|
| `DEPLOY_HOST` | secret | The machine's address |
| `DEPLOY_USER` | secret | The user that owns `/opt/ayni` and may run docker |
| `DEPLOY_SSH_KEY` | secret | A private key whose public half is in that user's `~/.ssh/authorized_keys`. Make a key for this and nothing else: `ssh-keygen -t ed25519 -f deploy_key -C ayni-deploy` |
| `DEPLOY_KNOWN_HOSTS` | secret, optional | The output of `ssh-keyscan -t ed25519 <host>`. Without it the workflow trusts the key the machine shows at that moment |
| `DEPLOY_PATH` | variable, optional | The checkout on the machine, `/opt/ayni` by default |

None of these is written in the repository.

## 10. Rolling back

The images of old tags stay in the registry, so the code goes back by deploying an older tag:

```bash
cd /opt/ayni
git checkout --detach refs/tags/v1.0.0
./deploy/update.sh v1.0.0
```

**Look at the migrations first.** They only go forward. Between the two tags, list what the newer
version added:

```bash
git diff --stat v1.0.0 v1.1.0 -- backend/src/main/resources/db/migration
```

- **Nothing printed:** the database is the same. Roll back freely.
- **A migration was added:** the older code now runs on a newer schema. It may work (a new nullable
  column, a new table) or fail (a new required column it never fills). Try it; if it fails or
  misbehaves, restore the backup you took before the update, which loses what was written since,
  or fix forward with a new version.

## What not to do

- **Never `docker compose down -v`**, `docker volume rm`, `docker volume prune` or
  `docker system prune --volumes`. They erase the evidence files and the academic directory, and the
  certificates. `docker compose down` without `-v` is safe: it only stops the stack. (The
  development `docker compose down -v` in the main README erases a database that holds nothing
  real; here the files are the only copy.)
- **Never run the `dev` profile** (`SPRING_PROFILES_ACTIVE=dev`). It seeds demonstration people.
- **Never connect the application as the owner role**, nor give `ayni_app` `BYPASSRLS`, superuser
  rights or ownership of a table: the isolation between universities stops applying.
- **Never edit a migration** that has been released. Write a new one.
- **Never commit `deploy/.env`**, paste it anywhere, or send it in a message.
- **Never open the database's firewall to the whole Internet**, nor drop `sslmode=require`.
- **Never expose port 8080, the Jitsi ports or the database from the machine.** Only 80, 443 and
  10000/udp are published.
- **Do not change `AYNI_JITSI_JWT_SECRET` casually.** The backend and Jitsi must hold the same
  value; changing it in `.env` and running `docker compose up -d` recreates both, and the video
  passes already issued stop working.
- **Do not change `AYNI_DB_APP_PASSWORD`** in `.env` without changing the role's password in the
  database to the same value, in that order: the other way round the application cannot connect.
- **Do not point `AYNI_SKILLS_EVIDENCE_DIR` or `AYNI_ACADEMIC_DIRECTORY` elsewhere.** The volumes
  are mounted at the default paths with the image's ownership.

## Mail

The backend sends one kind of email that matters: the link that signs people in. Without delivery
nobody can enter, including the first administrator. Choose one of two ways, or the third, which
adds a real relay for a few addresses to the second.

### Option A: SMTP with a domain you verified

The way to run it for real people.

1. Take an account with a transactional email provider (or your organisation's relay), and verify the
   domain you send from with them; they give you the DNS records (SPF, DKIM) that make the mail
   arrive as legitimate.
2. Set in `.env`:

   ```
   AYNI_MAIL_HOST=<the provider's SMTP host>
   AYNI_MAIL_PORT=587
   AYNI_MAIL_USER=<smtp user>
   AYNI_MAIL_PASSWORD=<smtp password>
   AYNI_MAIL_FROM=Ayni <no-reply@example.org>      # on the verified domain
   AYNI_MAIL_STARTTLS=true                         # port 587; use AYNI_MAIL_SSL=true for 465
   ```

3. Apply it (`docker compose up -d`) and request an access link for a real address.

Compromises: it needs a domain and an account, a provider's sending limits and prices apply (read
them), mail can be filtered as spam until the domain has a reputation, and an unreachable server
only delays the emails (each failure is recorded on its notice; the readiness check does not depend
on it). The two TLS switches pass Spring's JavaMail properties; they were not run here against a
real server.

### Option B: Mailpit behind a password

For a demonstration, with no domain to verify and no provider. Mailpit catches every email the
backend sends and shows it on a page; nothing is delivered to anyone. Whoever presents reads the
link on that page and opens it.

1. Create the password and its hash (it asks twice, and prints the hash only):

   ```bash
   docker run --rm -it caddy:2.8.4-alpine caddy hash-password
   ```

2. Set in `.env` (single quotes around the hash, which holds dollar signs):

   ```
   COMPOSE_PROFILES=mailpit
   AYNI_MAIL_HOST=mailpit
   AYNI_MAIL_PORT=1025
   AYNI_MAIL_USER=
   AYNI_MAIL_PASSWORD=
   MAILPIT_SITE=mail.example.org
   MAILPIT_BASIC_AUTH_USER=ayni
   MAILPIT_PASSWORD_HASH='$2a$14$...'
   ```

3. `docker compose up -d`. The page is at `https://mail.example.org`, and the proxy asks for the user
   and the password before showing anything.

Compromises, which are real:

- **Whoever has that password can read every sign in link, and so can enter as anyone**, the
  platform administrator included. Treat it as the administrator's own password, not as a viewer's.
- Real people receive nothing, so only people at your demonstration can use the installation.
  Do not register real students' addresses in it.
- Mailpit keeps its emails inside its container, which this stack does not back up: recreating the
  container empties it.
- With the profile off, the page is not exposed at all: `MAILPIT_SITE` falls back to an address that
  nothing outside the container can reach, and no certificate is requested for it.

To go from B to A, remove `COMPOSE_PROFILES`, `MAILPIT_*`, set the SMTP variables of A and run
`docker compose up -d --remove-orphans`.

### Option C: Mailpit for everybody, a real relay for a few addresses

For a demonstration where the people are invented but one or two are real, such as the lead
signing in with their own mailbox. It is option B plus a second server that only a short list of
addresses reaches. The invented students carry the domain of a real university, so no real relay
may ever see them: an address that is not on the list leaves through the Mailpit of option B, whatever
its domain.

1. Set up option B first.
2. Take a relay that delivers to the mailbox (a provider's SMTP, or the relay of the organisation)
   and set in `.env`:

   ```
   AYNI_REAL_RELAY_ALLOWED_RECIPIENTS=lead@example.org,second@example.org
   AYNI_REAL_RELAY_HOST=<the relay's SMTP host>
   AYNI_REAL_RELAY_PORT=587
   AYNI_REAL_RELAY_USERNAME=<smtp user>
   AYNI_REAL_RELAY_PASSWORD=<smtp password>
   AYNI_REAL_RELAY_STARTTLS=true                   # port 587; use AYNI_REAL_RELAY_SSL=true for 465
   ```

3. `docker compose up -d` and ask for an access link for an address of the list: it arrives in that
   mailbox. Ask for one for any other address: it appears in Mailpit and nowhere else.

What to know:

- The list is compared address by address, without regard to case, and never by domain. Putting
  `lead@example.org` on it sends nothing else of `example.org` out.
- The list and the host go together. Only one of them stops the backend from starting, with a
  message that says which one is missing and names no address.
- A relay that is down fails the email of an allowed address, and that failure is recorded on its
  notice like any other. The email is **not** sent to Mailpit instead: the person would wait for a
  link that never comes, so the failure must be seen.
- The log counts the emails that left through each server and never names an address, a link or a
  code. The sender is `AYNI_MAIL_FROM` for both servers, so it must be one the real relay accepts.
- Whoever reads Mailpit still reads the links of every address that is not on the list. Option B's
  warning stands.
- The TLS switches were not run here against a real relay; a test with two mail servers proves the
  routing.

To go back to B, empty the seven `AYNI_REAL_RELAY_*` variables.

---

## Not verified without a real machine

Everything in this folder was checked statically (`docker compose config` against `.env.example`,
the shell scripts and the workflow syntax). These need a real machine, a real domain and a real
browser, and should be run by hand the first time:

- Certificates actually issued for the three names, and the redirect from HTTP.
- WebSocket through the proxy: open the whiteboard of a session from `app.example.org`.
- Media over `10000/udp`: a call between two people on different networks, with audio and video
  both ways (the manual test of US08), including that `JVB_ADVERTISE_IPS` is the right address.
- Sending real email, and the STARTTLS and TLS switches against a provider.
- The connection to the managed database with `sslmode=require`, and the role checks of step 6.
- The `Release` and `Deploy` workflows on GitHub, with the `production` environment.
- `update.sh` on the machine, including the web build with Node in Docker.
- The memory of the backend on the machine itself, under real use (see [Memory](#memory)).
- The security headers of the `Caddyfile` as a browser receives them:
  `curl -sSI https://app.example.org/ | grep -i strict-transport`.
