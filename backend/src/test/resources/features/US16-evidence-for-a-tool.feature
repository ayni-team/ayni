# US16 - As a student who learned a tool by working and has work that shows it, I want to present
# that evidence to be reviewed, so that my real experience counts even when there is no exam for
# that skill.
#
# Each scenario below is covered by a test method of the same name in
# EvidenceValidationAcceptanceTest, which exercises it over HTTP against a real PostgreSQL and a
# real folder for the files.
#
# The card says that approving moves the skill to a "provisional" state. The model that was built
# has no such state: a global tool is enabled directly by the evidence a coordinator accepted (see
# state-offered-skill.puml), so scenario 2 is built that way and the pull request says so.
#
# The scenarios after the fourth are not on the card. They pin down what the card leaves out: who
# may review, what is refused, and what a tutor can do after the tool was approved.

Feature: Evidence for a global tool

  Background:
    Given a tutor of the university "UPC"
    And a coordinator of the university "UPC"
    And a global tool in the catalogue

  Scenario: Submitting the evidence
    When the tutor attaches a portfolio and sends the request
    Then the skill is pending and the submission is waiting for review
    And it appears in the queue of a coordinator of the university

  Scenario: Approved evidence
    Given the tutor submitted evidence
    When the coordinator approves it
    Then the skill is enabled by the reviewed evidence
    And the submission keeps who approved it and the files that were reviewed
    And the student and the search are told

  Scenario: Rejected evidence
    Given the tutor submitted evidence
    When the coordinator rejects it with a reason
    Then the skill is rejected and the tutor can read the reason
    And the tutor can submit new evidence, which puts the same skill back in the queue
    And the first submission stays rejected as history

  Scenario: Following the review
    Given the tutor submitted evidence
    When the tutor opens their skills
    Then the skill shows that it is in review and since when

  Scenario: A course cannot be validated by evidence
    Given a course in the catalogue
    When the tutor attaches a portfolio for the course
    Then the request is refused and nothing is stored

  Scenario: Evidence of a kind that is not accepted is refused
    When the tutor attaches a web page as evidence
    Then the request is refused, nothing is stored and no skill is created

  Scenario: A student cannot review evidence
    Given the tutor submitted evidence
    When the tutor tries to read the queue or decide
    Then both are forbidden

  Scenario: A submission already decided cannot be decided again
    Given the tutor submitted evidence
    And the coordinator approved it
    When the coordinator decides it again
    Then the request is refused as a conflict and nothing is announced twice

  Scenario: A rejection needs a reason
    Given the tutor submitted evidence
    When the coordinator rejects it without a reason
    Then the request is refused and the submission keeps waiting

  Scenario: A coordinator opens a file of a submission
    Given the tutor submitted evidence
    When the coordinator opens the file
    Then it comes as a download with the content that was uploaded

  Scenario: Another university does not see the queue
    Given the tutor submitted evidence
    When a coordinator of another university reads the queue
    Then the submission is not in it

  Scenario: A withdrawn tool with accepted evidence is offered again
    Given the tutor submitted evidence and the coordinator approved it
    And the tutor withdrew the skill
    When the tutor offers the tool again
    Then the same skill is enabled again without a new review
