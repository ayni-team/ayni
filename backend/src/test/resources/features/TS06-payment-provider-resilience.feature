# Scenarios map to PaymentProviderResilienceAcceptanceTest and
# PaymentProviderCircuitBreakerTest / ResilientPaymentProviderTest.

Feature: Keep Ayni available during a payment provider outage

  Scenario: Free platform operations remain available
    Given the payment provider is not responding
    When a student attempts a credit purchase
    Then the purchase is kept pending without blocking the wallet

  Scenario: A slow provider is timed out and new payment calls are rejected
    Given the payment provider does not respond within the configured timeout
    When another purchase request arrives while the circuit is open
    Then both attempts remain pending with explicit temporary-unavailability guidance
    And the provider is not called again while the circuit is open

  Scenario: The circuit automatically recovers when the provider responds
    Given the provider has recovered after the circuit open period
    When the half-open probe succeeds
    Then the circuit closes and the purchase is confirmed
