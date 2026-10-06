# US36 runs over HTTP and PostgreSQL in PaymentsAcceptanceTest:
# offline confirmation -> aPurchaseConfirmedOfflineIsCreditedAndTheStudentIsNotified
# in-progress purchase -> aPurchaseConfirmedOfflineIsCreditedAndTheStudentIsNotified
# no duplicate credit -> aPurchaseConfirmedOfflineIsCreditedAndTheStudentIsNotified
# expiration without limit usage -> anUnconfirmedPurchaseExpiresWithoutConsumingTheMonthlyLimit
# provider outage isolation -> aProviderOutageLeavesThePurchasePendingWithoutBlockingOtherApis

Feature: Resolve a pending credit purchase

  Background:
    Given the student has started a credit purchase

  Scenario: A purchase is confirmed after the student leaves the platform
    Given the provider initially reports that the payment is pending
    When reconciliation later finds that the provider confirmed the payment
    Then the purchased credits are added to the student's wallet
    And the student receives a purchase confirmation email

  Scenario: The student is told a pending payment is still being processed
    Given the provider has not returned a final payment result
    When the student lists their purchases
    Then the purchase appears as "PENDING"
    And the student is told not to retry the payment

  Scenario: Repeated provider confirmation does not credit twice
    Given reconciliation has already processed the provider confirmation
    When the same provider confirmation is processed again
    Then the wallet contains the purchased credits only once
    And PurchaseConfirmed has been published only once

  Scenario: A purchase with no result expires and releases its monthly allowance
    Given the payment has remained pending past its 24-hour deadline
    When reconciliation closes the purchase
    Then the purchase appears as "EXPIRED"
    And no credits are added to the wallet
    And the monthly allowance is available for another attempt

  Scenario: The rest of the platform remains available when the provider is down
    Given the payment provider is unavailable
    When the student starts a purchase
    Then only that purchase remains pending
    And the student can still list purchases and check their wallet
