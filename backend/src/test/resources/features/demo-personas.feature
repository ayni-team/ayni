# Demo personas - under the demo profile the platform administrator lists the active students and
# coordinators of every university and opens a session as any of them, so a presentation changes
# person in one request instead of going through the email each time.
#
# This is not a story of the backlog, so it carries no US number: it belongs to the demo profile
# (docs/demo.md). Outside it, the administrator reads counts of a university and never its people,
# and neither route exists (404).
#
# Every scenario names the test that runs it, with the same name. The rules without Spring (each
# university is read as itself, the order, what a session carries) are in DemoPersonasRulesTest;
# the routes against a real PostgreSQL and with the administrator's real session token in
# DemoPersonasAcceptanceTest; the application without the profile in DemoPersonasAbsentTest.

Feature: Acting as a persona in the demonstration

  Background:
    Given the application runs with the demo profile
    And the platform administrator signed in

  Scenario: An active person is listed and one who has not activated is not
    Given a university with an active student, a coordinator still pending and a restricted student
    When the administrator lists the personas
    Then only the active student is listed, with email, name, role, university, career and onboarding step

  Scenario: The people of every university are listed by university, role and name
    Given two universities with students and coordinators created out of order
    When the administrator lists the personas
    Then they are ordered by university, then role, then name

  Scenario: Acting as a person opens a session that answers as that person
    Given an active student
    When the administrator acts as that student
    Then the answer is the one confirming a link gives
    And the token it carries answers GET /api/v1/me as that student

  Scenario: A coordinator can be acted as too, whatever the case of the email
    Given an active coordinator
    When the administrator acts as them with the email in upper case
    Then a coordinator session is opened

  Scenario: The session of a persona lasts as long as one opened by a link and stores only a hash
    Given an active student
    When the administrator acts as that student
    Then the session expires after the duration of the demo profile
    And the table holds the hash of the token and never the token

  Scenario: Acting as a person consumes no access link and writes no notice
    Given an active student
    When the administrator acts as that student
    Then no access link was created or consumed and no notice was written

  Scenario: A student is refused on both routes
    Given a signed in student
    When they list the personas or try to act as someone
    Then both are answered 403

  Scenario: An email nobody has is not found
    When the administrator acts as an email that belongs to nobody
    Then the answer is 404 and the message does not repeat the email

  Scenario: A person who has not activated cannot be acted as
    Given a coordinator who has not accepted the invitation
    When the administrator acts as them
    Then the answer is 404 and no session was opened

  Scenario: A malformed email is refused
    When the administrator acts as something that is not an email, a blank one or no body at all
    Then each is answered 400

  Scenario: Without a session both routes answer 401
    When somebody with no session calls either route
    Then both are answered 401

  Scenario: Without the demo profile both routes do not exist
    Given the application runs without the demo profile
    When the administrator calls either route
    Then both are answered 404 and the beans are not in the context
