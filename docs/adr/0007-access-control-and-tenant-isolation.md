# 7. Access control and tenant isolation

- **Status:** Accepted
- **Date:** 2026-09-30

## Context

`0004-start-simple-add-quality-attributes-per-iteration.md` left two properties out of the starting
point: authentication (TS03) and isolation enforced by the database (TS01). Today the API trusts the
`X-Tenant-Id` and `X-User-Id` headers, so anyone can send another university's code and be believed,
and a forgotten `tenant_id` filter leaks data between universities.

This is the second iteration of ADD. Its drivers are who may do what, and that no university ever
sees another's data. Both rest on the same structure: modules with their own schemas and a
`tenant_id` on every scoped table. That is why they are decided together.

## Decision

**How a person gets in**

- **Passwordless access.** The person asks for a single use link, sent to their institutional email.
  Following it opens the session. There are no passwords to store, leak or reset.
- **No external identity provider.** Keycloak or similar was considered and discarded. Running it
  costs more than a team of six can carry, and the product does not need what it adds.
- **Opaque session token, stored as a hash and revocable.** A JWT was considered. An opaque token is
  revoked instantly by marking one row, and a leaked database does not hand out valid tokens because
  only the hash is stored. A JWT would stay valid until it expires.

**Who the request belongs to**

- **The university comes from the session, never from a header.** The request filters read the token,
  look up the session and bind the university and the user to `TenantContext` and `CurrentUser`.
  `X-Tenant-Id` and `X-User-Id` stop being read.

**What each role may call**

- **Role by route group.** `/api/v1/admin/**` requires `PLATFORM_ADMIN`, `/api/v1/coordinator/**`
  requires `COORDINATOR`, and everything else requires `STUDENT`. `/api/v1/access/request` and
  `/api/v1/access/confirm` are public, because they are how somebody gets a session.
- **Ownership is checked in the use case.** A role says what kind of person you are, not that this
  booking is yours. Only the use case has the data to decide that.

**How the database enforces isolation**

- **Row Level Security on every table with `tenant_id`.** A forgotten filter returns nothing instead
  of leaking.
- **The university is set per transaction**, with `SET LOCAL app.tenant_id`, not per connection.
  Connections are pooled and reused; a value that outlived the transaction would carry one
  university's identity into the next request.
- **Two database roles.** The application connects with a role that has no `BYPASSRLS`. Flyway
  migrations run with the owner role. A bypassing role in the application would make every policy
  decorative.
- **The administrator does not set a university**, so it sees no tenant scoped rows. It reads only
  the aggregates of `analytics`, which is what the product promises it can see.
- **Four tables are exempt from RLS**, each for a reason: `identity.tenants` and
  `identity.platform_admins` are not owned by any university, and `identity.access_links` and
  `identity.user_sessions` must be read before the university is known, to resolve the token that
  reveals it. Their `tenant_id` is nullable for that reason too.

## Consequences

- Every request reads one row by index to resolve the session. That is the price of revoking at once.
- Sessions are long, so a stolen token is useful for a long time. The hash and the revocation
  mitigate it; they do not remove it.
- **Every new table with `tenant_id` needs its policy**, and an automated test verifies it. A table
  added without one fails the build rather than reaching review.
- Getting in depends on email delivery. If the mailbox does not receive the link, the person cannot
  enter (CRN-07).
- The exempt tables are the ones nothing protects at the database level. Their queries are reviewed
  with extra care.
