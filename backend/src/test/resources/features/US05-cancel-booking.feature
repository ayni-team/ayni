# US05 - As a student or tutor, I want to cancel a tutoring booking, so that the
# booking, credits, tutor agenda and session all reflect the cancellation.
#
# The cancellation rules are covered by CancelBookingUseCaseTest. Session status
# propagation is covered by CancelScheduledSessionUseCaseTest, and wallet handling
# of late cancellation events is covered by WalletBookingCancelledListenerTest.
#
# Cancellation is based on the scheduled start (starts_at). Exactly twelve hours
# before the start is not late; less than twelve hours requires explicit confirmation
# and does not issue a refund.

Feature: Cancelling a tutoring booking

  Scenario: Cancellation at least twelve hours ahead returns the credits
    Given Ana booked a tutoring session with Bruno
    And the session starts at least twelve hours from now
    When Ana cancels the booking
    Then the booking and its session are cancelled
    And the credits are returned with their original types and expiry dates
    And the booked hour is available again
    And BookingCancelled is published

  Scenario: A late cancellation requires confirmation and has no refund
    Given Ana booked a tutoring session with Bruno
    And the session starts in less than twelve hours
    When Ana requests to cancel the booking without confirming the late cancellation
    Then the system warns that no refund will be issued
    And the booking, session, credits and booked hour remain unchanged
    When Ana confirms the late cancellation
    Then the booking and its session are cancelled
    And no refund is issued
    And BookingCancelled is published as a late cancellation

  Scenario: A cancelled booking is no longer reserved in the tutor's agenda
    Given Ana booked a tutoring session with Bruno
    When Ana cancels the booking
    Then the booked hour is no longer reserved in Bruno's agenda

  Scenario: A session cannot be cancelled at or after its scheduled start
    Given Ana booked a tutoring session with Bruno
    And the scheduled start time has arrived
    When Ana requests to cancel the booking
    Then the cancellation is rejected
    And the booking and session are not changed
    And the credits are not refunded
