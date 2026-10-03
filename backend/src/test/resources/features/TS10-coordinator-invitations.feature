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