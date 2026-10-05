# US51 - As the person in charge of the platform at my university, I want to load our courses and
# define the minimum grade to teach them, so that the tutoring matches our curriculum and the
# criterion for enabling a tutor is ours.
#
# Each scenario below is covered by a test method of the same name in
# AcademicCatalogAcceptanceTest, which exercises it over HTTP against a real PostgreSQL. Who a person
# is, and the courses and grades the academic system reports for them, are stubbed, since identity is
# another module; everything else is the real application.
#
# The person in charge is a coordinator of the university, as in US16, US43 and US44.
#
# Loading adds and updates the courses of the list and never removes one that is missing from it:
# taking a course out is retiring it, which has consequences for the tutors who offer it.
#
# The minimum grade is on the scale of 0 to 20. Until a coordinator sets one it is the grade the
# university was registered with. A change applies to the offers made from then on; the ones already
# enabled each keep the grade they were enabled with.
#
# The scenarios after the fourth are not on the card. They pin down what the card leaves out: who
# may do it and what happens to a list that cannot be loaded.

Feature: Configuring the academic catalogue

  Background:
    Given a coordinator and students of the university "UPC"
    And the university asked for 13 as the minimum grade when it was registered

  Scenario: Available courses
    Given the coordinator loads the courses of the curriculum
    When a student searches for them and offers one they passed
    Then the student finds both courses
    And the offer is enabled

  Scenario: Minimum grade to teach
    Given the coordinator sets 15 as the minimum grade
    When a student with 14 and another with 16 offer the same course
    Then the offer with 14 is refused and nothing is created
    And the offer with 16 is enabled automatically

  Scenario: Change of the minimum grade
    Given a student with 14 offered a course and was enabled
    When the coordinator raises the minimum grade to 16
    Then the student keeps the course enabled
    And another student with 14 who offers it afterwards is refused

  Scenario: Withdrawal of a course
    Given a tutor offers a course and a booking was confirmed on it
    When the coordinator reads how many tutors are affected and retires the course confirming it
    Then the course can no longer be offered and the search no longer finds it
    And the tutor sees the offer withdrawn
    And the booking stands

  Scenario: A student cannot configure the academic catalogue
    When a student tries to load courses, set the grade, read it or retire a course
    Then all four are forbidden and nothing changes

  Scenario: A list that cannot be loaded saves nothing
    When the coordinator loads a list with a course without a name after a valid one
    Then it is refused and the valid course is not saved either
