# US39 - As a student who signs in for the first time, I want to see right away that I am in my
# own university's space, so that I trust that the tutorings, courses and people I will see are
# those of my institution.
#
# Scenario 1 runs over HTTP against a real PostgreSQL, with the same name, in
# UniversityBrandingAcceptanceTest, on the public endpoint GET /api/v1/access/university, and the
# rules of its use case in UniversityReadingTest. The last five scenarios (W24) are not part of the
# story: the screens send the domain alone, because all of an email would reach the access logs, and
# the email is still accepted but deprecated. They run in the same class, with the same names.
#
# Scenarios 2 to 4 are not repeated here: they are the isolation between universities that the
# database enforces, and TS01-row-level-security.feature proves them with the same data over
# several modules:
#   - Scenario 2, scope limited to my university: "A query is scoped to the university of the
#     user" and "An automated check with data of two universities".
#   - Scenario 3, the policies of each university: the credit policy and the minimum teaching grade
#     are read per university, as TS10-register-university.feature proves in "Each university has
#     its own policies".
#   - Scenario 4, no access to another university: "A query without an explicit filter is
#     restricted by the policy" and "A request without a university gets nothing".

Feature: The space of my university

  Scenario: Identification of the university
    Given the university "UPC" claims the domain "upc.edu.pe"
    When somebody who has not signed in enters an institutional email of that domain
    Then the platform shows the name of the university and its logo and colours
    And nothing says whether the email has an account

  Scenario: Identification of the university by its domain
    Given the university "UPC" claims the domain "upc.edu.pe"
    When somebody who has not signed in sends that domain alone, in any case
    Then the platform shows the name of the university and its logo and colours
    And the answer is the same as for an email of that domain

  Scenario: A domain that no active university claims is not found
    Given a domain that no university claims, or that only a suspended one does
    When somebody sends that domain
    Then the answer is 404, the same whichever the case

  Scenario: A domain that does not look like one is answered as a bad request
    When somebody sends a domain with an at sign, with spaces, without a dot or with an empty label
    Then the answer is 400 and no university is looked at

  Scenario: Both the domain and the email are refused
    When somebody sends a domain and an email together
    Then the answer is 400 and no university is looked at

  Scenario: Neither the domain nor the email is refused
    When somebody sends neither
    Then the answer is 400 and no university is looked at
