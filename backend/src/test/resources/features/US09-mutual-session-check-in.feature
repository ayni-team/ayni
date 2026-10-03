# US09 - Both participants' first arrivals are recorded and visible, and a session starts only
# after both have checked in by the ten-minute deadline.
#
# The join/read scenarios are exercised against PostgreSQL in JoinSessionAcceptanceTest. The
# deadline and absence side effects are covered by AbandonTutorNoShowUseCaseTest and the US06 tests.

Feature: Mutual session check-in

  Background:
    Given Ana booked an hour with Bruno, which scheduled their session

  Scenario: Both participants check in and the session starts
    Given the session starts in ten minutes
    When Ana joins
    Then the session stays scheduled and Ana's check-in time is recorded
    When Bruno joins
    Then the session starts and both check-in times are recorded
    And SessionStarted is published once

  Scenario: A participant can see that the other person has arrived
    Given Ana joined the session
    When Bruno reads the session
    Then he can see Ana's check-in time

  Scenario: One absent participant is recorded after ten minutes
    Given only Bruno joined before the check-in deadline
    When the ten-minute deadline passes
    Then the session is abandoned and Ana is recorded as absent
    And the booking is marked as a no-show without refunding or penalizing Bruno

  Scenario: Neither participant arrives
    Given neither participant joined before the check-in deadline
    When the ten-minute deadline passes
    Then the session is abandoned and both absences are recorded
    And there is no refund or tutor no-show record
