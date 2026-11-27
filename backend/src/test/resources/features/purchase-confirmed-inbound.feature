# The other half of ADR 0011: the payments service records PurchaseConfirmed in its outbox and
# delivers it to POST /internal/v1/events/purchase-confirmed, at least once. The monolith publishes
# it as the payments module did, and wallet credits it with grantOnce keyed by the purchase
# (Idempotent Consumer). Every scenario has a test with exactly its name in
# PurchaseConfirmedInboundTest, over HTTP against PostgreSQL; wallet is observed only through
# WalletApi and the inbox.

Feature: A purchase confirmed by the payments service reaches the wallet

  Background:
    Given a student of the university "UPC"

  Scenario: A confirmed purchase reaching the monolith credits the student immediately
    When the payments service delivers PurchaseConfirmed for 2 credits with the internal token
    Then the answer is 202
    And the wallet balance includes 2 non-expiring purchased credits, under the purchase
    And the student receives a purchase confirmation email

  Scenario: The same confirmation delivered twice credits once
    Given the payments service delivered PurchaseConfirmed for 3 credits
    When it delivers the same purchase again, with the same event and with another
    Then every delivery is answered 202
    And the wallet contains the 3 credits once, and the student got one email

  Scenario: Purchased credits do not count towards recognition
    When a purchase of 3 credits reaches the wallet
    Then the student's earned credit total remains 0

  Scenario: An event without the internal token is refused
    When PurchaseConfirmed arrives without X-Internal-Token, with another value, or with a session
    Then the answer is a 401 and nothing is credited

  Scenario: An incomplete event is refused
    When PurchaseConfirmed arrives without credits, without a purchase, or not as JSON
    Then the answer is a 400 and nothing is credited
