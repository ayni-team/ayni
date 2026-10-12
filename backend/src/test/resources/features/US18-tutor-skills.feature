# US18 - As a tutor accredited through different paths, I want to see where each skill I offer
# stands and what is missing to confirm it, so that I know where to focus before the skill is
# taken away from me.
#
# Each scenario below is covered by a test method of the same name in
# TutorSkillsAcceptanceTest, which exercises it over HTTP against a real PostgreSQL.
#
# Scenarios 2, 3 and 4 are tagged @pending on purpose. They describe a "provisional" skill that
# is confirmed, or sent back to pending, by the average rating of the first sessions taught. The
# model that was built has no such state: a course is enabled only by grade and a global tool only
# by reviewed evidence (see state-offered-skill.puml and the skills section of data-model.md), so
# there is no skill that can be provisional. They stay here until the lead decides whether to drop
# them or to redesign them, and the pull request lists them under "Still pending".

Feature: Status of the skills a tutor offers

  Background:
    Given a tutor of the university "UPC"
    And the university's minimum teaching grade is 13.00

  Scenario: Listing of offered skills
    Given the tutor offers two university courses
    And the tutor withdrew one of them
    When the tutor opens their skills
    Then each skill shows its status, its accreditation path and the date its status changed
    And the withdrawn one is listed too

  @pending
  Scenario: Confirmation of a provisional accreditation
    Given a skill is provisional and the tutor taught the required sessions
    When the average rating of those sessions reaches the minimum
    Then the skill becomes accredited and the tutor is notified

  @pending
  Scenario: Provisional skill that does not reach the minimum
    Given a skill is provisional and the tutor taught the required sessions
    When the average rating of those sessions is below the minimum
    Then the skill goes back to pending and stops appearing in the search

  @pending
  Scenario: Progress of a provisional skill
    Given a skill is provisional
    When the tutor looks at it
    Then they see how many sessions they taught and how many are missing for the review

  Scenario: Stop offering a skill
    Given the tutor offers a university course
    When the tutor withdraws it
    Then the skill is listed as withdrawn
    And the rest of the platform is told it was withdrawn, so it leaves the search
    # Confirmed bookings are not touched: skills never writes to booking, and the only listener of
    # the event is the search projection, which keeps hours that are already booked out of it.

  Scenario: A withdrawn skill cannot be withdrawn again
    Given the tutor withdrew a course
    When the tutor withdraws it again
    Then the request is refused as a conflict and nothing is announced

  Scenario: Another tutor's skill cannot be withdrawn
    Given another tutor of the same university offers a course
    When the tutor tries to withdraw it
    Then the request is refused as forbidden and the skill stays enabled

  Scenario: A skill that does not exist is not found
    When the tutor withdraws a skill that does not exist
    Then the answer is not found

  Scenario: Offering a withdrawn course again
    Given the tutor withdrew a course and its grade still clears the threshold
    When the tutor asks what they could offer
    Then the withdrawn course is suggested again
    When the tutor offers it
    Then the same skill is enabled again and the tutor still has one skill for it
