# US13 - As a student who passed a course with a good grade, I want to offer it as tutoring and be
# enabled without extra steps, so that I start earning credits the same day I decide to.
#
# The scenarios below are the acceptance criteria of the story. Each one is covered by a test
# method of the same name in OfferApprovedCourseAcceptanceTest, which exercises it over HTTP
# against a real PostgreSQL.

Feature: Offering a university course the tutor already passed

  Background:
    Given a tutor of the university "UPC"
    And the university's minimum teaching grade is 13.00

  Scenario: Automatic enablement by academic record
    Given the tutor's academic record reports course "1ASI0657" approved with grade 15.50
    And the catalogue has course "1ASI0657" as a university item
    When the tutor offers that catalogue item
    Then the skill is enabled
    And the accreditation path is the academic record
    And the accredited grade is 15.50

  Scenario: Suggestion from the approved courses
    Given the tutor's academic record reports course "1ASI0616" approved with grade 16.00
    And the catalogue has course "1ASI0616" as a university item
    And the tutor has not offered that item yet
    When the tutor asks what they could offer
    Then the suggestions include course "1ASI0616"

  Scenario: Grade below the threshold
    Given the tutor's academic record reports course "1MAT0101" approved with grade 11.00
    And the catalogue has course "1MAT0101" as a university item
    When the tutor offers that catalogue item
    Then the offer is refused for not reaching the academic threshold

  Scenario: Course not approved
    Given the tutor's academic record has no approved course "1ASI0625"
    And the catalogue has course "1ASI0625" as a university item
    When the tutor asks what they could offer
    Then the suggestions do not include course "1ASI0625"

  Scenario: Skills that are not courses
    Given the catalogue has a global tool with no course code
    When the tutor offers that catalogue item
    Then the offer is refused for needing reviewed evidence instead
