# US52 - As a coordinator, I want to set the initial credits my university grants, so that the
# policy fits what the university can afford.
#
# Each scenario runs over HTTP against a real PostgreSQL, with the same name, in
# CreditPolicyAcceptanceTest, with the rules of the use case in UpdateCreditPolicyUseCaseTest and
# of the entity in CreditPolicyTest. The same file holds the race of two coordinators replacing the
# policy at once. Nothing here is @pending.

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

  # Not a scenario of the story: the contract of the endpoint when two coordinators change the
  # policy at once.
  Scenario: Two coordinators replacing the policy at the same time leave one current baseline
    Given two active coordinators of a university with a current baseline policy
    When both replace the policy at the same time
    Then both are answered 200 and none is answered 500
    And exactly one baseline policy of the university is current
