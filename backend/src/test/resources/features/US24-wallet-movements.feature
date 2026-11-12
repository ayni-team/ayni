# US24 - As a student I want to see every movement of my credits, so that I understand where my
# balance went.
#
# Every scenario has a test with its name as display name in WalletMovementsAcceptanceTest, over
# HTTP against a real PostgreSQL: GET /api/v1/wallet/movements. That expired credits stay in the
# history is a scenario of US23 (WalletBalanceAcceptanceTest), and each story that moves credits
# checks its own entry: booking (BookABlockAcceptanceTest), purchases (PaymentsAcceptanceTest) and
# the campus benefits and donations (WalletRedemptionAcceptanceTest).

Feature: The history of my credit movements

  Background:
    Given a student of a university with 5 SEED credits granted
    And a booking that charged 2 of them

  Scenario: Every movement is listed, newest first
    When the student asks for their movements
    Then the charge comes first and the grant after it
    And each movement says its direction, amount, reason and credit type
    And the charge points at the booking it paid for

  Scenario: The history can be narrowed to one kind of movement
    When the student asks only for the movements of reason GRANT
    Then only the grant is listed

  Scenario: The history can be narrowed to a period
    When the student asks for the movements of a period that ended before the grant
    Then nothing is listed
    When the student asks for a period that includes both movements
    Then both are listed

  Scenario: A long history comes in pages
    Given the student received three more grants
    When the student asks for pages of two movements
    Then the first page has two movements and says there are five in three pages
    And the last page has the oldest movement

  Scenario: Only the student's own movements are listed
    Given another student of the same university and a student of another university have movements
    When the student asks for their movements
    Then none of theirs is listed

  Scenario: A page outside the limits is refused
    When the student asks for pages of more than one hundred movements
    Then the request is refused as a bad request, in the common error shape
