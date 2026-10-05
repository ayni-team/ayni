# US30 - As a coordinator about to grant an extracurricular credit, I want to review which tutoring
# sessions the presented hours come from, so that I support my decision with concrete evidence and not
# only with a total.
#
# Each scenario below is covered by a test method of the same name in
# RecognitionSessionEvidenceAcceptanceTest, which exercises it over HTTP against a real PostgreSQL. The
# identity of each person, the sessions, the bookings and the catalogue are stubbed, since they are
# other modules; the requests are submitted through the real endpoint of US28 and everything else is
# the real application.
#
# The figures of the request and its sessions are the ones copied when it was submitted, so the list
# and the total cannot disagree. A session is only reached through the request it backs.
#
# Attendance: the register shows both participants and whether their presence was verified, which is
# whether the session ended completed (both confirmed their code). The time at which each one arrived
# or confirmed is not exposed by sessions and is not shown.
#
# The scenarios after the fourth are not on the card. They pin down what it leaves out: which sessions
# can be reached through a request and a coordinator of another university.

Feature: Evidence of the sessions behind a recognition request

  Background:
    Given a coordinator of the university "UPC"
    And a student who submitted a request backed by a course session of 12 hours and a tool session of 8

  Scenario: Detail of the sessions that support it
    When the coordinator reads the sessions that back the request
    Then they see each one with its date, its duration and the course or skill that was taught

  Scenario: Evidence of each session
    When the coordinator opens one of the sessions
    Then they see the register of attendance of both parties, the verified presence and the rating the tutor received

  Scenario: Consistency between the detail and the total
    When the coordinator adds up the hours of the sessions listed
    Then the result is the total presented

  Scenario: Restricted access
    When a student who is not a coordinator tries to read the sessions or the evidence
    Then the system denies access
    And a person who is not of the university does not find them either

  Scenario: A session that does not back the request
    Given a session of another request of the same university
    When the coordinator opens it through this request
    Then it is not found

  Scenario: A request of another university
    When the coordinator of another university reads its sessions
    Then it is not found
