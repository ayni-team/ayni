# US08 - As a student or a tutor, I want to meet the other person in the session's room, so that the
# tutoring happens where Ayni can vouch for it.
#
# Also the part of US07 (arriving prepared) that sessions owns: the tutor reads what the student
# needs before the session.
#
# Every scenario names the test that runs it, against a real PostgreSQL, in
# JoinSessionAcceptanceTest, with the same name. The rules of the room are covered without Spring by
# SessionTest.

Feature: Meeting in the session's room

  Background:
    Given Ana booked an hour with Bruno, which scheduled their session

  Scenario: The tutor reads the session and what the student needs
    When Bruno reads the session
    Then he sees when it starts, when the room opens and what Ana needs help with
    And he does not see the room name, which is only given by joining

  Scenario: Somebody else cannot see the session or join it
    When a student who is neither Ana nor Bruno reads the session or tries to join it
    Then they are refused, whatever link they hold
    And nothing is recorded for them

  Scenario: Joining opens the room and the first participant starts the session
    Given the session starts in ten minutes
    When Ana joins
    Then she gets the room name and the session is in progress from that moment
    When Bruno joins
    Then he gets the same room, the start stays the moment Ana arrived
    And SessionStarted was published once

  Scenario: Coming back after a dropped connection
    Given Ana joined the session
    When she joins again
    Then she gets the room again, and her first arrival is what stays recorded

  Scenario: Both arriving at the same moment start the session once
    When Ana and Bruno join at the same moment
    Then both are let in
    And SessionStarted is published once

  Scenario: The room is not open too early, after the end, or for a cancelled session
    Then joining an hour before the start is refused, saying the room opens fifteen minutes before
    And joining after the scheduled end is refused
    And joining a cancelled session is refused

  Scenario: A session of another university, or one that does not exist, is not found
    Then joining it is answered as not found
