# Demo profile - the platform administrator moves the clock of the application while it runs, so a
# presentation can show credits that expire, the window of a session that opens, the presence code
# that is sent or a hold that runs out without waiting and without restarting.
#
# This is not a story of the backlog, so it carries no US number: it is the profile the project is
# presented with (docs/demo.md). It changes nothing under dev or in a deployment.
#
# Every scenario names the test that runs it, with the same name: the clock itself without Spring
# in AdjustableClockTest; the endpoints against a real PostgreSQL and with real session tokens in
# DemoClockEndpointsTest; the application started with the demo profile alone in
# DemoProfileStartupTest; the application without it in DemoClockAbsentTest; and the video pass
# without Spring in JitsiVideoRoomPassesTest.

Feature: Demo profile with an adjustable clock

  Background:
    Given the application runs with the demo profile
    And the platform administrator signed in

  Scenario: The clock keeps running after it is moved
    Given the clock reads the real time
    When the administrator moves it two hours ahead
    Then it reads two hours later than the real time
    And three minutes later it reads three minutes more, still two hours ahead

  Scenario: The administrator reads the clock
    When the administrator reads the clock
    Then the answer has the time the application reads, the real time and the offset between them

  Scenario: The administrator moves the clock forward and back
    When the administrator moves the clock by PT2H and then by -PT30M
    Then the offset is an hour and a half
    And every rule of the application reads the moved time

  Scenario: The administrator sets the clock at an instant
    When the administrator sets the clock at an instant forty days ahead
    Then the application reads that instant, and keeps running from it

  Scenario: The administrator takes the clock back to the real time
    Given the clock was moved a month ahead
    When the administrator takes it back
    Then the offset is zero

  Scenario: A value that is not a duration or an instant is refused
    When the administrator sends a duration or an instant that is not ISO 8601, nothing, or one too far away
    Then the answer is 400 with the shape of every error
    And the clock has not moved

  Scenario: Only the platform administrator moves the clock
    When a student reads or moves the clock
    Then the answer is 403 and the clock has not moved
    And without a session the answer is 401

  Scenario: Without the demo profile the clock cannot be moved
    Given the application runs without the demo profile
    When the administrator reads or moves the clock
    Then the routes do not exist and the answer is 404
    And the application reads the real time

  Scenario: Credits expire when the clock passes their expiry
    Given a student with three credits that expire tomorrow
    And the expiry job finds nothing to expire
    When the administrator moves the clock two days ahead
    And the expiry job runs
    Then the three credits expire and the history shows them

  Scenario: The jobs run often enough to react to the clock
    Given the application started with the demo profile alone
    Then the expiry of credits and the generation of hours run every minute
    And the jobs that run at an interval run every few seconds
    And the clock is the one the administrator moves

  Scenario: Sessions outlast a jump of the clock and access links do not
    Given the demo coordinator signed in, and asked for another link
    When the administrator moves the clock a month ahead
    Then the session of the coordinator still answers
    And the link of ten minutes is refused

  Scenario: The demo starts with the data of dev and an active coordinator
    Given the application started with the demo profile alone
    Then the university, its students, the administrator and the recognition rule of dev are there
    And an active coordinator of the university is there with the configured address

  Scenario: The demo starts with the two universities of the academic documents
    Given the application started with the demo profile alone
    Then UPC and USIL are registered with what their academic documents say
    And the curriculum of each is loaded from its document
    And Ana and Lucía are at the first step of the onboarding, Bruno and Mateo finished it and teach
    And no other student is activated: Carla and the rest activate their account live
    And each university has an active coordinator

  Scenario: A video pass is signed with the real time while the clock is moved
    Given the clock is three days ahead
    When a participant joins the session and gets a video pass
    Then the pass is signed with the real time
    And it lasts what it would have lasted, and never past the end of the session
