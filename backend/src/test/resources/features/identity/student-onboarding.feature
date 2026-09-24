Feature: Student onboarding

  As a university student
  I want to complete my onboarding step by step
  So that Ayni can prepare my academic tutoring profile

  Scenario: Complete the profile step
    Given an authenticated student whose current onboarding step is PROFILE
    When the student updates their editable profile information
    Then the profile information is updated
    And the onboarding step becomes ACADEMIC_RECORD

  Scenario: Import the academic record
    Given an authenticated student whose current onboarding step is ACADEMIC_RECORD
    When the student imports their academic record
    Then the approved courses reported by the academic system are stored
    And the onboarding step becomes LEARNING_INTERESTS

  Scenario: Academic fields remain read-only
    Given an authenticated student
    When the student updates their profile
    Then only the photo and bio can be modified
    And the institutional email remains unchanged
    And the student code remains unchanged
    And the career remains unchanged
    And the current academic term remains unchanged

  Scenario: Prevent skipping the profile step
    Given an authenticated student whose current onboarding step is PROFILE
    When the student tries to import their academic record
    Then the request is rejected
    And no academic records are imported