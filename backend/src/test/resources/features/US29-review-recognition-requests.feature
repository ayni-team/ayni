# US29 - As a coordinator of my university, I want to review the recognition requests with all their
# evidence, so that I can decide with grounds whether the extracurricular credit should be granted.
#
# Each scenario below is covered by a test method of the same name in
# ReviewRecognitionRequestsAcceptanceTest, which exercises it over HTTP against a real PostgreSQL. The
# identity of each person, the sessions they taught and their bookings are stubbed, since identity,
# sessions and booking are other modules; everything else is the real application, and the requests
# are submitted through the real endpoint of US28.
#
# The coordinator is the one of the university, as in skills: another university's requests do not
# exist for them, and a student cannot read the queue.
#
# Alerts of the audit: audit does not detect or publish anomalies yet, so the alerts come from a port
# that has none today. The scenario that shows them replaces that port with one that has an alert.
#
# Every session of a request was verified, since only completed sessions back one: both participants
# confirmed their presence code.
#
# The scenarios after the fifth are not on the card. They pin down what it leaves out: who may do
# it, a decision that is taken once, and a decision that always says why.

Feature: Reviewing recognition requests

  Background:
    Given a coordinator of the university "UPC" and two students who submitted a request each

  Scenario: Request queue
    Given a third request that the coordinator already decided
    When the coordinator enters the recognition requests
    Then they see the two that wait with the student, the hours and since when
    And the decided one is not among them
    And the one that waited longest comes first

  Scenario: Review with evidence
    Given a request backed by two sessions, one rated 5 stars
    When the coordinator opens it
    Then they see each session with its date, how long it lasted, the presence verified and the rating

  Scenario: Visible audit alerts
    Given the audit detected something odd in one session of the request
    When the coordinator opens it
    Then the alert appears next to that session and together with the others

  Scenario: Recorded decision
    When the coordinator approves one request and rejects the other, each with a reason
    Then both decisions are recorded with who, when and the reason
    And each student reads theirs
    And the university announces both

  Scenario: Frozen figures
    Given a request submitted days ago
    And the student has taught more sessions and been rated since
    When the coordinator opens it again
    Then they see the hours, the sessions and the ratings of the moment of the submission

  Scenario: A student cannot review requests
    When a student tries to read the queue, open a request or decide one
    Then all three are forbidden and nothing changes

  Scenario: A request of another university
    When the coordinator of another university opens or decides it
    Then it is not found

  Scenario: A decision is taken once
    Given a request that was approved
    When the coordinator rejects it
    Then it is a conflict and the first decision stays

  Scenario: A decision always says why
    When the coordinator decides without a reason
    Then it is refused and the request keeps waiting
