Feature: Configure university credit policy

  Scenario: A coordinator changes the baseline credit policy
    Given an active coordinator belongs to a university
    And the university has a current baseline credit policy
    When the coordinator changes the credit amount and validity
    Then the previous policy is marked as superseded
    And the previous policy remains stored
    And a new baseline policy becomes current
    And the new policy records the coordinator as its author

  Scenario: Future student grants use the new policy
    Given a coordinator superseded the baseline credit policy
    When the current baseline policy is requested
    Then the new policy is returned
    And the superseded policy is not returned as current

  Scenario: A student cannot change university credit policy
    Given an active student belongs to a university
    When the student tries to change the credit policy
    Then the request is rejected

  Scenario: Invalid credit policy values are rejected
    Given an active coordinator belongs to a university
    When the coordinator configures zero credits or zero validity days
    Then the request is rejected