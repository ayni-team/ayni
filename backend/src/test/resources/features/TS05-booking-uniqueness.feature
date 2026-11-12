# TS05 - An hour of a tutor is booked by one student at most, however many try at the same instant
# (QA-05 of the quality attribute scenarios: six students at once).
#
# Each scenario has a test with its name as display name, against a real PostgreSQL with each
# student on a connection of their own: HoldRaceTest for the hold and
# BookingConfirmationDatabaseTest for the confirmation. The cancellation has its own race in
# US05 (CancelBookingRaceTest).

Feature: One hour, one booking, under concurrency

  Background:
    Given a tutor of the university "UPC" who is free tomorrow at 10:00, Lima time

  Scenario: Of six students holding the same hour at once, exactly one gets it
    Given six students who start holding the hour at the same instant
    Then exactly one of them holds it
    And the other five are told that the hour is not available

  Scenario: Two confirmations racing for one hour are decided by the version of the hour
    Given two students who confirm the same hour at the same instant
    Then exactly one booking exists
    And the student who lost pays nothing

  Scenario: The student who loses the race is told that the hour was just taken
    Given two students who confirm the same hour at the same instant
    Then the one who lost is told that the hour is not available, not that something failed
