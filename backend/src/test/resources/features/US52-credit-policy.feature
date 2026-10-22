# US52 - As a coordinator, I want to set the initial credits my university grants, so that the
# policy fits what the university can afford.
#
# Each scenario runs over HTTP against a real PostgreSQL, with the same name, in
# CreditPolicyAcceptanceTest, with the rules of the use case in UpdateCreditPolicyUseCaseTest and
# of the entity in CreditPolicyTest. The same file holds the race of two coordinators replacing the
# policy at once. The scenarios about the audit log run in ConfigurationChangesAuditAcceptanceTest
# with real session tokens, and the events are published by the use case inside its transaction
# (UpdateCreditPolicyUseCaseTest). Nothing here is @pending.

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

  Scenario: A credit policy change is recorded in the audit log
    Given an active coordinator belongs to a university
    When the coordinator changes the baseline credit policy
    Then the coordinator reads an entry of the audit log about the policy
    And the entry names the coordinator as who acted, the kind of policy, the credits and the validity days

  Scenario: A rejected credit policy change leaves no entry
    Given an active coordinator and a student belong to a university
    When the coordinator sends a credit amount of zero and the student tries to change the policy
    Then both requests are rejected
    And the audit log has no entry about the credit policy

  Scenario: The entry of a credit policy change is not seen by another university
    Given a coordinator changed the credit policy of their university
    When the coordinator of another university reads their audit log
    Then it has no entry about the credit policy

  # Not a scenario of the story: the contract of the listener, which an event delivered again must
  # not write twice.
  Scenario: The same credit policy change delivered twice is recorded once
    Given a coordinator changed the credit policy and the audit log has its entry
    When the same event is delivered twice more
    Then the audit log still has one entry about that policy

  # Not a scenario of the story: the console of the coordinator has to show the policy it changes.
  Scenario: A coordinator reads the current baseline credit policy
    Given an active coordinator belongs to a university
    When the coordinator asks for the credit policy
    Then the current baseline policy is returned
    And after changing it the new one is what is returned

  Scenario: Only an active coordinator of the university reads its credit policy
    Given a student, a restricted coordinator and a coordinator of another university
    When any of them asks for the credit policy
    Then the answer is forbidden
