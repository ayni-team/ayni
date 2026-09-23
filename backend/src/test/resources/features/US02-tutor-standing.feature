# US02 - As a student who found several free blocks at the same hour, I want to compare the tutors
# who offer them, so that I choose the one with the best record teaching that subject.
#
# These scenarios cover the part of the story that reputation owns: the standing of a tutor in one
# skill, which the tutor profile shows. They are covered by TutorStandingTest, ReputationServiceTest
# and RecordCompletedSessionTest. The profile itself, with the most frequent tags and the
# accredited skills, arrives with its own task.
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
