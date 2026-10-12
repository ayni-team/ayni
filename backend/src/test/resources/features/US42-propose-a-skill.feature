# US42 - As a student who masters a tool that is not in the catalogue, I want to propose it so that it
# is added, so that I can offer it as tutoring instead of having no way to record it.
#
# Each scenario below is covered by a test method of the same name in
# SkillProposalAcceptanceTest, which exercises it over HTTP against a real PostgreSQL.
#
# A moderator's decision is US43. Scenarios 3 and 4 read what it leaves, and their tests make the
# decision through its endpoint (see US43-moderate-proposed-skills.feature).
#
# The scenarios after the fifth are not on the card. They pin down what the card leaves out: which
# proposals the student cannot make, and who can read them.

Feature: Proposing a skill that is not in the catalogue

  Background:
    Given a student of the university "UPC"
    And a category in the catalogue

  Scenario: Proposal sent
    When the student proposes a tool with its name, category and a short description
    Then it is recorded as a proposal waiting for a moderator

  Scenario: Skill that already exists
    Given the catalogue has a tool called "Node.js"
    When the student proposes "NodeJS Advanced"
    Then the answer lists the similar skills and nothing is recorded
    When the student sends it again confirming that theirs is a different one
    Then it is recorded as a proposal waiting for a moderator

  Scenario: Following my proposal
    Given the student sent a proposal
    When the student opens their proposals
    Then the proposal shows its status, its category and when it was sent
    When a moderator rejects it with a reason
    Then the student reads the decision and the reason

  Scenario: Approved proposal
    Given a moderator approved a proposal
    When the student opens the catalogue
    Then the skill is available and the student can start its accreditation

  Scenario: No free text in the offer
    Given the student wants to offer a skill
    When they send a name instead of a catalogue item
    Then the request is refused and no skill is created
    When they send a catalogue item that does not exist
    Then it is not found and no skill is created

  Scenario: A name the catalogue already has is refused
    Given the catalogue has a tool called "Node.js"
    When the student proposes "NodeJS", even confirming
    Then the request is refused as a conflict and nothing is recorded

  Scenario: A proposal already waiting is refused
    Given the student sent a proposal
    When the student proposes the same name again, even confirming
    Then the request is refused as a conflict and there is still one proposal

  Scenario: A rejected proposal can be proposed again
    Given the student sent a proposal and a moderator rejected it
    When the student proposes the same name again
    Then it is recorded as a new proposal and the first one stays rejected

  Scenario: A name or a category that does not fit is refused
    When the student proposes a name of two letters
    Then the request is refused as malformed
    When the student proposes a category that does not exist
    Then it is not found and nothing is recorded

  Scenario: Proposals are private to their author
    Given the student sent a proposal
    When another student of the same university opens their proposals
    Then the proposal is not in the list
    When a student of another university opens theirs
    Then the proposal is not in the list
