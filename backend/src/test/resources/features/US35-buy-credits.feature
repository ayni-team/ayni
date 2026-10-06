# US35 is covered over HTTP against PostgreSQL in PaymentsAcceptanceTest. Scenario-to-test mapping:
# confirmed purchase -> aConfirmedPurchaseCreditsTheStudentImmediately
# monthly limit -> aStudentAtTheLimitIsToldTheLimitAndCreditsUsed
# remaining allowance -> aPurchaseAboveTheRemainingLimitReportsTheMaximum
# rejected payment -> aRejectedPaymentDoesNotConsumeCreditsOrTheLimit
# idempotent retry -> aRepeatedIdempotencyKeyDoesNotCreditTwice
# mismatched idempotency key -> anIdempotencyKeyCannotBeReusedForAnotherAmount
# recognition -> purchasedCreditsDoNotCountTowardsRecognition
# monthly reset -> theMonthlyAllowanceResetsAtTheNextCalendarMonth
# concurrent purchases -> concurrentPurchasesCannotExceedTheMonthlyLimit
# The provider is replaced with confirmed/rejected outcomes; wallet is observed only through WalletApi.

Feature: Buy credits for a tutoring session

  Background:
    Given a student of the university "UPC"
    And the monthly purchase allowance is 5 credits
    And each credit costs S/ 5.00

  Scenario: A confirmed purchase credits the student immediately
    When the student purchases 2 credits and payment is confirmed
    Then the purchase is confirmed for S/ 10.00
    And the wallet balance includes 2 non-expiring purchased credits
    And PurchaseConfirmed is published once

  Scenario: A student who used the monthly limit is told the limit and credits used
    Given the student purchased 5 credits this UTC calendar month
    When the student tries to purchase 1 more credit
    Then the purchase is refused with a monthly limit of 5 and 5 already used
    And the maximum available purchase is 0 credits

  Scenario: A purchase above the remaining allowance reports the maximum amount
    Given the student purchased 4 credits this UTC calendar month
    When the student tries to purchase 2 more credits
    Then the purchase is refused with a monthly limit of 5 and 4 already used
    And the maximum available purchase is 1 credit

  Scenario: A rejected payment does not consume credits or the monthly allowance
    When the student purchases 5 credits and payment is rejected
    Then the wallet balance is unchanged
    And no PurchaseConfirmed event is published
    When the student retries with a new idempotency key and payment is confirmed
    Then the wallet balance includes 5 purchased credits

  Scenario: A repeated idempotency key does not credit twice
    When the student purchases 2 credits twice with the same idempotency key
    Then both responses refer to the same purchase
    And the wallet balance includes only 2 purchased credits
    And PurchaseConfirmed is published once

  Scenario: An idempotency key cannot be reused for a different amount
    Given the student already attempted a purchase of 2 credits with an idempotency key
    When the student reuses that key to request 3 credits
    Then the request is refused as a conflicting purchase
    And the wallet balance remains 2 purchased credits

  Scenario: Purchased credits do not count towards recognition
    When the student purchases 3 credits and payment is confirmed
    Then the student's earned credit total remains 0

  Scenario: The monthly allowance resets on the first day of the next UTC calendar month
    Given the student purchased 5 credits in the previous UTC calendar month
    When the new UTC calendar month begins
    And the student purchases 5 credits and payment is confirmed
    Then the purchase is confirmed

  Scenario: Concurrent purchases cannot exceed the monthly allowance
    Given the student purchased 3 credits this UTC calendar month
    When two requests concurrently purchase 2 credits each
    Then exactly one purchase is confirmed
    And the monthly confirmed total is 5 credits
