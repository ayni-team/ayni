# US41 - As a student who started a new term and approved more courses, I want to update what I
# need and what I offer, so that the tutorings I receive and the ones I can give still match my
# current situation.
#
# Each scenario below is covered by a test method of the same name in
# UpdateAcademicProfileAcceptanceTest (skills), which exercises it over HTTP against a real
# PostgreSQL with real session tokens and the real academic directory.
#
# What I need is replaced as a whole with PUT /api/v1/me/learning-interests. What I teach is still
# managed with POST and DELETE /api/v1/tutor/skills (US13, US18), and the record is brought up to
# date with POST /api/v1/me/academic-record/import.
#
# Scenario 3 is the rule of "Academic fields remain read-only" in identity/student-onboarding.feature.
# That file describes it during the onboarding; the test here checks it once the onboarding is
# over, from the student's own profile, and does not describe it again.
#
# Scenario 1 says the recommendations follow the new needs. Recommendations are not built yet: the
# test proves the needs that feed them are replaced.

Feature: Updating my academic profile

  Background:
    Given a student of a university who completed the onboarding

  Scenario: Updating my needs
    Given the courses I need help with changed
    When I update them
    Then the whole set of what I need is the new one
    And the step of my onboarding is not touched

  Scenario: Newly approved courses
    Given I approved courses in the previous term
    When I bring my record up to date in the new term
    Then those courses are among what I can offer as a tutoring

  Scenario: Data that comes from my university
    Given my name, code and career come from my university
    When I review my profile
    Then I see them but cannot change them from the platform

  Scenario: Changes do not affect what is already committed
    Given I have a confirmed tutoring
    When I update what I need, and my tutor brings their record up to date
    Then the tutoring stays as it was
