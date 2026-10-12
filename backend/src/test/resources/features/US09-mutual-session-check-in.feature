# US09 - As a student or a tutor, I want to confirm that the other person is in the session, so that
# the hour only starts, and only counts, when both of us are there.
#
# Entering the room is a check-in, not a start. The session starts, and SessionStarted is published
# once, when both participants have entered before ten minutes have passed since the scheduled
# start. At the ten-minute mark, if either of them is missing, the session is abandoned and its
# booking is a no-show. What that costs each side is US06's: the tutor missing with the student
# present refunds the student and counts against the tutor; the student missing with the tutor
# present, or both missing, refunds nobody and counts against nobody.
#
# Every scenario names the test that runs it, in MutualCheckInAcceptanceTest, with the same name.
# They run over HTTP against a real PostgreSQL, with the clock the tests move.
#
# The last scenario is tagged @pending on purpose: the arrival record cannot be changed through the
# API, which the scenario before it shows, but nothing in the database stops an UPDATE of joined_at.
# A trigger needs a migration, and this story ships none, so the pull request lists it under "Still
# pending".

Feature: Mutual session check-in

  Background:
    Given Ana booked an hour with Bruno, which scheduled their session

  Scenario: Both participants check in and the session starts, with the time of each arrival
    Given the session starts in five minutes
    When Ana joins
    Then the session stays scheduled and Ana's arrival is recorded
    When Bruno joins
    Then the session is in progress, from the moment Bruno arrived
    And both arrival times are recorded
    And SessionStarted is published once

  Scenario: Whoever enters sees that the other person already arrived
    Given Ana joined the session
    When Bruno reads the session and then joins
    Then he sees Ana's arrival time, before and after joining
    And Ana sees Bruno's arrival time once he arrived

  Scenario: Only one participant is present at ten minutes, so the session does not start and who was absent is recorded
    Given Ana joined the session and Bruno did not
    When ten minutes have passed since the scheduled start
    Then the session is abandoned and was never started
    And Bruno is recorded as the absent participant
    Given Bruno joined another session and Ana did not
    When ten minutes have passed since its scheduled start
    Then the session is abandoned and was never started
    And Ana is recorded as the absent participant

  Scenario: The arrival record can be consulted and cannot be modified
    Given both participants checked in
    When either of them reads the session, now or after it ended
    Then both arrival times are there
    When Ana joins again later
    Then the first arrival is what stays recorded, for both of them
    And no request changes an arrival time

  @pending
  Scenario: The database refuses to change an arrival time
    Given both participants checked in
    When anybody updates the arrival time of a participation directly in the database
    Then the update is refused
