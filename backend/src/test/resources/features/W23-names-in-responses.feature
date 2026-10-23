# W23 - As the web client, I want the responses that name a course or a person to carry the name
# and not only the identifier, so that the screens of a tutoring do not have to resolve ids one by
# one.
#
# Not a story of the backlog: the screens of "My tutorings", the room, the close and the review
# show the course and the other person, and until now the answers only had ids. Each scenario runs
# over HTTP against a real PostgreSQL, with the same name: the sessions ones in
# SessionNamesAcceptanceTest, the catalogue one in CatalogItemAcceptanceTest and the booking ones in
# BookingNamesAcceptanceTest.

Feature: Names in the responses

  Scenario: A student reading a session sees the tutor and the course by name
    Given a session between a student and a tutor, about a course of the university
    When the student reads it
    Then the course comes with its name and code, and the other person is the tutor, by name

  Scenario: A tutor reading a session sees the student by name
    Given a session between a student and a tutor
    When the tutor reads it
    Then the other person is the student, by name

  Scenario: A global tool has no course code
    Given a session about a tool that is not a course of any university
    When a participant reads it
    Then the course comes with its name and no course code

  Scenario: The room and the review name the same course and person
    Given a session between a student and a tutor
    When the student joins the room and later reviews the session
    Then both answers name the course and the tutor

  Scenario: A person or a course the other module no longer knows is shown without a name
    Given a session whose tutor and course are unknown to identity and skills
    When the student reads it
    Then their identifiers are there and their names are null

  Scenario: A catalogue item is read by its identifier
    Given a course of the university, a global tool and a course that was retired
    When a student asks for each of them
    Then each answers as the catalogue lists it, the retired one too

  Scenario: An item of another university is not found
    Given a course of another university
    When a student asks for it
    Then it is not found, as if it did not exist

  Scenario: Held hours name the tutor
    Given a student who holds two hours of a tutor
    When the hold is answered
    Then it carries the name of the tutor

  Scenario: The lists of tutorings name every person and course with one read per module
    Given a student with several tutorings and a tutor with several students
    When each of them reads their list
    Then every line names the other person and the course, and skills was asked once for the list
