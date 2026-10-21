# US27 and US28 - A university decides how many hours a student must have taught before recognition,
# so that its students can see how far they are (US27) and ask for it (US28).
#
# Not a scenario of either card: until a coordinator sets the rule, recognition is closed. Nobody
# can see a requirement or submit a request, and only the demonstration data of the dev profile
# wrote a rule.
#
# Each scenario below is covered by a test method of the same name in
# SetRecognitionRuleAcceptanceTest, over HTTP against a real PostgreSQL with real session tokens,
# except the one about two coordinators at once, which runs in SetRecognitionRuleConcurrencyTest.
# The sessions a student taught are stubbed, since sessions is another module.
#
# A rule is never edited: setting one closes the rule in force and adds the next. A university has one
# rule in force at a time. The day a rule starts is a day of the university, in its own time zone.

Feature: Recognition rule

  Background:
    Given an active coordinator of a university in Lima
    And a student of the same university

  Scenario: Setting the rule opens recognition
    Given the university has no recognition rule
    When the coordinator sets the rule to 20 hours
    Then the answer is the rule: 20 hours, from today
    And the coordinator reads the same rule
    And the student sees that 20 hours are required

  Scenario: Replacing the rule keeps the previous one
    Given the university asks for 20 hours
    When the coordinator sets the rule to 30 hours
    Then the rule in force asks for 30 hours
    And the rule of 20 hours is kept, replaced, and is no longer in force

  Scenario: A rule that starts later leaves the current one running
    Given the university asks for 20 hours
    When the coordinator sets the rule to 30 hours from next week
    Then the rule in force today still asks for 20 hours
    And the new rule is kept, ready to start on that day

  Scenario: Hours that are not at least one
    Given the university asks for 20 hours
    When the coordinator sets the rule to 0 hours, to -5 hours, to a word, or sends no hours at all
    Then each is refused as a malformed request
    And the rule in force still asks for 20 hours

  Scenario: A day that already passed
    Given the university asks for 20 hours
    When the coordinator sets the rule to start yesterday
    Then it is refused as a malformed request
    And the rule in force still asks for 20 hours

  Scenario: A student cannot set the rule
    When the student sets the rule to 20 hours
    Then it is refused as forbidden
    And the university still has no rule

  Scenario: A restricted coordinator cannot set the rule
    Given a coordinator of the university who is restricted
    When they set the rule to 20 hours
    Then it is refused as forbidden
    And the university still has no rule

  Scenario: The rule of another university is not seen
    Given another university asks for 40 hours
    When the coordinator reads the rule of their university
    Then it is not found
    When the coordinator sets the rule to 20 hours
    Then the other university still asks for 40 hours

  Scenario: Requests already submitted keep their figures
    Given the university asks for 20 hours and the student submitted a request
    When the coordinator sets the rule to 30 hours
    Then the request of the student has the same hours, count and state as before
    And the student reads it exactly as before

  Scenario: Two coordinators set the rule at the same time
    Given the university asks for 20 hours
    When two coordinators set a different rule at the same moment
    Then both are answered and the university has exactly one rule in force
    And the rules they set are kept, all but the one in force replaced
