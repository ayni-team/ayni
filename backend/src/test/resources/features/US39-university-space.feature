# US39 - As a student who signs in for the first time, I want to see right away that I am in my
# own university's space, so that I trust that the tutorings, courses and people I will see are
# those of my institution.
#
# Scenario 1 runs over HTTP against a real PostgreSQL, with the same name, in
# UniversityBrandingAcceptanceTest, on the public endpoint GET /api/v1/access/university, and the
# rules of its use case in FindUniversityByEmailQueryTest.
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
