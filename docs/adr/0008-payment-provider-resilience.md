# 8. Isolate payment-provider failures with an in-process circuit breaker

- **Status:** Accepted
- **Date:** 2026-10-06

## Context

The payment provider is an optional dependency for buying credits. Its outage must not make
searching, booking, teaching, or checking a wallet unavailable. A slow provider must not occupy
request threads indefinitely, and a sustained outage must not cause a growing queue of provider
calls. US36 also requires uncertain payments to remain pending so students do not create duplicate
charges by retrying.

## Decision

Keep resilience inside the `payments` module and behind its existing `PaymentProvider` port. The raw
adapter implements `PaymentProviderGateway`; a Spring-configured `ResilientPaymentProvider`
decorates it with:

- a configurable timeout (2 seconds by default);
- a circuit breaker that opens after three consecutive failures for 30 seconds, permits one
  half-open probe, closes after success, and reopens after failure;
- a bounded bulkhead of two concurrent calls with no waiting queue.

Timeouts, provider exceptions, an open circuit, and saturation are surfaced as
`PaymentProviderUnavailable`. A purchase that cannot obtain a definite provider result remains
`PENDING`; its response explicitly reports temporary purchase unavailability and instructs the
student not to retry. Existing reconciliation continues to query the provider. Circuit state is
local to each application instance and resets when that instance restarts.

All values are configurable through `AYNI_PAYMENTS_TIMEOUT`,
`AYNI_PAYMENTS_FAILURE_THRESHOLD`, `AYNI_PAYMENTS_CIRCUIT_OPEN_DURATION`, and
`AYNI_PAYMENTS_MAX_CONCURRENT_CALLS`. No external dependency, shared state, or new deployment
service is required.

## Consequences

- Payment-provider calls fail fast during outages; unrelated modules do not call this boundary and
  remain available.
- The system may keep a purchase pending when the provider's charge outcome is uncertain, avoiding
  an unsafe retry that could charge twice.
- Circuit state is not shared between instances, so each instance performs its own recovery probe.
- The simulated provider remains the default adapter; replacing it with a real provider does not
  change application use cases.
