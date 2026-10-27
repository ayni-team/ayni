# TS10 - As the platform, I want to bring a new university in by configuration, so that growing
# beyond the first universities does not mean changing or deploying code.
#
# Registering and listing universities is built (TS10-T1). Each scenario below with the same name
# runs over HTTP against a real PostgreSQL in RegisterUniversityAcceptanceTest, except "Onboarding a
# university by configuration", which runs in OnboardingByConfigurationAcceptanceTest with real
# session tokens (TS03); the contract of the two endpoints, with the race on the code and on the
# domain, is in UniversityAdministrationAcceptanceTest.
#
# The simulated academic system reads one directory file per university from a folder, each time it
# looks a student up (ADR 0006, amended): a new university's students get in once its file is in
# the folder, with the application running.
#
# Scenarios 2, 3 and 4 run on what identity owns: profiles, the directory, the tenant and its
# credit policy. What the other modules keep per university is theirs to prove. A student of the
# new university the test needs is stored directly, because activating one is scenario 1.

Feature: Registering a university

  Background:
    Given the university "UPC" is registered and has an active student

  # The configuration is the registration over HTTP and the university's file in the academic
  # directory, written while the application runs.
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
