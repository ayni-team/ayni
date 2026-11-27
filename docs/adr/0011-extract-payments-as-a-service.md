# 11. Extract payments as the first service

- **Status:** Accepted
- **Date:** 2026-11-25
- **Amends:** [0002](0002-modular-monolith-first.md), [0003](0003-module-map.md),
  [0005](0005-module-communication.md), [0008](0008-payment-provider-resilience.md)

## Context

Record 0002 started Ayni as a modular monolith and named `payments` as the first module to extract.
Iteration 7 of the attribute driven design (CON-06, QA-09, CRN-05, CRN-06, QA-06) decided to do it:
purchases depend on an external provider, change at their own pace, and their failure must not
reach tutoring. The boundary already existed. Nothing called into `payments` (the wallet hears
`PurchaseConfirmed`), no foreign key crossed its schema (`SchemaBoundaryTest`), and ADR 0008 had put
the provider behind a port with its own resilience.

What extraction breaks is what the monolith gave for free: one authentication, one database with
row level security, and an event delivered by the event publication registry in the same
transaction as the purchase (TS04).

The backend machine has 1 GB (887 MiB usable). The backend alone used 445 MiB of it, with about
214 MiB available and 190 MiB of swap in use, so the service has to be small.

## Decision

Five patterns, each answering one thing the monolith used to give:

1. **Strangler Fig.** The monolith keeps `/api/v1/payments/**` and the demo switch of the simulated
   provider, with the same paths, OpenAPI description and answers, in a new module
   `paymentsfacade`. It forwards each call to `payments-service` and returns the service's status
   and body unchanged. The `payments` module and its package are removed. The web application does
   not change, and the OpenAPI document is identical. The facade bounds every call to 5 seconds and
   to 10 at once. A service that is down, slow or refusing the secret gives a clear `503` for
   purchases, and searching, booking, sessions and the wallet do not wait for it (QA-06).

2. **Gateway Offloading.** Authentication stays in the monolith, which checks the session token as
   it does for every route. It forwards `X-Tenant-Id`, `X-User-Id`, `Idempotency-Key` and
   `X-Internal-Token`, a secret only the two containers share. The service publishes no port, and
   it shares a private Docker network with the backend alone; the proxy is not on that network.
   It refuses any call without the secret (`401`). The monolith reports a `401` from the service as
   a `503`, because the browser would otherwise read it as an expired session.

3. **Database per Service.** The service owns `ayni_payments`, a separate database on the same
   PostgreSQL server, with its own Flyway history. No other service connects to it. Tenant
   isolation is the one of ADR 0007: an application role without `BYPASSRLS`, `app.tenant_id` set
   by every transaction, and a policy on each table. The jobs need to know which universities have
   work before they can bind one. Two `SECURITY DEFINER` functions answer that and return only the
   tenant codes. So the reconciler no longer asks identity for the active universities: it visits
   the ones with pending purchases in its own database. The monolith's
   `V202610061118`/`V202610061119` stay in its history, because production ran them and
   `V202610150020` alters their table. Their table is now only the source of the one-off copy
   (`payments-service/data-migration/copy-purchases.sh`); dropping it is a later migration, after
   the copy is verified in production.

4. **Transactional Outbox.** Confirming a purchase inserts `PurchaseConfirmed` into
   `payments.outbox` in the same transaction, with one row per purchase (unique key). A relay sends
   the due rows every 2 seconds to `POST /internal/v1/events/purchase-confirmed` of the monolith. It
   sends with no transaction open, marks a row sent on `202`, and after a failure waits 2 seconds,
   doubling up to 5 minutes. A row is never dropped. Delivery is at least once.

5. **Idempotent Consumer.** The monolith's endpoint, guarded by the same secret, publishes
   `PurchaseConfirmed` in a transaction, so the registry stores it before answering `202`. The wallet
   credits it with `grantOnce` keyed by the purchase, and notifications records one notice per
   purchase. A delivery repeated after a lost answer therefore credits once and emails once. The
   endpoint keeps no memory of its own.

Everything ADR 0008 decided moves unchanged: 2 s timeout, a circuit that opens after 3 failures for
30 s with a half-open probe, a bulkhead of 2 with no queue, `PENDING` without retry,
reconciliation every `PT1M`, expiry after `PT24H`, a limit of 5 credits a month, S/ 5.00 a credit,
and `201`/`202`/`200`. So do its variables, which the service now reads.

The service is plain Spring Boot with JDBC. JPA and Hibernate, Actuator, springdoc and Bean
Validation are left out, because each costs memory the service does not use. The JVM runs with
SerialGC, a 96 MB heap, 80 MB of metaspace, 512 KB stacks, C1 only and a 32 MB code cache. Hikari
keeps at most 3 connections and Tomcat at most 20 threads. Heap and container limit are variables
(`AYNI_PAYMENTS_HEAP`, `AYNI_PAYMENTS_MEMORY_LIMIT`).

## Consequences

- Payments can be built, tested, released and restarted without the monolith (its own CI job,
  image and Flyway history), and the monolith keeps working while payments is down.
- A purchase is credited a moment after it is confirmed, not in the same transaction: up to one
  relay period while the monolith answers, longer while it is down, never twice, never lost.
- The wallet of a confirmed purchase depends on the relay. `payments.outbox` rows with
  `sent_at is null` and a growing `attempts` are what to look at when credits do not arrive.
- Two containers share one secret. Changing it needs both restarted together.
- Measured locally (deploy/README.md, "Memory of the payments service"), the service uses about
  165 MiB idle and 190 MiB after 200 purchases. The backend without the module used 378–393 MiB idle
  against 487 MiB for the backend of `develop` on the same machine and data. Whether both fit the
  1 GB machine has not been measured there, and the extraction is not considered deployed until
  it is.
- The demo clock (`pe.ayni.config.demo`) moves only the monolith's time. The service keeps the real
  time, so moving the clock no longer expires pending purchases or opens a new month of
  allowance.
- Circuit state stays per instance, as in ADR 0008, now per instance of the service.
