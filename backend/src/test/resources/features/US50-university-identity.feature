# US50 - As a coordinator, I want to configure my university's logo and colours, so that Ayni
# looks like my university.
#
# Each scenario runs over HTTP against a real PostgreSQL, with the same name. The first three run
# in UniversityIdentityAcceptanceTest, with the rules of the use case in
# UpdateUniversityIdentityUseCaseTest. The ones about reading the university and editing its email
# domains run in UniversityEmailDomainsAcceptanceTest with real session tokens, with the rules of
# the domains in EmailDomainsTest and ReplaceEmailDomainsUseCaseTest. Nothing here is @pending.

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

  Scenario: A student and a coordinator read their university
    Given a student and a coordinator belong to a university
    When each of them asks for their university
    Then they get its name, logo, colours, email domains and time zone
    And the answer is the same for both and has nothing about people

  Scenario: A platform administrator has no university to read
    Given a platform administrator is signed in
    When the administrator asks for the university of the session
    Then the request is rejected

  Scenario: The administrator replaces the email domains of a university
    Given a registered university with one email domain
    When the administrator sends a new complete list of domains
    Then the university keeps exactly those domains, in lower case and without repeats
    And an email of a new domain is recognised as belonging to the university

  Scenario: The list of domains needs at least one domain
    Given a registered university with email domains
    When the administrator sends an empty list or a domain that is not one
    Then the request is rejected
    And the domains of the university do not change

  Scenario: A domain of another university is refused
    Given two registered universities, one of them suspended
    When the administrator gives one university a domain that the other one holds
    Then the request is refused as a conflict
    And the domains of both universities do not change

  Scenario: Two requests for the same domain end with one refusal
    Given two registered universities
    When the administrator gives both the same new domain at the same time
    Then one request succeeds and the other is refused as a conflict

  Scenario: Only the administrator edits the email domains
    Given a coordinator and a student belong to a university
    When either of them tries to replace the email domains
    Then the request is rejected
    And the domains of the university do not change
