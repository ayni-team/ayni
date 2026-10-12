# TS10-T2 - As the platform, I want to invite the coordinator of a university by email, so that
# a university has someone who configures it from its first day.
#
# Each scenario runs over HTTP against a real PostgreSQL, with the same name, in
# CoordinatorInvitationAcceptanceTest, with the rules of the use cases in
# InviteCoordinatorUseCaseTest and ConfirmAccessUseCaseTest. The same file holds what the
# scenarios do not name: an address that already has a user, and the two races of the invitation.
# Nothing here is @pending.

Feature: Invite university coordinators

  Scenario: An administrator invites a coordinator
    Given an active university
    When an administrator invites a coordinator using an institutional email
    Then a pending coordinator account is created
    And a single-use coordinator invitation link is sent

  Scenario: The invited coordinator follows the link
    Given a pending coordinator invitation
    When the coordinator opens the invitation link
    Then the coordinator becomes active
    And a session is opened
    And CoordinatorActivated is published

  Scenario: The same invitation cannot activate twice
    Given an already consumed coordinator invitation
    When the invitation is opened again
    Then no second session is opened
    And CoordinatorActivated is not published again

  # Not scenarios of the story: the contract of the endpoints when the state refuses.
  Scenario: An email that already has a user cannot be invited again
    Given a coordinator invited at an email, pending or active
    When an administrator invites that email again
    Then the answer is a conflict and nothing is created

  Scenario: An invitation of a university that cannot take it is refused
    Given a university that does not exist, a suspended one, or an email of another domain
    When an administrator invites a coordinator
    Then the answer is 404, 409 or 400, and nothing is created

  Scenario: An invitation of an account that is not a coordinator is forbidden
    Given an invitation link that points to a student account
    When the link is opened
    Then the request is forbidden and nobody is activated

  Scenario: Two confirmations of the same invitation at the same time activate once
    Given a pending coordinator invitation
    When the link is opened twice at the same time
    Then one answer is 200 and the other 409
    And the coordinator is activated once and CoordinatorActivated is published once

  Scenario: Two invitations to the same email at the same time end with a 201 and a 409
    Given an active university
    When two administrators invite the same email at the same time
    Then one answer is 201 and the other 409
    And exactly one coordinator and one invitation link exist
