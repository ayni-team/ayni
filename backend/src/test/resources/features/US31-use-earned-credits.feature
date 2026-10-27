# US31 runs over HTTP and PostgreSQL in WalletRedemptionAcceptanceTest: every scenario below has a
# test with exactly its name.

Feature: Use earned credits for a campus benefit or incoming-student donation

  Scenario: The student redeems earned credits for a campus benefit
    Given the university coordinator has configured a campus benefit
    And the student has enough earned credits for it
    When the student confirms the redemption
    Then only earned credits are debited
    And the student receives a redemption receipt

  Scenario: The student donates earned credits to the incoming-student pool
    Given the student has earned credits to donate
    When the student confirms a donation
    Then only earned credits are debited
    And the donation is added to the university pool
    And the donation appears in the student's wallet history

  Scenario: The student must review the irreversible-operation warning
    Given the student is about to redeem a benefit or make a donation
    When the student requests confirmation
    Then the system warns that the operation cannot be reversed
    And no credits move until the returned confirmation is submitted

  Scenario: Purchased credits cannot cover a benefit redemption
    Given the student has fewer earned credits than the benefit costs
    And the student also has purchased credits
    When the student confirms the redemption
    Then the redemption is refused with the missing earned-credit amount
    And the wallet and ledger remain unchanged

  Scenario: A coordinator lists every benefit of the university, the deactivated ones too
    Given the university coordinator has configured two campus benefits
    And one of them was deactivated
    When the coordinator lists the benefits
    Then both appear, the deactivated one marked as inactive
    And the students of the university see only the active one

  Scenario: A student or a restricted coordinator cannot manage the campus benefits
    Given a student, and a coordinator who is restricted
    When either of them tries to list or create a campus benefit
    Then the answer is forbidden and nothing is created
