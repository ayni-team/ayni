# campus benefit redemption -> aConfirmedCampusBenefitRedemptionSpendsEarnedCreditsAndReturnsAReceipt
# incoming-student donation -> aConfirmedDonationReducesEarnedCreditsAndIncreasesTheIncomingPool
# irreversible confirmation -> aRedemptionAndDonationRequireAWarningRoundTripBeforeConfirmation
# insufficient earned credits -> aBenefitRedemptionRefusesWhenEarnedCreditsAreInsufficient

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
