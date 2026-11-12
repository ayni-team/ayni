# W27 - A refusal for lack of credits says how many are missing in a field of its own, and names
# itself, so the web application tells it apart from the other conflicts of the same request
# without reading the message or working it out from a balance it read earlier.
#
# Not a story of the backlog: it serves US03 (booking), US31 (campus benefits and donations) and
# FE-11 and FE-33. The wallet's refusals run in MissingCreditsAcceptanceTest and the booking's in
# MissingCreditsOfABookingTest, over HTTP against a real PostgreSQL, each with the scenario's name.

Feature: The refusal for lack of credits says how many are missing

  Scenario: A redemption without enough earned credits says how many are missing
    Given a student with 1 earned credit and 4 purchased ones
    And a campus benefit that costs 3 credits, whose warning the student confirmed
    When the student redeems it
    Then the answer is a conflict with code INSUFFICIENT_EARNED_CREDITS and 2 missing credits
    And nothing is spent

  Scenario: A donation without enough earned credits says how many are missing
    Given a student with 2 earned credits who confirmed the warning of a donation of 5
    When the student donates
    Then the answer is a conflict with code INSUFFICIENT_EARNED_CREDITS and 3 missing credits

  Scenario: An expired confirmation is told apart from missing credits
    Given a student with 1 earned credit who asked for the warning of a benefit that costs 3
    When the student confirms after the warning expired
    Then the answer is a conflict with code CONFIRMATION_EXPIRED and no missing credits

  Scenario: A booking without enough credits says how many are missing
    Given a student with 1 credit who holds three hours
    When the student confirms the booking
    Then the answer is a conflict with code INSUFFICIENT_CREDITS and 2 missing credits

  Scenario: Other errors carry neither a code nor missing credits
    When a request of the wallet is refused for another reason
    Then the answer has the five fields of every error and nothing else
