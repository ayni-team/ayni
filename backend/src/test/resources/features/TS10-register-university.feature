# TS10 - As the platform, I want to bring a new university in by configuration, so that growing
# beyond the first universities does not mean changing or deploying code.
#
# Registering and listing universities is built (TS10-T1). Each scenario below with the same name
# runs over HTTP against a real PostgreSQL in RegisterUniversityAcceptanceTest, and the contract of
# the two endpoints, with the race on the code and on the domain, in
# UniversityAdministrationAcceptanceTest.
#
# The students of a new university cannot be activated yet: the simulated academic system answers
# only for UPC and PUCP (ADR 0006), and teaching it another university is a change to its adapter
# that is not part of this task. Whatever needs a student of the new university to get in for the
# first time is tagged @pending and says why.
#
# Scenarios 2, 3 and 4 run on what identity owns: profiles, the directory, the tenant and its
# credit policy. What the other modules keep per university is theirs to prove. A student of the
# new university the test needs is stored directly, because activating one is scenario 1.

Feature: Registering a university

  Background:
    Given the university "UPC" is registered and has an active student

  # Pending: the first access of a student is an activation, and the simulated academic system only
  # knows UPC and PUCP. The registration half, with its domains and its configuration kept and the
  # domain leading to the university, is covered by "Registering a university creates its baseline
  # policy and announces it" and "The email domain of a registered university leads to it" in
  # UniversityAdministrationAcceptanceTest.
  @pending
  Scenario: Onboarding a university by configuration
    When the administrator registers a university with its domains and its configuration
    Then a student with an email of that university can request access
    And the student signs in and operates as any other student
    And no code was changed or deployed

  Scenario: Isolation from the first day
    Given a university registered by the administrator, with a student of its own
    When the student of each university asks for their profile in the other university
    Then the answer is that nobody was found, in both directions
    And the student of the new university sees none of the students or courses of the others
    And the directory counts each university's people apart

  Scenario: Each university has its own policies
    Given another registered university with a minimum teaching grade of 12.00 and 4 initial credits
    When the administrator registers a university with a minimum teaching grade of 16.50 and 9 initial credits
    Then its minimum teaching grade and its initial credit policy are the ones it was registered with
    And the grade and the credit policy of "UPC" and of the other university do not change

  Scenario: No interruption of the service
    Given students of "UPC" keep asking for their profile
    When the administrator completes the registration of several universities
    Then every request of the students of "UPC" is answered normally
    And "UPC" is still active, with the same configuration
