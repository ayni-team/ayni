# US26 - Notify students before university-granted credits expire.
#
# Each scenario is exercised against PostgreSQL by a test of the same name in
# WalletCreditExpiryNoticeAcceptanceTest.
Feature: Advance notices for expiring credits

  Background:
    Given a student of the university "UPC"

  Scenario: An advance notice reports the remaining amount and expiry date
    Given the university granted the student 5 credits expiring within seven days
    When the wallet expiry job evaluates upcoming credits
    Then one notice reports 5 credits and their expiry date

  Scenario: Expired credits are removed from the balance and recorded in history
    Given the university granted the student 5 credits that expired yesterday
    When the wallet processes expired credits
    Then the balance excludes those credits
    And the history records an EXPIRY movement for 5 credits

  Scenario: Earned and purchased credits do not trigger expiry notices
    Given the student earned 4 credits by teaching
    And the student purchased 2 credits
    When the wallet expiry job evaluates upcoming credits
    Then no expiry notice is published

  Scenario: A credit group is notified only once
    Given the university granted the student 5 credits expiring within seven days
    When the wallet expiry job evaluates upcoming credits twice
    Then exactly one notice is published for that group
