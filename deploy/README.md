# Deploying Ayni

A runbook for putting the backend of Ayni and its video on two Linux virtual machines, from empty
machines to a healthy system with HTTPS and video. The decisions behind it are short:

- **Two virtual machines**, each running its own `docker compose` stack from this folder:
  `backend/` (the backend and a Caddy proxy) and `jitsi/` (the four containers of Jitsi and a Caddy
  proxy of their own). Both fit machines of 1 GB (see [Memory](#memory)).
- **The backend never talks to the video machine.** It signs a pass with a secret both machines
  hold, and the browser takes it to `meet.<domain>`. No port opens between the two machines.
- **PostgreSQL is a managed server**, not a container. The stacks never hold the data of the
  universities; the evidence files and the academic directory are the only state on the backend
  machine, and the video machine holds none.
- **The web application is static files** on Azure Static Web Apps, at `app.<domain>`. The
  **Deploy** workflow builds it with the address of the API (`VITE_API_BASE_URL`) and publishes it;
  no machine serves it. It calls `api.<domain>` across origins, which `AYNI_ALLOWED_ORIGINS` allows.
- **The academic system of the universities is another service** (ADR 0006): their documents
  (`UPC.json`, `USIL.json`) are static files on a GitHub Pages site, which the backend reads over
  HTTPS (`AYNI_ACADEMIC_DIRECTORY_URL`), keeping each copy a minute and the last one while the site
  does not answer.
- **Caddy** ends TLS on each machine, and gets and renews its certificates by itself.
- **Jitsi is self hosted** (US08). A browser only gives a page the camera and the microphone over
  HTTPS, and the media travels over UDP port 10000 straight to the videobridge, so both must be
  reachable from the Internet.

```
                       Azure Static Web Apps
Internet ── 443 ──────▶ app.<domain>  web/dist: the page, which then calls api. and meet.
   │
   │                   backend machine
   ├────── 80, 443 ──▶ proxy (Caddy) ──▶ backend :8080 ─┬─▶ managed PostgreSQL (sslmode=require)
   │                      api.<domain>   WebSocket too    └─▶ GitHub Pages: <CODE>.json, the
   │                                                          academic system (ADR 0006)
   │
   │                   video machine
   ├────── 80, 443 ──▶ proxy (Caddy) ──▶ meet.<domain>  jitsi-web ─ prosody ─ jicofo
   └──── 10000/udp ───────────────────▶ jitsi-jvb (the media of every call)
```

| File | What it is |
|---|---|
| `backend/docker-compose.yml` | The backend machine. Mailpit is optional, under the `mailpit` profile |
| `backend/.env.example` | Every variable of the backend machine, commented. Copy it to `backend/.env` |
| `backend/Caddyfile` | `api.` (and the optional `mail.`, for Mailpit) |
| `backend/update.sh` | Sets the version, pulls and restarts |
| `jitsi/docker-compose.yml` | The video machine |
| `jitsi/.env.example` | Every variable of the video machine. Copy it to `jitsi/.env` |
| `jitsi/Caddyfile` | `meet.` |
| `../.github/workflows/release.yml` | A release pull request merged into `main` (or a `v*` tag pushed by hand): tags it, publishes `ghcr.io/<owner>/ayni-backend:<tag>` and calls `deploy.yml` |
| `../.github/workflows/deploy.yml` | Called by each release, and by hand for any tag: runs `backend/update.sh` on the backend machine through Azure (`az vm run-command`, no SSH), then builds and publishes the web application |
| `../web/public/staticwebapp.config.json` | The routes and headers of the web application on Static Web Apps |

Nothing here states what the machines or the database will cost: prices change, so read them from
your provider. [Azure, inside the free services](#azure-inside-the-free-services) says which
choices keep a student subscription inside what Azure gives for free, and where the money goes when
it does not.

---

## Before you start

You need:

- A domain you control, and access to its DNS.
- Two Linux virtual machines, each with a public IPv4 address that does not change, and SSH access
  to them. Any current Ubuntu LTS works; the commands below assume it. Machines of 1 GB are enough
  with the settings of [Memory](#memory); the four Jitsi containers do not fit next to the backend
  in that much, which is why they get a machine of their own.
- A managed PostgreSQL server (version 16 is what the project is developed against), a server
  administrator login for it, and the ability to allow the backend machine's address through its
  firewall.
- A way to send email (see [Mail](#mail)).
- A release tag. The image the backend runs is published by each release (see
  [Publishing a version](#publishing-a-version)).

Use `example.org` below for your domain.

---

## Azure, inside the free services

What follows is for an Azure for Students subscription. The commands use the Azure CLI (`az login`
first, version 2.90); the portal has the same options. They were run against a real subscription on
2026-10-27, up to the static web app; check each result as you go.

### What is free and what is not

| Resource | Free? | The choice that keeps it free |
|---|---|---|
| The backend machine | Yes, 750 hours a month of each of the B1s, B2ats v2 and B2pts v2 sizes | `Standard_B2ats_v2` (AMD, 2 vCPU, 1 GB): the published image is amd64 |
| The video machine | Yes, the same allowance, a different size | `Standard_B2pts_v2` (Arm, 2 vCPU, 1 GB): the Jitsi images exist for arm64 |
| The backend's disk | Yes, two P6 disks | **Premium SSD of exactly 64 GB**. Another size or type is billed |
| The video machine's disk | No, about USD 1.5 a month | Standard HDD of 32 GB: Jitsi barely touches its disk. A P6 is free only if no other machine of the subscription holds one |
| The academic system | Yes | GitHub Pages of a **public** repository, which is all a free organisation can publish: invented students only |
| The web application | Yes, always | Static Web Apps on the **Free** plan: two custom domains and their certificates are included |
| PostgreSQL | Yes, for the first 12 months | Flexible server, `Standard_B1ms`, 32 GB of storage, backups kept 7 days, no geo-redundant backup, no zonal resiliency |
| The names under `cloudapp.azure.com` | Yes | A label on each public IP (see [The machines and the DNS](#1-the-machines-and-the-dns)) |
| **The public IPv4 addresses** | **No**, about USD 3.6 a month each | None. They are billed by the hour whether the machine runs or not. This is the cost of the deployment |
| Azure Backup, Microsoft Defender plans, extra storage accounts | No | Do not turn them on when a wizard offers them. Cloud Shell asks for a storage account: choose *No storage account required* |

Microsoft lists the free services on <https://azure.microsoft.com/pricing/free-services>, and the
two pages it has disagree on whether the machines are free for 12 months or always: read it again
when you create them.

**The allowance is the subscription's, not the deployment's.** 750 hours cover one machine of each
size running a whole month, and one PostgreSQL B1ms; two P6 disks and 32 GB of PostgreSQL storage
in all. Another project of the same subscription that holds a B2ats v2, a B1ms or a P6 spends the
same allowance: two machines of one size running all month are billed some 710 hours. Stopping
both when they are not used (`az vm deallocate`) keeps them inside it. A PostgreSQL server stopped
with `az postgres flexible-server stop` starts again by itself after seven days, and its storage is
billed while stopped.

**Find where money goes before it is spent.** In the portal: Cost Management, Budgets, create a
monthly budget of USD 5 on the subscription with an alert at 20 %. To find what was charged: Cost
Management, Cost analysis, filter by the resource group and group by *Meter*; every line names
what was billed.

### Creating the resources

**Find the regions first.** An Azure for Students subscription carries a policy that allows only
some regions, and not every region offers both sizes. These list the allowed regions and the
sizes each offers (the second takes about a minute a region):

```bash
az policy assignment list --query "[].parameters.listOfAllowedLocations.value" -o tsv
az vm list-skus --location <region> --resource-type virtualMachines \
  --query "[?name=='Standard_B2ats_v2' || name=='Standard_B2pts_v2'].name" -o tsv
```

On the subscription this was written with, the policy allowed `brazilsouth`, `centralus`,
`chilecentral`, `eastus` and `canadacentral`; `Standard_B2ats_v2` was offered only in
`chilecentral`, and `Standard_B2pts_v2` in `centralus` and `canadacentral`. So the backend and the
database live in `chilecentral` and the video machine in `centralus`, all in one resource group: a
group may hold resources of several regions. The commands below use those regions; change them to
what yours offers.

```bash
az group create --name ayni --location chilecentral
```

Make one SSH key for the machines **on your own computer**, not in Cloud Shell, and keep the private
half there:

```bash
ssh-keygen -t ed25519 -f ~/.ssh/ayni_vm -C ayni-vm
```

The backend machine. The disk is the P6 of the free services, deleted together with the machine:

```bash
az vm create --resource-group ayni --name ayni-backend --location chilecentral \
  --image Ubuntu2404 --size Standard_B2ats_v2 \
  --os-disk-size-gb 64 --storage-sku Premium_LRS --os-disk-delete-option Delete \
  --public-ip-sku Standard --public-ip-address-allocation static \
  --admin-username ayni --ssh-key-values ~/.ssh/ayni_vm.pub
```

The video machine is Arm, so it takes the Arm image of Ubuntu, and a Standard HDD of 32 GB. The
filter keeps the stable image: the offer also matches `ubuntu-24_04-lts-daily`. The first command
prints the name of the latest image; `latest` in the second picks the same:

```bash
az vm image list --publisher Canonical --offer ubuntu-24_04-lts --sku server-arm64 \
  --location centralus --all \
  --query "[?offer=='ubuntu-24_04-lts' && sku=='server-arm64'] | [-1].urn" -o tsv
az vm create --resource-group ayni --name ayni-jitsi --location centralus \
  --image Canonical:ubuntu-24_04-lts:server-arm64:latest --size Standard_B2pts_v2 \
  --os-disk-size-gb 32 --storage-sku Standard_LRS --os-disk-delete-option Delete \
  --public-ip-sku Standard --public-ip-address-allocation static \
  --admin-username ayni --ssh-key-values ~/.ssh/ayni_vm.pub
```

Each `az vm create` prints `publicIpAddress`: the backend's is the one the database lets in, the
video machine's goes in `AYNI_JITSI_PUBLIC_IP`, and each gets a DNS name (see
[The machines and the DNS](#1-the-machines-and-the-dns)). The firewall of each machine (its network
security group, `<name>NSG`) is in [Firewall](#firewall).

If the key was made somewhere else first (Cloud Shell), add the one of your computer to a machine
without SSH, from your computer:

```bash
az vm user update --resource-group ayni --name ayni-backend --username ayni \
  --ssh-key-value "$(cat ~/.ssh/ayni_vm.pub)"
```

It adds the key to `~/.ssh/authorized_keys` and keeps the ones already there; remove the other line
from that file once you can enter with this one.

The database, allowing in the backend machine's address only. The administrator password is
yours to generate and keep; `-hex` keeps `/`, `+` and `=` out of it. Pass it from a variable, never
written in a file of the repository, and keep the output off the screen: the command prints the
password in its result.

```bash
export AYNI_PG_ADMIN_PASSWORD="$(openssl rand -hex 32)"
az postgres flexible-server create --resource-group ayni --name <server-name> \
  --location chilecentral --version 16 --tier Burstable --sku-name Standard_B1ms \
  --storage-size 32 --storage-auto-grow Disabled --backup-retention 7 \
  --geo-redundant-backup Disabled --zonal-resiliency Disabled \
  --public-access <backend public IP> \
  --admin-user ayniadmin --admin-password "$AYNI_PG_ADMIN_PASSWORD" --output none
```

`--zonal-resiliency` is what older versions of the CLI called `--high-availability`. To change the
password later: `az postgres flexible-server update --resource-group ayni --name <server-name>
--admin-password "$AYNI_PG_ADMIN_PASSWORD" --output none`. Then follow
[The database](#3-the-database) to create the two roles, from the backend machine: it is the only
address the server lets in.

The web application, on the Free plan. Static Web Apps exists in fewer regions than the machines,
which this lists; `centralus` is the one of them the policy above allows. Its files are served from
everywhere anyway:

```bash
az provider show -n Microsoft.Web \
  --query "resourceTypes[?resourceType=='staticSites'].locations" -o tsv
```

```bash
az staticwebapp create --resource-group ayni --name ayni-web --location centralus --sku Free \
  --output none
az staticwebapp show --resource-group ayni --name ayni-web --query defaultHostname -o tsv
```

Give it no `--source`: that would have Azure write a workflow of its own into the repository. The
second command prints a name like `<words>.azurestaticapps.net`: the address of the web
application, or the value of the DNS record of `app.` (below). Its deployment token is not
copied anywhere: the **Deploy** workflow reads it from Azure each time
([Configuring the deploy workflow](#configuring-the-deploy-workflow-once)), and a publication by
hand reads it with `az` (see [Starting](#starting)).

With a domain of your own, once the record of `app.` resolves, give the web application its
address; Static Web Apps checks the record and issues the certificate by itself, which can take some
minutes:

```bash
az staticwebapp hostname set --resource-group ayni --name ayni-web --hostname app.example.org
```

### When a call needs more than 1 GB

The video machine was measured idle, not during a call (see [Memory](#memory-of-the-video-machine)).
If a call runs out of memory, give the machine a larger size for the day of the presentation and
take it back afterwards. A larger size is billed by the hour, and only while the machine runs.
`Standard_B2pls_v2` is the Arm size of 4 GB; the first command lists what the machine can take, so
check it is there:

```bash
az vm list-vm-resize-options --resource-group ayni --name ayni-jitsi -o table
az vm deallocate --resource-group ayni --name ayni-jitsi
az vm resize --resource-group ayni --name ayni-jitsi --size Standard_B2pls_v2
az vm start --resource-group ayni --name ayni-jitsi
```

and the same with `Standard_B2pts_v2` to go back. Raise `VIDEOBRIDGE_MAX_MEMORY` and
`JICOFO_MAX_MEMORY` in `jitsi/.env` while it is larger. A machine stopped from inside (`sudo
poweroff`) is still billed; only `az vm deallocate` (or Stop in the portal) stops the bill of a paid
size. The public address stays the same, because it is static.

---

## 1. The machines and the DNS

1. Create the two virtual machines and give each a static public IP (on Azure, above). Note both
   addresses.
2. Give each machine a name. Each stack takes the whole name of its site (`AYNI_API_HOST` in
   `backend/.env`, `AYNI_MEET_HOST` in `jitsi/.env`), so either way works:

   **With a domain you control**, create these records at your DNS provider:

   | Name | Type | Value |
   |---|---|---|
   | `api.example.org` | A | the backend machine's IP |
   | `app.example.org` | CNAME | the default name of the static web app, `<words>.azurestaticapps.net` |
   | `meet.example.org` | A | the video machine's IP |
   | `mail.example.org` | A | the backend machine's IP, only for the [protected Mailpit](#option-b-mailpit-behind-a-password) |

   **Without a domain**, give each public IP a label; Azure answers for
   `<label>.<region>.cloudapp.azure.com` at no cost, and the web application keeps its
   `<words>.azurestaticapps.net` name:

   ```bash
   az network public-ip update --resource-group ayni --name ayni-backendPublicIP --dns-name ayni-api
   az network public-ip update --resource-group ayni --name ayni-jitsiPublicIP --dns-name ayni-meet
   ```

   They make `ayni-api.chilecentral.cloudapp.azure.com` and
   `ayni-meet.centralus.cloudapp.azure.com`. A label is unique within its region: if one is taken,
   choose another. An IP carries one name only, so the protected Mailpit is served at `/mailpit/`
   of the backend's name instead (see [Option B](#option-b-mailpit-behind-a-password)).

3. Wait until they resolve, from your own computer: `dig +short <api name>` and
   `dig +short <meet name>` must print their addresses, and with a domain
   `dig +short app.example.org` the name of the static web app. Neither Caddy nor Static Web Apps can get a certificate before
   this is true.

### Firewall

Open these in the cloud firewall of each machine (on Azure, its network security group), and
nothing else towards the Internet except SSH, which should be allowed only from where you work:

| Machine | Port | Protocol | Why |
|---|---|---|---|
| both | 80 | TCP | Certificate authority validation and the redirect to HTTPS |
| both | 443 | TCP | The sites |
| video | **10000** | **UDP** | **The media of every video call. Closed, a call opens and nobody sees or hears anyone** |
| video | 4443 | TCP | Optional: media over TCP for people whose network blocks UDP. Off in this stack |

On Azure, `az vm create` opened SSH to the whole Internet. Narrow it to your address, then open the
rest (`ayni-backendNSG` for the backend machine, `ayni-jitsiNSG` for the video one):

```bash
az network nsg rule update --resource-group ayni --nsg-name ayni-backendNSG \
  --name default-allow-ssh --source-address-prefixes <your public IP>/32
az network nsg rule create --resource-group ayni --nsg-name ayni-backendNSG \
  --name web --priority 1010 --direction Inbound --access Allow --protocol Tcp \
  --destination-port-ranges 80 443
```

and for the video machine the same two commands with `ayni-jitsiNSG`, plus:

```bash
az network nsg rule create --resource-group ayni --nsg-name ayni-jitsiNSG \
  --name media --priority 1020 --direction Inbound --access Allow --protocol Udp \
  --destination-port-ranges 10000
```

Docker publishes ports by writing its own firewall rules, which skip `ufw`: do not rely on `ufw`
to close a port that compose publishes. The backend machine publishes only 80 and 443, the video
machine 80, 443 and 10000/udp. The backend (8080), Jitsi's internal ports and Mailpit are not
published at all.

To turn on the TCP fallback, add `JVB_TCP_HARVESTER_DISABLED: "false"` to the environment of
`jitsi-jvb` in `jitsi/docker-compose.yml` and `"4443:4443"` to its `ports`, and open 4443/tcp on the
video machine. It is untested here.

## 2. Docker

On both machines, install Docker Engine and the Compose plugin from Docker's own repository,
following <https://docs.docker.com/engine/install/ubuntu/> (its packages exist for amd64 and arm64
alike), then let your user run it and check:

```bash
sudo usermod -aG docker "$USER"     # log out and back in afterwards
docker compose version
docker run --rm hello-world
```

On a machine of 1 GB, add a swap file as well, on both. The backend container never swaps (see
[Memory](#memory)), but the system, Docker, the proxies and Jitsi get room when memory runs short,
instead of the kernel killing a process:

```bash
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
free -h                             # Swap: 2.0Gi
```

### Memory

`backend/docker-compose.yml` limits the backend to `AYNI_BACKEND_MEMORY_LIMIT` (650 MB) and gives
the JVM a fixed heap instead of a share of the container. Past the limit the container is killed and
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

Every container of both machines keeps at most three log files of 10 MB (`x-logging` in each
compose file), so a
machine that stays on does not fill its disk with logs.

### Memory of the video machine

The images of jicofo and of the videobridge give each Java process a heap of 3 GB unless told
otherwise. `jitsi/docker-compose.yml` caps them with `JICOFO_MAX_MEMORY` (256 MB) and
`VIDEOBRIDGE_MAX_MEMORY` (384 MB).

Measured on 2026-10-25 with those caps, the whole stack started and idle, with no call:

| Container | Memory |
|---|---|
| `proxy` | 14 MiB |
| `jitsi-web` | 9 MiB |
| `jitsi-prosody` | 17 MiB |
| `jitsi-jicofo` | 146 MiB |
| `jitsi-jvb` | 173 MiB |
| **Total** | **359 MiB** |

A call was not measured: the browser of the measurement had no camera or microphone. A call of two
people is peer to peer by default and barely touches the videobridge; from the third person on,
the media of everyone crosses it. Measure during the first real call (`docker stats`), and see
[When a call needs more than 1 GB](#when-a-call-needs-more-than-1-gb).

## 3. The database

The application connects as one role and migrates as another (TS01, ADR 0007). That is what makes
row level security bind the application, so build it exactly:

| Role | Purpose | Must be |
|---|---|---|
| `ayni` | The owner. Flyway migrates as it, and it owns every schema and table | Not a superuser |
| `ayni_app` | What the application connects as | **No `BYPASSRLS`, not a superuser, owns nothing** |

1. On the managed server, allow the backend machine's public IP through the server's firewall (on
   Azure, `--public-access` above did it). The video machine never connects to it. Allow nothing
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

   **On Azure, give the owner the `public` schema as well.** A database made there from its
   template keeps `public` under one of Azure's roles, and the first start then stops at once with
   `permission denied for schema public`: Flyway's history and the event publication registry live
   in it. Connected to the new database (`dbname=ayni` in the command of step 2):

   ```sql
   ALTER SCHEMA public OWNER TO ayni;
   SELECT pg_get_userbyid(nspowner) FROM pg_namespace WHERE nspname = 'public';   -- ayni
   ```

   On a server where `public` already belongs to the owner of the database (PostgreSQL 15 and
   later, from a template of its own), the statement changes nothing.

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

On each machine, the same checkout of the repository; each uses only its own folder of `deploy/`:

```bash
sudo mkdir -p /opt/ayni && sudo chown "$USER" /opt/ayni
git clone https://github.com/ayni-team/ayni.git /opt/ayni
```

If the repository is private, give the machines read access first, with a read-only deploy key of
the repository (Settings, Deploy keys) or a fine-grained token.

Generate each secret on its own, never reuse one, and write it straight into the `.env` that needs
it:

```bash
openssl rand -base64 48
```

A `.env` is ignored by git and holds the secrets of its machine: keep it out of chats, tickets and
backups that other people can read. The backend refuses to start, naming what is missing, if a
required variable is blank, and compose refuses before that.

### The backend machine

```bash
cd /opt/ayni/deploy/backend
cp .env.example .env && chmod 600 .env
```

Edit `.env`. Every variable is explained there, with whether it is a secret. The ones to set now:

- `AYNI_API_HOST` (the name of this machine), `ACME_EMAIL`.
- `AYNI_IDENTITY_ACCESS_LINK_BASE_URL` and `AYNI_ALLOWED_ORIGINS`, with the address of the static
  web app; `AYNI_JITSI_URL`, `https://` and the name of the video machine.
- `AYNI_DB_URL`, the two users and the two passwords from step 3.
- `AYNI_JITSI_JWT_SECRET`: generate it here, and copy the same value to the video machine.
- `AYNI_BOOTSTRAP_ADMIN_EMAIL`: a mailbox you can read.
- The mail variables (see [Mail](#mail)).

### The video machine

```bash
cd /opt/ayni/deploy/jitsi
cp .env.example .env && chmod 600 .env
```

Set:

- `AYNI_MEET_HOST` (the name of this machine) and `ACME_EMAIL`.
- `AYNI_JITSI_PUBLIC_IP`: the public address of this machine.
- `AYNI_JITSI_JWT_SECRET`: **the same value** as on the backend machine. Copy it from one `.env` to
  the other over SSH, not through a chat or a document. With two different values every video pass
  is refused.
- `JICOFO_AUTH_PASSWORD`, `JICOFO_COMPONENT_SECRET`, `JVB_AUTH_PASSWORD`: three new values, used
  only inside this machine.

### Checking both

In each folder, without starting anything:

```bash
docker compose config > /dev/null && echo valid
```

Run every `docker compose` command of a machine from its folder: `/opt/ayni/deploy/backend` or
`/opt/ayni/deploy/jitsi`. From the root of the repository the same command would pick the
development `docker-compose.yml` instead.

## 5. First start

### Publishing a version

The backend machine runs an image built by GitHub, never one built on the machine. A release is a
pull request from `develop` into `main`, titled `chore(release): v1.0.0` and merged with a merge
commit (`docs/pull-requests.md`, "Releases"). The first time, before the machines exist, a tag of
a commit of `main` pushed by hand does the same:

```bash
git tag -a v1.0.0 origin/main -m v1.0.0 && git push origin v1.0.0
```

The **Release** workflow tags the merge commit, builds `backend/Dockerfile.prod` and publishes
`ghcr.io/<owner>/ayni-backend:v1.0.0`; its last job, **Deploy**, fails until the machines and
[its configuration](#configuring-the-deploy-workflow-once) exist, which is expected on the first
release. Wait for the image (Actions tab). The first publication
makes a private package, so the backend machine needs to be able to pull it, in one of two ways:

- Make the package public (the package's settings on GitHub), or
- Sign the backend machine in once, with a personal access token that can only read packages:
  `echo <token> | docker login ghcr.io -u <github-user> --password-stdin`.

### Starting

Start the video machine first; it does not depend on the backend:

```bash
cd /opt/ayni/deploy/jitsi
docker compose pull && docker compose up -d
```

Then the backend machine, from the checkout of that tag:

```bash
cd /opt/ayni
git fetch --tags && git checkout --detach refs/tags/v1.0.0
./deploy/backend/update.sh v1.0.0
```

`update.sh` writes the tag into `backend/.env`, pulls the images and starts the stack, waiting until
the backend is healthy. The first start runs every migration and can take a couple of minutes.
Each Caddy gets its certificates the first time each name is asked for.

Last, the web application. Once
[the deploy workflow is configured](#configuring-the-deploy-workflow-once), **Deploy** does it: it
runs as the last job of each release, and by hand from Actions (Deploy, Run workflow) with any
tag. It repeats the step above through Azure,
which changes nothing the second time, and then builds the web with `VITE_API_BASE_URL` and
publishes it. Before it is configured, publish the web by hand, from your own computer; the token
goes from `az` to the tool without being printed. This is how it was published on 2026-10-27:

```bash
git switch --detach v1.0.0 && cd web
npm ci && VITE_API_BASE_URL=https://api.example.org npm run build
SWA_CLI_DEPLOYMENT_TOKEN="$(az staticwebapp secrets list --resource-group ayni --name ayni-web \
  --query properties.apiKey -o tsv)" \
  npx --yes @azure/static-web-apps-cli deploy ./dist --env production
cd .. && git switch develop
```

A web built without `VITE_API_BASE_URL` calls `app.` itself, where there is no API, and every
screen fails.

While you are still testing the setup, you can ask Let's Encrypt for its staging certificates, which
have no usage limit and are not trusted by browsers, by adding this line to the global block at the
top of the machine's `Caddyfile` and running `docker compose restart proxy`. Remove it, and restart
again, before real people use the site:

```
acme_ca https://acme-staging-v02.api.letsencrypt.org/directory
```

## 6. Verifying

From your own computer:

```bash
curl -sS https://api.example.org/actuator/health/readiness     # {"status":"UP"}
curl -sSI https://app.example.org/ | head -n 1                 # HTTP/2 200
curl -sSI https://app.example.org/access/confirm | head -n 1   # HTTP/2 200 too: a route is index.html
curl -sSI https://meet.example.org/ | head -n 1                # HTTP/2 200
```

And that the API lets the web application in from its origin, and names downloaded files to it:

```bash
curl -sS -o /dev/null -D - -H 'Origin: https://app.example.org' \
  'https://api.example.org/api/v1/access/university?email=someone@upc.edu.pe' | grep -i '^access-control'
# access-control-allow-origin: https://app.example.org
# access-control-expose-headers: Content-Disposition
```

On the backend machine:

```bash
cd /opt/ayni/deploy/backend
docker compose ps                         # backend (healthy) and proxy
docker compose logs --tail 100 backend
```

On the video machine:

```bash
cd /opt/ayni/deploy/jitsi
docker compose ps                         # proxy and the four jitsi containers
curl -sSI https://meet.example.org/ | grep -i strict-transport   # the headers of the Caddyfile
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

Once the first university exists (step 7), row level security can be seen at work: connected as
`ayni_app`, which sets no university, a table of the universities reads empty, while the
administrator reads its rows.

```sql
SELECT count(*) FROM identity.credit_policies;   -- ayni_app: 0; the administrator: 1 per university
```

If something fails:

| What you see | Look at |
|---|---|
| `docker compose` complains about a variable | The named variable is missing or blank in `.env` |
| The backend exits at once, `Missing required environment variables: ...` | The variables it names; the list is in `.env.example` |
| The backend stays unhealthy, the log says the connection was refused or timed out | The database server's firewall does not allow the backend machine's IP |
| The log says authentication failed | A wrong password, or `AYNI_DB_APP_PASSWORD` differs from the password of a role that already existed |
| The log says SSL is required | `sslmode=require` is missing from `AYNI_DB_URL` |
| `502` from Caddy | The backend is not healthy yet: `docker compose logs backend` |
| A call opens and says the pass is not valid | `AYNI_JITSI_JWT_SECRET` or `AYNI_JITSI_APP_ID` differ between `backend/.env` and `jitsi/.env` |
| A call opens and nobody sees or hears anyone | 10000/udp closed on the video machine, or `AYNI_JITSI_PUBLIC_IP` is not its public address |
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

2. Read the email (with Mailpit, on its page, see [Option B](#option-b-mailpit-behind-a-password)).
   The link looks like `https://app.example.org/access/confirm?token=...`, and works **once**:
   opened in a browser, it signs that browser in, and the web application has no screen of the
   administrator yet. For what follows, send its `token` yourself instead, without opening it, and
   keep the `sessionToken` of the answer in a variable of your shell:

   ```bash
   S=$(curl -sS -X POST https://api.example.org/api/v1/access/confirm \
     -H 'Content-Type: application/json' -d '{"token": "<token from the link>"}' \
     | python3 -c "import json,sys;print(json.load(sys.stdin)['sessionToken'])")
   ```

   A `KeyError` there means the answer was an error: run the `curl` alone to read it. *This access
   link has expired or was already used* asks for a new link.

3. Register the universities. When the academic system already has the university's document
   (step 4 below), register it from there, which takes its name, domains, timezone and minimum
   grade from the document; the credits are Ayni's, so you give them:

   ```bash
   for U in UPC USIL; do
     curl -sS -w ' -> %{http_code}\n' -X POST \
       https://api.example.org/api/v1/admin/universities/from-academic-system \
       -H "Authorization: Bearer $S" -H 'Content-Type: application/json' \
       -d "{\"code\": \"$U\", \"initialCredits\": 5, \"creditValidityDays\": 90}"
   done                                              # 201 each
   curl -sS 'https://api.example.org/api/v1/access/university?email=someone@upc.edu.pe'
   ```

   Or by hand, with every field given:

   ```bash
   curl -sS -X POST https://api.example.org/api/v1/admin/universities \
     -H "Authorization: Bearer $S" -H 'Content-Type: application/json' \
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
   `backend/academic-directory/README.md`. Either way below, the backend reads it when a student
   is looked up, so no restart is needed. Do not publish or copy the demonstration students of the
   repository into an installation for real people.

   **From another service (this deployment).** A public repository whose GitHub Pages site serves
   the documents as they are, one file each: `https://<owner>.github.io/<repository>/UPC.json`.
   Nothing runs there; the path is the file name. Create it once, from your own computer, with the
   `gh` CLI (this publishes the files to the Internet):

   ```bash
   gh repo create ayni-team/ayni-academic-system --public --clone && cd ayni-academic-system
   cp ../ayni/backend/academic-directory/UPC.json ../ayni/backend/academic-directory/USIL.json .
   touch .nojekyll                       # serve the files as they are
   git add . && git commit -m "Publish the academic documents of UPC and USIL" && git push
   gh api -X POST repos/ayni-team/ayni-academic-system/pages \
     -f 'source[branch]=main' -f 'source[path]=/'
   curl -sS https://ayni-team.github.io/ayni-academic-system/UPC.json | head -c 80   # {"university"...
   ```

   Then, on the backend machine, set in `backend/.env` and apply:

   ```
   AYNI_ACADEMIC_DIRECTORY_URL=https://ayni-team.github.io/ayni-academic-system/
   ```

   ```bash
   cd /opt/ayni/deploy/backend && docker compose up -d
   docker compose logs backend | grep 'academic system reads'   # ... from the address https://...
   ```

   An edit pushed to that repository can take about ten minutes to reach the backend: Pages
   publishes it in a minute or two, its servers answer with `cache-control: max-age=600`, and the
   backend keeps its own copy a minute more. While the site does not answer, the backend uses the last copy it read; with no
   copy, the lookup fails and the log names the university. `logoUrl` in a document is a path of
   the web application (`/logos/upc.png`), which Static Web Apps serves.

   A real person added for a demonstration, such as the lead signing in with a mailbox of their
   own, never goes in that repository: put them in `<CODE>.local.json` in the folder of the
   backend, which is read from there also when the documents come from the address:

   ```bash
   cd /opt/ayni/deploy/backend
   chmod 644 UPC.local.json
   docker compose cp UPC.local.json backend:/var/lib/ayni/academic-directory/UPC.local.json
   ```

   **From the folder.** With `AYNI_ACADEMIC_DIRECTORY_URL` empty, copy the documents into the
   volume instead:

   ```bash
   cd /opt/ayni/deploy/backend
   chmod 644 UPC.json
   docker compose cp UPC.json backend:/var/lib/ayni/academic-directory/UPC.json
   ```

   The folder is a volume, so its files survive updates.

5. Invite the university's coordinator with `POST /api/v1/admin/universities/{code}/coordinators`
   (TS10); their students then sign in with the link of their own institutional address.

## 8. Backup and restore

What holds state, and how to copy it:

| State | Where | How |
|---|---|---|
| Everything of the universities | The managed PostgreSQL | `pg_dump`, below (and whatever automatic backup your provider offers: check its retention yourself) |
| Evidence files (US16) | Volume `ayni-prod-evidence` | `tar`, below |
| Academic directory | The repository of its GitHub Pages site, and the volume `ayni-prod-academic-directory` for the local students (or every document, without the site) | `git`; `tar`, below |
| Certificates | Volume `ayni-prod-caddy-data` | Not needed: they are issued again, within the authority's limits |
| Secrets | `deploy/backend/.env` and `deploy/jitsi/.env` | Your password manager, never the same place as the dumps |

The video machine holds no state: its `.env` is all it needs to be built again.

**Back up** before every update, and on a schedule. On the backend machine, with the owner's
password:

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
cd /opt/ayni/deploy/backend
docker compose stop backend
```

As the server administrator, recreate the database:

```sql
DROP DATABASE ayni;
CREATE DATABASE ayni OWNER ayni;
REVOKE ALL ON DATABASE ayni FROM PUBLIC;
GRANT CONNECT ON DATABASE ayni TO ayni_app;
```

and, on Azure, connected to the new `ayni`, `ALTER SCHEMA public OWNER TO ayni;` as in
[The database](#3-the-database). Then load the dump as the owner. Keep the privileges in it (do
not pass `--no-acl`): they are what gives `ayni_app` its access. The roles themselves are not in
a dump, so they must exist already:

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

1. Take a [backup](#8-backup-and-restore), **before the merge**: the release deploys itself as
   soon as its image is published.
2. The lead merges the release pull request into `main` with a merge commit
   (`docs/pull-requests.md`, "Releases"). The **Release** workflow tags it and publishes its image.
3. Deploy it, either way:
   - **From GitHub:** the last job of **Release**, **Deploy**, does it (Actions shows it), or
     Deploy by hand from Actions, Run workflow, with the tag. This needs the `production`
     environment configured once (below). If it fails because the backend machine is deallocated,
     start it (`az vm start`) and run Deploy by hand.
   - **By hand, on the backend machine:**

     ```bash
     cd /opt/ayni
     git fetch --tags --force origin && git checkout --detach refs/tags/v1.1.0
     ./deploy/backend/update.sh v1.1.0
     ```
4. Check [Verifying](#6-verifying).

Flyway applies the new migrations when the new backend starts. The backend replaces itself in a few
seconds, and the proxy stays up. **Deploy** publishes the web application of the tag afterwards; by
hand, publish it as in [Starting](#starting).

The video machine changes only when `jitsi/docker-compose.yml` does (a new Jitsi version is a new
image tag in that file). When it does, on the video machine:

```bash
cd /opt/ayni && git fetch --tags --force origin && git checkout --detach refs/tags/v1.1.0
cd deploy/jitsi && docker compose pull && docker compose up -d
```

A call in progress is cut; do it when there is none.

### Configuring the deploy workflow, once

**Deploy** never opens SSH. It signs in to Azure with the OpenID Connect token GitHub gives each
run, as a *managed identity*: an identity of the subscription with no password, which accepts only
the runs of this repository's `production` environment and may act only on the resource group
`ayni`. Through it, Azure runs the update on the backend machine (`az vm run-command`, as root,
which changes to the user `ayni`) and hands over the deployment token of the static web app. It
needs no app registration in the directory, which a university's tenant often forbids. From your
own computer, with `az` signed in as an owner of the subscription and `gh` signed in:

```bash
az provider register --namespace Microsoft.ManagedIdentity --wait
az identity create --resource-group ayni --name ayni-deploy --location chilecentral --output none
az role assignment create --role Contributor \
  --assignee-object-id "$(az identity show -g ayni -n ayni-deploy --query principalId -o tsv)" \
  --assignee-principal-type ServicePrincipal \
  --scope "$(az group show --name ayni --query id -o tsv)" --output none
az identity federated-credential create --resource-group ayni --identity-name ayni-deploy \
  --name github-production --issuer https://token.actions.githubusercontent.com \
  --subject repo:ayni-team/ayni:environment:production \
  --audiences api://AzureADTokenExchange --output none
```

`Contributor` on the group lets it run commands on the machines and read the token of the static
web app, and touches nothing outside `ayni`. The subject ties it to the `production` environment of
`ayni-team/ayni`: no other repository, branch or environment can use it.

Then the environment and its variables. None is a secret: an identifier alone signs nobody in.

```bash
gh api -X PUT repos/ayni-team/ayni/environments/production --silent
gh variable set AZURE_CLIENT_ID --env production \
  --body "$(az identity show -g ayni -n ayni-deploy --query clientId -o tsv)"
gh variable set AZURE_TENANT_ID --env production --body "$(az account show --query tenantId -o tsv)"
gh variable set AZURE_SUBSCRIPTION_ID --env production --body "$(az account show --query id -o tsv)"
gh variable set AYNI_API_BASE_URL --env production --body https://api.example.org
```

| Name | Kind | Value |
|---|---|---|
| `AZURE_CLIENT_ID` | variable | The client id of the managed identity `ayni-deploy` |
| `AZURE_TENANT_ID` | variable | The tenant of the subscription |
| `AZURE_SUBSCRIPTION_ID` | variable | The subscription |
| `AYNI_API_BASE_URL` | variable | The origin of the API the web calls: `https://api.example.org`, no trailing slash |
| `AZURE_RESOURCE_GROUP` | variable, optional | `ayni` by default |
| `AYNI_BACKEND_VM` | variable, optional | `ayni-backend` by default |
| `AYNI_STATIC_WEB_APP` | variable, optional | `ayni-web` by default |
| `DEPLOY_USER` | variable, optional | The user that owns `/opt/ayni` and may run docker, `ayni` by default |
| `DEPLOY_PATH` | variable, optional | The checkout on the machine, `/opt/ayni` by default |

Check that Azure can run commands as that user on the machine before the first release, from your
own computer (it prints the containers of the stack):

```bash
az vm run-command invoke --resource-group ayni --name ayni-backend --command-id RunShellScript \
  --scripts "sudo -u ayni -H bash -c 'cd /opt/ayni/deploy/backend && docker compose ps'" \
  --query 'value[0].message' -o tsv
```

Azure reports success whatever the command returns, so the workflow looks for the last line of
its output, which only a finished update prints, and fails without it. Add required reviewers to
the environment (Settings, Environments, production) if a release should wait for someone before
it reaches the machine. A release merged while the backend machine is deallocated publishes its
image and then fails at the first step of Deploy; start the machine and run **Deploy** by hand
with the tag.

## 10. Rolling back

The images of old tags stay in the registry, so the code goes back by deploying an older tag:
**Deploy** with that tag moves the backend and the web application together. By hand, on the
backend machine:

```bash
cd /opt/ayni
git checkout --detach refs/tags/v1.0.0
./deploy/backend/update.sh v1.0.0
```

and publish the web application of that tag as in [Starting](#starting), or the newer web stays
in front of the older API.

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
- **Never publish a web application built without `VITE_API_BASE_URL`**, and keep
  `https://app.<domain>` in `AYNI_ALLOWED_ORIGINS`: the web on `app.` and the API on `api.` must
  name each other, or every call is refused.
- **Never run the `dev` profile** (`SPRING_PROFILES_ACTIVE=dev`). It seeds demonstration people.
- **Never connect the application as the owner role**, nor give `ayni_app` `BYPASSRLS`, superuser
  rights or ownership of a table: the isolation between universities stops applying.
- **Never edit a migration** that has been released. Write a new one.
- **Never commit `deploy/backend/.env` or `deploy/jitsi/.env`**, paste them anywhere, or send them
  in a message.
- **Never open the database's firewall to the whole Internet**, nor drop `sslmode=require`.
- **Never expose port 8080, the Jitsi ports or the database from a machine.** Only 80 and 443 are
  published on the backend machine, and 80, 443 and 10000/udp on the video machine.
- **Do not change `AYNI_JITSI_JWT_SECRET` casually.** The two machines must hold the same value.
  To change it, write the new value in both `.env` files and run `docker compose up -d` on both,
  one right after the other: in between, every pass is refused, and the passes already issued stop
  working.
- **Do not change `AYNI_DB_APP_PASSWORD`** in `.env` without changing the role's password in the
  database to the same value, in that order: the other way round the application cannot connect.
- **Do not point `AYNI_SKILLS_EVIDENCE_DIR` or `AYNI_ACADEMIC_DIRECTORY` elsewhere.** The volumes
  are mounted at the default paths with the image's ownership.

## Mail

The backend sends one kind of email that matters: the link that signs people in. Without delivery
nobody can enter, including the first administrator. Choose one of two ways, or the third, which
adds a real relay for a few addresses to the second. All of it happens on the backend machine: the
variables go in `deploy/backend/.env`, and `docker compose` runs from `deploy/backend`.

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

   Without a name of its own for the page (on the names Azure gives the addresses, which carry
   one name each), leave `MAILPIT_SITE` out and set instead:

   ```
   MAILPIT_ON_API_HOST=true
   ```

3. `docker compose up -d`. The page is at `https://mail.example.org/mailpit/` (the root redirects
   there), or at `https://<AYNI_API_HOST>/mailpit/` with `MAILPIT_ON_API_HOST`, and the proxy asks
   for the user and the password before showing anything. Its search box filters by recipient
   (`to:u202500025@upc.edu.pe`). Mailpit's own API is under the same path, behind the same password:

   ```bash
   curl -sS -u ayni 'https://api.example.org/mailpit/api/v1/message/latest' | head -c 200
   ```

Compromises, which are real:

- **Whoever has that password can read every sign in link, and so can enter as anyone**, the
  platform administrator included. Treat it as the administrator's own password, not as a viewer's.
- Real people receive nothing, so only people at your demonstration can use the installation.
  Do not register real students' addresses in it.
- Mailpit keeps its emails inside its container, which this stack does not back up: recreating the
  container empties it.
- With the profile off, the page is not exposed at all: `MAILPIT_SITE` falls back to an address that
  nothing outside the container can reach, and no certificate is requested for it.
  `MAILPIT_ON_API_HOST` off leaves `/mailpit/` to the backend, which answers 404.
- Without the page, read it through SSH instead, from your own computer: the first command finds
  the container's address on the machine, the second keeps a tunnel open, and the page is then at
  <http://localhost:58025/mailpit/>:

  ```bash
  IP=$(ssh ayni@api.example.org \
    "docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' ayni-mailpit-1")
  ssh -N -L 58025:$IP:8025 ayni@api.example.org
  ```

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

Everything in this folder was checked statically (`docker compose config` of both stacks against
their `.env.example`, `caddy validate` of both `Caddyfile`, the shell scripts and the workflow
syntax). The backend and the video stack were also started on a laptop and measured (see
[Memory](#memory) and [Memory of the video machine](#memory-of-the-video-machine)); there the proxy
of the video machine answered `meet.` with the headers of its `Caddyfile`. The web application,
built with `VITE_API_BASE_URL` and served from another origin than the API, signed the platform
administrator in through the emailed link and made calls with the session (preflight included);
another origin was refused with `403`. These need real
machines, a real domain and a real browser, and should be run by hand the first time:

- Certificates actually issued for the names of the two machines, and the redirect from HTTP.
- WebSocket through the proxy: open the whiteboard of a session from `app.example.org`, across
  origins to `api.example.org`.
- The academic system on GitHub Pages: the `gh` commands, and the backend reading
  `https://<owner>.github.io/...` over HTTPS from the machine (tested against a local HTTP server).
- The web application on Static Web Apps: the `az staticwebapp` commands, the custom domain and its
  certificate, the routes and headers of `staticwebapp.config.json` (that a route such as
  `/access/confirm` answers the page, that `assets/` keeps its long cache over the global
  `no-cache`), and the publishing job of the **Deploy** workflow.
- The **Deploy** workflow through Azure: the managed identity and its federated credential, the
  sign in of `azure/login` with them, `az vm run-command` running `update.sh` as `ayni`, the
  token of the static web app read at the time of use, and the call from **Release** after a
  release pull request is merged (the tag taken from its title or counted from the latest one).
  Checked with `actionlint`, the choice of the tag run against sample titles; not run on GitHub.
- Media over `10000/udp`: a call between two people on different networks, with audio and video
  both ways (the manual test of US08), including that `AYNI_JITSI_PUBLIC_IP` is the right address.
- Sending real email, and the STARTTLS and TLS switches against a provider.
- Mailpit at `/mailpit/` of the backend's name, and at the root of `MAILPIT_SITE` redirecting to
  it: run on a laptop through the same `Caddyfile` (the password asked, the page and its API
  served, the path left to the backend while it is off), not yet on the machine.
- The `Release` and `Deploy` workflows on GitHub, with the `production` environment.
- `backend/update.sh` on the backend machine, including the web build with Node in Docker.
- The memory of the backend on the machine itself, under real use (see [Memory](#memory)), and
  the memory of the video machine during a call of three people.
- The Jitsi images on an Arm machine (`Standard_B2pts_v2`); they were measured on an arm64 laptop.
- The `az` commands of [Azure, inside the free services](#azure-inside-the-free-services) after
  the static web app (the token handed to GitHub, the custom domain), the labels under
  `cloudapp.azure.com` and a certificate issued for them, and that a month of the two machines and
  the database bills nothing but the two public addresses and the disk of the video machine. The
  ones up to the static web app were run on 2026-10-27.
- The security headers of the `Caddyfile` as a browser receives them:
  `curl -sSI https://api.example.org/actuator/health | grep -i strict-transport`.
