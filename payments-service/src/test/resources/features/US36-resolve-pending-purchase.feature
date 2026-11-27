# US36 in the payments service, over HTTP and PostgreSQL in PaymentsAcceptanceTest: every scenario
# below has a test with exactly its name. The first three share one purchase the provider confirms
# only when reconciliation asks, after answering PENDING. Reconciliation no longer asks identity for
# the universities: it visits those with a pending purchase in ayni_payments. The wallet and the
# email are the monolith's (purchase-confirmed-inbound.feature); here they are the fact owed to it.

Feature: Resolve a pending credit purchase

  Background:
    Given the student has started a credit purchase

  Scenario: A purchase is confirmed after the student leaves the platform
    Given the provider initially reports that the payment is pending
    When reconciliation later finds that the provider confirmed the payment
    Then the purchase appears as "CONFIRMED"
    And PurchaseConfirmed is recorded for the wallet and the confirmation email

  Scenario: The student is told a pending payment is still being processed
    Given the provider has not returned a final payment result
    When the student lists their purchases
    Then the purchase appears as "PENDING"
    And the student is told not to retry the payment

  Scenario: Repeated provider confirmation does not credit twice
    Given reconciliation has already processed the provider confirmation
    When the same provider confirmation is processed again
    Then PurchaseConfirmed has been recorded only once

  Scenario: A purchase with no result expires and releases its monthly allowance
    Given the payment has remained pending past its 24-hour deadline
    When reconciliation closes the purchase
    Then the purchase appears as "EXPIRED"
    And nothing is owed to the wallet
    And the monthly allowance is available for another attempt

  Scenario: The rest of the platform remains available when the provider is down
    Given the payment provider is unavailable
    When the student starts a purchase
    Then only that purchase remains pending
    And the student can still list purchases

  Scenario: Reconciliation visits every university with pending purchases and only its own rows
    Given two universities each have a pending purchase
    When reconciliation runs with no university bound
    Then both purchases are confirmed, each under its own university
