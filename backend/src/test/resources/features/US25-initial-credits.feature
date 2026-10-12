# US25 - As a student who has just arrived and has nothing to book a first tutoring with, I want to
# receive the credits my university assigns, so that I can ask for help from the first day.
#
# Each scenario that is built is exercised against PostgreSQL by a test of the same name in
# WalletEventListenersTest, which publishes StudentActivated the way identity does.
#
# The targeted allocation, the one only some students qualify for and that is delivered by period,
# is not built: wallet has no notion of periods yet and identity has no rule for who qualifies. Those
# scenarios are tagged @pending and listed in the pull request.
Feature: Receiving the credits my university assigns

  Background:
    Given a student of the university "UPC"
    And the university has a baseline policy of 5 credits valid for 30 days

  Scenario: Initial credits of the term
    Given the student completes their first access to the platform
    When they open their wallet
    Then they see the 5 initial credits with their expiry date

  @pending
  Scenario: Periodic delivery of the targeted allocation
    Given the student qualifies for the targeted allocation under the policy of their university
    When a delivery period starts
    Then they receive the part of that period and are notified

  @pending
  Scenario: A student who does not qualify
    Given the student does not qualify for the targeted allocation
    When they open their wallet
    Then they see only the initial credits every student receives

  Scenario: The allocation is not duplicated
    Given the student already received the initial credits
    When their activation is announced again
    Then no credits are granted a second time

  @pending
  Scenario: A change in the policy of the university
    Given the university changes its allocation policy
    When the next delivery runs
    Then the policy in force is applied and the credits already received are not affected
