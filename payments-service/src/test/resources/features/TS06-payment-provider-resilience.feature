# TS06 in the payments service. The first scenario runs over HTTP against a real PostgreSQL in
# PaymentProviderResilienceAcceptanceTest; the timeout and the circuit, with a fake provider and
# without Spring, in ResilientPaymentProviderTest, each with the scenario's name. The states of the
# circuit itself are covered by PaymentProviderCircuitBreakerTest. Every value is the one of ADR
# 0008: a 2 s timeout, three failures open the circuit for 30 s, one half-open probe, two calls at
# once and no queue. That the rest of Ayni stays available when this whole service is down is the
# monolith's: backend/src/test/resources/features/payments-gateway.feature.

Feature: Keep Ayni available during a payment provider outage

  Scenario: Free platform operations remain available
    Given the payment provider is not responding
    When a student attempts a credit purchase
    Then the purchase is kept pending without blocking the student's list of purchases

  Scenario: A slow provider is timed out and new payment calls are rejected
    Given the payment provider does not respond within the configured timeout
    When another purchase request arrives while the circuit is open
    Then both attempts remain pending with explicit temporary-unavailability guidance
    And the provider is not called again while the circuit is open

  Scenario: The circuit automatically recovers when the provider responds
    Given the provider has recovered after the circuit open period
    When the half-open probe succeeds
    Then the circuit closes and the purchase is confirmed
