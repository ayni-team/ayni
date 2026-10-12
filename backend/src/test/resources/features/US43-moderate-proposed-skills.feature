# US43 - As a catalogue moderator, I want to review the skills students propose and decide whether
# they are added, joined to an existing one or discarded, so that the catalogue grows with real
# demand without filling with duplicates.
#
# Each scenario below is covered by a test method of the same name in
# SkillProposalModerationAcceptanceTest, which exercises it over HTTP against a real PostgreSQL.
#
# The card does not say who the moderator is. It is a coordinator of the student's university: a
# platform administrator has no university and must not read the names of students. Approving adds
# a global item, which every university sees; the pull request asks the lead to confirm that a
# coordinator may do it.
#
# "Associated to the existing skill" is built as the proposal keeping the item it was joined to,
# which the student reads in their proposals. No offered skill is created, because a pending skill
# with no evidence would break the flow of US16.
#
# The scenarios after the fifth are not on the card. They pin down what the card leaves out: who may
# decide and what stops a decision. Two moderators on the same proposal at once is a race, and it
# is covered by ResolveProposalConcurrencyTest.

Feature: Moderating the proposed skills

  Background:
    Given a student and a coordinator of the university "UPC"
    And a category in the catalogue

  Scenario: Queue of pending proposals
    Given two proposals wait for a decision
    When the coordinator opens the moderation
    Then the proposals are listed oldest first with their name, category, proposer and date

  Scenario: Approved proposal
    Given a proposal waits for a decision
    When the coordinator approves it
    Then the skill is added to the catalogue as a global item
    And a student of another university finds it in their catalogue

  Scenario: Proposal joined to an existing skill
    Given the catalogue has a tool called "Node.js"
    And a proposal for "NodeJS Advanced" waits for a decision
    When the coordinator reads it and sees "Node.js" among the similar skills
    And joins it to "Node.js"
    Then no new skill is created
    And the student sees the proposal joined to "Node.js"

  Scenario: Rejected proposal
    Given a proposal waits for a decision
    When the coordinator rejects it with a reason
    Then the student reads the decision and the reason

  Scenario: Traceability of the decision
    Given the coordinator resolved a proposal
    When the history is consulted
    Then it says who resolved it, when and how, with the reason and the skill it ended up in

  Scenario: A rejection needs a reason
    Given a proposal waits for a decision
    When the coordinator rejects it without a reason
    Then the request is refused and the proposal keeps waiting

  Scenario: A proposal already resolved cannot be resolved again
    Given the coordinator approved a proposal
    When the coordinator decides it again
    Then the request is refused as a conflict and the catalogue has the skill once

  Scenario: A name the catalogue already has cannot be approved
    Given the catalogue has a tool called like the proposal
    When the coordinator approves it
    Then the request is refused as a conflict and the proposal keeps waiting

  Scenario: A student cannot moderate
    Given a proposal waits for a decision
    When the student tries to read the moderation or decide
    Then both are forbidden

  Scenario: Another university does not see the proposals
    Given a proposal waits for a decision
    When a coordinator of another university opens the moderation
    Then the proposal is not in it
    And they cannot decide it
