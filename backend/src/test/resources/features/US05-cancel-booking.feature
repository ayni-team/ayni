# US05 - As a student or tutor, I want to cancel a tutoring booking, so that the
# booking, credits, tutor agenda and session all reflect the cancellation.
#
# Every scenario is run over HTTP against PostgreSQL by CancelBookingAcceptanceTest, with the
# same display name. The rules themselves are covered by CancelBookingUseCaseTest, session
# propagation by CancelScheduledSessionUseCaseTest, and what wallet does with the event by
# WalletBookingCancelledListenerTest and WalletEventListenersTest.
#
# Cancellation is measured against the scheduled start (starts_at). Exactly twelve hours before
# the start is not late.
#
# The refund rule:
#   - The student cancels twelve hours or more ahead: the credits are returned and the hours are
#     available again.
#   - The student cancels less than twelve hours ahead: they are warned first that nothing will be
#     returned, and once they confirm the booking is cancelled without a refund.
#   - The tutor cancels, at any hour: the student always gets their credits back and the hours are
#     released. Less than twelve hours ahead the tutor is asked to confirm, is warned that it will
#     be recorded, and a LATE_CANCELLATION incident is kept against them.

Feature: Cancelling a tutoring booking

  Scenario: Cancellation at least twelve hours ahead returns the credits
    Given Ana booked a tutoring session with Bruno
    And the session starts at least twelve hours from now
    When Ana cancels the booking
    Then the booking and its session are cancelled
    And the credits are returned with their original types and expiry dates
    And the booked hour is available again
    And BookingCancelled is published

  Scenario: A late cancellation by the student requires confirmation and has no refund
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

  Scenario: The tutor cancels ahead of time and the student is refunded
    Given Ana booked a tutoring session with Bruno
    And the session starts at least twelve hours from now
    When Bruno cancels the booking
    Then the booking and its session are cancelled
    And Ana gets her credits back
    And the booked hour is released
    And no incident is recorded against Bruno

  Scenario: The tutor cancels late and the student is refunded
    Given Ana booked a tutoring session with Bruno
    And the session starts in less than twelve hours
    When Bruno requests to cancel the booking without confirming the late cancellation
    Then the system warns that the cancellation will be recorded and Ana will be refunded
    And the booking, session, credits and booked hour remain unchanged
    When Bruno confirms the late cancellation
    Then the booking and its session are cancelled
    And Ana gets her credits back
    And the booked hour is released
    And a LATE_CANCELLATION incident is recorded against Bruno
    And BookingCancelled is published as a late cancellation by the tutor

  Scenario: Someone who is not part of the booking cannot cancel it
    Given Ana booked a tutoring session with Bruno
    When Carla, who is neither the student nor the tutor, requests to cancel the booking
    Then the request is refused as forbidden
    And the booking, session, credits and booked hour remain unchanged
    And BookingCancelled is not published

  Scenario: Cancelling twice changes nothing and refunds once
    Given Ana booked a tutoring session with Bruno
    And the session starts at least twelve hours from now
    When Ana cancels the booking
    And Ana cancels the booking again
    Then the second request changes nothing
    And the credits were returned once
    And BookingCancelled was published once
