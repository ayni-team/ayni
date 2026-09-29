# US02 - As a student who found several free blocks at the same hour, I want to compare the tutors
# who offer them, so that I choose the one with the best record teaching that subject.
#
# These scenarios cover the part of the story that reputation owns: the standing of a tutor in one
# skill, which the tutor profile shows. The rules are covered by TutorStandingTest,
# ReputationServiceTest, RecordCompletedSessionTest and TutorStandingQueryTest. The endpoint
# GET /api/v1/tutors/{id}/standing runs over HTTP against a real PostgreSQL in
# TutorStandingControllerAcceptanceTest, one test per scenario of the second part with the same
# name. The profile itself, with the most frequent tags and the accredited skills, arrives with its
# own task.
#
# The threshold follows backend-guide.md and data-model.md: below three ratings there is no average.

Feature: A tutor's standing in one skill

  Background:
    Given a tutor of the university "UPC" who teaches "Calculus I"

  Scenario: A completed session is counted in the skill it was about
    Given the tutor has no standing in "Calculus I" yet
    When a session of "Calculus I" taught by the tutor is completed
    Then the tutor's standing in "Calculus I" shows 1 session taught

  Scenario: Tutor without enough history
    Given the tutor has 2 ratings in "Calculus I" averaging 4.50 stars
    When a student looks at the tutor's standing in "Calculus I"
    Then no average is shown
    And the tutor is marked as new

  Scenario: Tutor with enough history
    Given the tutor has 3 ratings in "Calculus I" averaging 4.33 stars
    When a student looks at the tutor's standing in "Calculus I"
    Then the average shown is 4.33 stars

  # Asking the endpoint for a tutor's standing in one skill.

  Scenario: A tutor's standing in the requested skill
    Given the tutor has taught "Calculus I" 8 times with 5 ratings averaging 4.60 stars
    When a student asks for the tutor's standing in "Calculus I"
    Then the answer shows 8 sessions taught, 5 ratings and an average of 4.60

  Scenario: A new tutor who never taught the skill
    Given the tutor is enabled for "Calculus I" but has never taught it
    When a student asks for the tutor's standing in "Calculus I"
    Then the answer shows 0 sessions taught and 0 ratings
    And no average is shown, so the tutor is shown as new

  Scenario: A tutor who does not teach the skill
    Given the tutor is not enabled for "Calculus I" and has never taught it
    When a student asks for the tutor's standing in "Calculus I"
    Then the answer is not found

  Scenario: Another university's standing is not visible
    Given the same tutor has a standing in "Calculus I" at another university
    When a student of "UPC" asks for it
    Then the other university's figures are not shown

  Scenario: A request without a university
    When the standing is asked for without the X-Tenant-Id header
    Then the request is refused as a bad request, in the common error shape

  Scenario: A request without the skill, or with an id that is not a UUID
    When the standing is asked for without catalogItemId, or with an id that is not a UUID
    Then the request is refused as a bad request, saying which parameter is wrong
