# US50 - As a coordinator, I want to configure my university's logo and colours, so that Ayni
# looks like my university.
#
# Each scenario runs over HTTP against a real PostgreSQL, with the same name, in
# UniversityIdentityAcceptanceTest, and the rules of the use case in
# UpdateUniversityIdentityUseCaseTest. Nothing here is @pending.

Feature: Configure university identity

  Scenario: An active coordinator updates their university identity
    Given an active coordinator belongs to a university
    When the coordinator updates the university logo and colours
    Then the logo is stored for that university
    And the primary colour is stored
    And the secondary colour is stored

  Scenario: A student cannot modify university identity
    Given a student belongs to a university
    When the student tries to update the university identity
    Then the request is rejected

  Scenario: A coordinator cannot modify another university
    Given an active coordinator belongs to a university
    When the coordinator updates the university identity
    Then the university is taken from the authenticated tenant context
    And no university code is accepted in the request