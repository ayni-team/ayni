# US23 - As a student I want to see my credit balance, where it came from and when it expires,
# so that I know what I can spend and what I am about to lose.
#
# The scenarios below are the acceptance criteria of the story. Each one is covered by a test
# method of the same name in WalletBalanceAcceptanceTest, which exercises it over HTTP against a
# real PostgreSQL.
#
# Dates are written as "in N days" rather than as calendar dates on purpose: a test pinned to a
# date starts failing on its own the day that date arrives.

Feature: Credit balance with its origin and expiry

  Background:
    Given a student of the university "UPC"

  Scenario: The balance shows the total and what it is made of
    Given the university granted the student 6 credits expiring in 90 days
    And the university allocated 4 more credits expiring in 180 days
    And the student earned 4 credits by teaching
    When the student asks for their balance
    Then the available balance is 14 credits
    And the balance is broken down into 6 SEED, 4 ALLOCATED and 4 EARNED credits
    And the SEED credits show the date they expire on
    And the ALLOCATED credits show a later expiry date than the SEED credits

  Scenario: Earned and purchased credits never expire, and only the earned ones count towards recognition
    Given the student earned 4 credits by teaching
    And the student purchased 2 credits
    When the student asks for their balance
    Then the EARNED credits are shown without an expiry date
    And the PURCHASED credits are shown without an expiry date
    And the EARNED credits are marked as counting towards recognition
    And the PURCHASED credits are not marked as counting towards recognition

  Scenario: A student with nothing to spend is told how to obtain credits
    Given the student has no credits
    When the student asks for their balance
    Then the available balance is 0 credits
    And the answer explains how to obtain credits

  Scenario: Credits that expired are no longer available but stay in the history
    Given the university granted the student 5 credits that expired yesterday
    And the nightly expiry has run
    When the student asks for their balance
    Then the available balance is 0 credits
    When the student asks for their movements
    Then the history shows the grant of 5 credits
    And the history shows those 5 credits leaving with reason "EXPIRY"
