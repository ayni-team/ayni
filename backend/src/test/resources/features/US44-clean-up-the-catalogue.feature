# US44 - As a catalogue moderator, I want to join duplicate skills and retire the ones nobody uses
# any more, so that students find what they look for without choosing between three versions of the
# same thing.
#
# Each scenario below is covered by a test method of the same name in
# CatalogCleanUpAcceptanceTest, which exercises it over HTTP against a real PostgreSQL and a real
# folder for the files.
#
# The moderator is a coordinator, as in US43, and moderates the catalogue of their own university:
# its courses. A global tool is shared by every university, so retiring or joining one is left to
# the ADMIN role, which does not exist yet (TS03); a coordinator who tries is refused. The numbers
# the moderator reads are those of their own university, for a global tool too. They are counts and
# never people.
#
# "Sessions taught" counts what skills recorded from the announcements of completed sessions since it
# started doing so; the sessions completed before that are not included.
#
# Joining moves what the skills module holds. The ratings and the bookings of the past stay with the
# retired item, so the reputation of a tutor in the item that stays starts from zero. Fixing that
# takes an event that reputation listens to, and the pull request leaves it to the lead.
#
# The scenarios after the fourth are not on the card. They pin down what the card leaves out: who
# may do it, which items can be joined, and what happens to evidence that was waiting for a review.
#
# Scenarios tagged @pending only make sense with a global tool, which a coordinator cannot retire or
# join, so they wait for the ADMIN role (TS03). They stay here, with their tests disabled, and the
# pull request lists them under "Still pending".

Feature: Cleaning up the catalogue

  Background:
    Given a coordinator and tutors of the university "UPC"
    And two courses of the university in the catalogue

  Scenario: Joining duplicate skills
    Given a tutor offers the duplicate, another tutor offers both, and a student wants to learn it
    And a booking was confirmed on the duplicate
    When the coordinator joins the duplicate to the other course
    Then the first tutor now offers the course that stays, with the same status
    And the tutor who offered both keeps one offer and the other is withdrawn
    And the search is told the old offer ended and the new one began
    And the duplicate is no longer in the catalogue
    And the booking stands

  Scenario: Retiring a skill
    Given a tutor offers a course
    When the coordinator retires it
    Then it can no longer be offered and it is no longer in the catalogue or among the similar skills
    And the tutor sees their offer withdrawn

  Scenario: Offers in force when retiring a skill
    Given three tutors offer a course and a booking was confirmed on it
    When the coordinator reads how many tutors are affected
    And tries to retire it confirming a different number
    Then nothing is retired and the real number is said
    When the coordinator confirms the right number
    Then the three offers are withdrawn and announced, and the booking stands

  Scenario: Use of the catalogue as a criterion
    Given a course offered by two tutors with four sessions taught on it, and a course nobody uses
    When the coordinator reads the use of each
    Then the first shows two tutors and four sessions, and the second shows zeros

  Scenario: A student cannot clean up the catalogue
    Given a course offered by a tutor
    When a student tries to read its use, retire it or join it
    Then all three are forbidden and nothing changes

  Scenario: A coordinator cannot retire or join a global tool
    Given a global tool offered by a tutor of the university and by one of another university
    When the coordinator tries to retire it, or to join it to a course or to another global tool
    Then every attempt is forbidden, nothing changes and no event is published
    And the coordinator can still read its use, counted in the coordinator's university only

  # Waits for the ADMIN role (TS03): only an administrator can join a global tool.
  @pending
  Scenario: A course and a tool cannot be joined
    Given a course of the university and a tool
    When the administrator joins one to the other
    Then both attempts are refused and nothing changes

  # Waits for the ADMIN role (TS03): only an administrator can retire a global tool, and evidence is
  # only submitted for a global tool.
  @pending
  Scenario: Evidence waiting for a retired tool cannot be approved
    Given a tutor submitted evidence for a tool
    When the administrator retires the tool
    Then the evidence can no longer be approved
    And it can still be rejected, which the tutor sees
