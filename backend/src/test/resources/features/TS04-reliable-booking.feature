# TS04, for the booking - Confirming a booking is atomic: the charge, the booking and the hours are
# written in one transaction, and what other modules do with it happens only once it committed.
#
# Each scenario has a test with its name as display name, against a real PostgreSQL:
# BookingConfirmationDatabaseTest for the transaction of the booking and
# SessionOnBookingConfirmedTest (sessions) for what is done after it. The delivery of the events
# between modules is TS04-reliable-events.feature.

Feature: A booking is confirmed whole or not at all

  Background:
    Given a tutor of the university "UPC" who is free tomorrow at 09:00, Lima time
    And a student with credits who holds that hour

  Scenario: A failure after everything was written leaves the balance intact and the hour free
    When the confirmation fails after the charge, the booking and the hour were written
    Then the student's balance is as it was
    And no booking exists and the hour can be held again

  Scenario: The wallet's refusal reaches the student as it is, never as an unexpected rollback
    Given the student has fewer credits than the hour costs
    When the student confirms
    Then the student is told how many credits are missing
    And nothing is charged and the hours are free again

  Scenario: A confirmation whose transaction rolled back creates no session
    When a booking confirmation is published in a transaction that rolls back
    Then no session exists for it

  Scenario: A late confirmation of a booking already cancelled creates no session
    Given the booking was cancelled before its confirmation was heard
    When the confirmation arrives
    Then no session exists for it

  # The compensation of the reservation saga that ADR 0004 lists next to TS04 was not built (ADR
  # 0005, amendment of 2026-10-22): the refund of a cancellation is a synchronous call inside the
  # cancellation's transaction, so nothing is left to compensate later. No test proves the rollback
  # of a cancellation whose refund fails.
  @pending
  Scenario: A cancellation whose refund fails leaves the booking as it was
    Given a confirmed booking
    When the student cancels and the refund fails
    Then the booking is still confirmed and the credits are still charged
