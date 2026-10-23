# US40 - As a student signing in for the first time, I want to say which courses I need help with
# and in which ones I can help others, so that the platform shows me useful tutorings from the
# first day instead of an empty screen.
#
# Each scenario below is covered by a test method of the same name in
# OnboardingInterestsAcceptanceTest (skills), which exercises it over HTTP against a real
# PostgreSQL with real session tokens and the real academic directory.
#
# The steps before the last one are identity's and are already described in
# identity/student-onboarding.feature: "Complete the profile step" (PUT /api/v1/me/profile),
# "Import the academic record" (POST /api/v1/me/academic-record/import), "Academic fields remain
# read-only" and "Prevent skipping the profile step". They are not repeated here: the scenarios
# below go through those steps and assert only what US40 adds, the last step
# (POST /api/v1/me/onboarding/interests) and the step COMPLETED that GET /api/v1/me shows.
#
# Scenario 3 builds nothing new: the courses the record suggests are the ones of
# GET /api/v1/tutor/skills/suggestions (US13), asked at the step of the interests.

Feature: Declaring what I need and what I can help with

  Background:
    Given a student of a university whose minimum teaching grade is 13.00
    And the academic system reports Databases I approved with 17.00 and Calculus II with 11.00

  Scenario: Initial configuration completed
    Given I sign in for the first time
    When I complete the profile, import my academic record and say what I need and what I can teach
    Then my approved courses, what I need help with and what I can teach are registered
    And only the courses my record backs are enabled to teach, the rest are skipped
    And my onboarding step is COMPLETED

  Scenario: Both sides for everyone
    Given I am at the step of the interests
    When I send what I need without what I can offer, or the other way round
    Then the request is refused and nothing is saved
    When I send both, even empty
    Then the configuration is completed

  Scenario: Suggestions from my record
    Given my university has the record of my courses
    When I reach the step of what I can offer
    Then the courses I approved with a grade that clears the threshold are suggested
    And once I offer them they are no longer suggested

  Scenario: Later sign-ins
    Given I already completed my initial configuration
    When I sign in again
    Then my onboarding step says there is nothing left to do
    And sending the last step again changes nothing

  Scenario: Interrupted configuration
    Given I leave the configuration after the profile
    When I sign in again
    Then I resume from the academic record
    And the interests are refused until I import it
    When I import it, leave and sign in once more
    Then I resume from the interests and can finish
