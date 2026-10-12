# US47 - As a coordinator who answers for the validity of the recognitions, I want the system to
# alert me about patterns that suggest credits are being accumulated artificially, so that I can
# step in before recognising hours that nothing supports.
#
# Each scenario runs over HTTP against a real PostgreSQL, with the same name, in
# AnomalousUsageAcceptanceTest. The alerts are produced by publishing the real events inside a
# transaction, never by inserting rows: the two that audit detects as an event arrives, and the one
# that a job finds by looking at what the activity log recorded, which the test asks for by calling
# the use case the job calls. The rules are covered without Spring by ShortSessionRuleTest,
# RepeatedPairRuleTest and AnomalyTest, and what each listener does by
# AnomalyEventListenersTest.
#
# Two scenarios close what other stories left to audit: an abnormally short session, scenario 5 of
# US11, and a failed presence verification, scenario 3 of US54. Two patterns of the model are
# @pending and the pull request lists them under "Still pending".

Feature: Alerts of anomalous usage

  Background:
    Given a university with an active coordinator, a tutor and a student

  Scenario: Alert generated
    Given the tutor and the student completed more sessions together than is usual in the period
    When the analysis completes
    Then an alert is generated with the people and the sessions involved
    And running the analysis again does not generate it twice

  Scenario: Support of the alert
    Given an alert about a pair that met more than is usual
    When the coordinator opens it
    Then they see the pattern that raised it, the people involved and the sessions that support it
    And an alert of another university does not exist for them

  Scenario: Dismissing an alert
    Given an open alert
    When the coordinator dismisses it with a reason
    Then the alert is closed, with who dismissed it and when
    And the decision is recorded in the activity log
    And it is no longer one of the open alerts of its session

  Scenario: The platform does not stop for the analysis
    Given the analysis cannot be done
    When a session is completed
    Then the session stays completed and the activity is recorded
    And an operation that is rolled back is not analysed

  Scenario: Abnormally short session
    Given a session was booked for an hour
    When it is completed after lasting a small fraction of the hour
    Then an alert about its duration is generated, once, naming the tutor and the session
    And a session that lasted enough raises none

  Scenario: Failed presence verification
    Given a session ended without the student confirming their presence
    When it is closed as unverified
    Then an alert about the presence is generated, once, naming the people and the session

  # Not a scenario of the story: who may see and decide the alerts.
  Scenario: Only a coordinator of the university reaches the alerts
    Given a student, a restricted coordinator and somebody the university does not know
    When any of them lists, opens or dismisses an alert
    Then access is refused

  # Not a scenario of the story: two coordinators deciding the same alert.
  Scenario: Two coordinators dismissing the same alert at once
    Given an open alert
    When two coordinators dismiss it at the same moment
    Then one of them dismisses it and the other is told it was already resolved
    And the decision is recorded once

  # Not built. A pattern that depends on what is usual for each tutor needs a baseline that nothing
  # computes yet: how often a tutor teaches, measured against their own history and against the
  # university. It is a design decision of its own.
  @pending
  Scenario: Unusual frequency of use
    Given a tutor who teaches far more often than their own history and their university
    When the analysis completes
    Then an alert about the frequency is generated

  # Not built. The whiteboard (US10) does not exist yet, so there is no activity to compare a session
  # with.
  @pending
  Scenario: No activity on the whiteboard
    Given a completed session in which nothing was drawn
    When the analysis completes
    Then an alert about the lack of activity is generated
