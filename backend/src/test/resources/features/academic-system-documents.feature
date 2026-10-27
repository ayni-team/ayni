# ADR 0006, amended 2026-10-23 - The academic system serves the university, its curriculum and its
# students. Each university's source of truth is one document of its academic system: who it is
# (name, logo, colours, email domains, time zone, minimum teaching grade, current term), what it
# teaches (its curriculum, by career and cycle) and who its students are. This is the first of two
# phases: the document is read from a folder; the second phase serves the same document over HTTP
# behind the same port, without touching the use cases. That second phase is here too: with
# ayni.identity.academic-directory-url set, each document is a static file under that address of
# another service (a GitHub Pages site in a deployment). It is not a story of the backlog: it serves
# TS10, US38, US40 and US51.
#
# Every scenario names the test that runs it, with the same display name:
# - reading a document, without Spring, in MockAcademicSystemAdapterTest;
# - reading it from an address, against a real HTTP server of the JDK, in HttpAcademicSystemTest;
# - the documents the repository delivers, in AcademicDocumentsCoherenceTest;
# - registering, activating and importing over HTTP with real session tokens, in
#   AcademicSystemDocumentsAcceptanceTest (identity);
# - synchronising the curriculum and two universities kept apart, over HTTP with real session
#   tokens, in AcademicCatalogSyncAcceptanceTest (skills);
# - the rule of a student no longer enrolled, without Spring, in ConfirmAccessUseCaseTest, and the
#   refresh of the record in ImportAcademicRecordUseCaseTest.
#
# The demo profile started from the two documents is a scenario of demo-profile.feature.

Feature: The academic system serves the university, its curriculum and its students

  Background:
    Given the academic system keeps one document per university, read each time it is asked

  Scenario: A valid document is read and the course names come from its curriculum
    Given the document of a university with its careers, its courses and a student
    When the student is looked up by their institutional email, whatever its case
    Then the profile has their code as written, their career, cycle, term and status
    And each approved course has the name the curriculum gives its code

  Scenario: An incoherent document is a mistake of the platform
    Given a document where an approved course is not in the curriculum of the student's career
    And a document where an email is repeated
    And a document where an email is not of a domain of the university
    When it is read
    Then the platform fails, naming the university and the rule broken and no student

  Scenario: Without its document a university has no academic system
    Given a university with no document in the folder
    When a student or the university is looked up
    Then the answer is that the academic system is not configured for that university

  Scenario: A local file adds students and wins on a repeated email
    Given the document of a university and a local file next to it with two students
    And one of them has an email the document already has
    When the students are looked up
    Then the new one is found, the repeated one is the local one, and the rest are the document's
    And a local student of another domain makes the document incoherent

  Scenario: A document published at an address is read as from the folder
    Given an address of another service that publishes the document of a university
    When a student or the university is looked up
    Then the answer is the one the same document gives from the folder

  Scenario: Without its document at the address a university has no academic system
    Given an address that answers 404 for the document of a university
    When the university is looked up
    Then the answer is that the academic system is not configured for that university

  Scenario: A document edited at the address is used once its copy is a minute old
    Given the document of a university was read from its address
    And it is edited there
    When a student is looked up within the minute, and again after it
    Then the first answer is the copy, without asking the address, and the second the edited document

  Scenario: While the address does not answer, the last copy read is used
    Given the document of a university was read from its address
    And the address stops answering
    When a student is looked up hours later
    Then the answer is the last copy read

  Scenario: With no copy, an address that does not answer is a mistake of the platform
    Given an address that takes longer than the timeout, or answers an error
    And no copy of the document was read before
    When a student or the university is looked up
    Then the request fails as unexpected, naming the university and no student

  Scenario: The local students are read from the folder, also with an address
    Given the document of a university at an address and a local file in the folder
    When the students are looked up
    Then the local student is found from the folder, and the local file is never asked of the address

  Scenario: The documents delivered with the repository are coherent
    Given the documents of UPC and USIL in backend/academic-directory
    When they are read with the logic of the adapter
    Then both are coherent
    And every student approved only courses behind their cycle

  Scenario: The administrator registers a university from its academic document
    Given the document of a university that is not registered
    When the platform administrator registers it from its document with its initial credits
    Then it is registered with the name, logo, colours, domains, time zone and grade of the document
    And its baseline credit policy has the credits the administrator gave
    And UniversityRegistered is published once
    And no course is loaded yet

  Scenario: A code without an academic document is not found
    When the platform administrator registers a code the academic system has no document for
    Then the answer is 404 with the shape of every error
    And nothing is registered

  Scenario: A university registered twice from its document is a conflict
    Given a university registered from its document
    When the administrator registers it again
    Then the answer is 409 and nothing more is published

  Scenario: Only the platform administrator registers a university from its document
    When a student or a coordinator registers a university from its document
    Then the answer is 403
    And without a session the answer is 401

  Scenario: A student who is no longer enrolled is not activated and the link stays unused
    Given a university whose document has a graduate and a student who left
    When each of them opens their activation link
    Then the answer is 400, the student is not currently enrolled at the university
    And no account is created and the link stays unused

  Scenario: Importing the record again updates the cycle
    Given a student activated in cycle 3 of the term 2026-2
    When the university moves them to cycle 4 of the term 2027-1 and they import their record again
    Then their profile shows cycle 4 and the term 2027-1
    And their approved courses are the new ones

  Scenario: A coordinator synchronises the curriculum of the university
    Given a university whose document has three courses
    When its coordinator synchronises the curriculum
    Then the three courses are in the catalogue with the names of the document
    And the minimum teaching grade is still the one of the registration

  Scenario: Synchronising twice in a row does not duplicate a course
    Given the coordinator synchronised the curriculum
    When they synchronise it again
    Then nothing is created and every course is unchanged

  Scenario: Synchronising never removes a course
    Given the coordinator added a course by hand, described another and retired a third
    When they synchronise the curriculum
    Then the course added by hand is still there and the description is kept
    And the retired course is back, as loading the curriculum brings it back

  Scenario: A student cannot synchronise the curriculum
    When a student synchronises the curriculum
    Then the answer is 403 and no course is loaded

  Scenario: Each university sees only its own curriculum and its own brand
    Given UPC and USIL registered from their documents, each with its curriculum synchronised
    When a student of each searches the catalogue and reads their university
    Then the student of UPC finds only the courses of UPC and the student of USIL only those of USIL
    And each sees the name, logo and colours of their own university
