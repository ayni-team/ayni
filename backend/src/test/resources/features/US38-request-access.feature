# US38 - As a student of an affiliated university, I want to get in using only my institutional
# email, so that I do not create or remember another password and my identity is backed by my
# university.
#
# Requesting access (US38-T1) is covered by RequestAccessUseCaseTest, AccessLinkTest and
# AccessControllerTest. Confirming it (US38-T2) runs over HTTP against a real PostgreSQL, one test
# per scenario with the same name, in ConfirmAccessAcceptanceTest, with the rules of the use case
# in ConfirmAccessUseCaseTest.
#
# Delivering the link by email (US38-T3) runs in AccessLinkEmailAcceptanceTest, against a real
# PostgreSQL and an SMTP server inside the test, with the content and the order of recording in
# DeliverAccessLinkUseCaseTest.
#
# A scenario nobody runs yet is tagged @pending and says why.

Feature: Requesting access with an institutional email

  Background:
    Given the university "UPC" is affiliated with the email domain "upc.edu.pe"

  Scenario: Access request from a new student
    Given nobody with the email "u202400001@upc.edu.pe" has an account
    When access is requested for "u202400001@upc.edu.pe"
    Then an activation link is sent to that email
    And the link can be used once and expires in minutes
    And only the hash of its token is stored

  Scenario: Access request from a student who already has an account
    Given a student of "UPC" with the email "u202400001@upc.edu.pe"
    When access is requested for "u202400001@upc.edu.pe"
    Then a sign in link is sent to that email

  Scenario: Domain not affiliated
    Given no affiliated university uses the domain "gmail.com"
    When access is requested for "someone@gmail.com"
    Then the request is refused saying the institution is not affiliated with Ayni
    And no account is created

  Scenario: Signing in with a valid link
    Given a student of "UPC" with the email "u202400001@upc.edu.pe"
    And a sign in link was sent to that email
    When the link is opened
    Then a session is opened for that student in "UPC", the university written on the link
    And the request does not name the university
    And only the hash of the session token is stored

  Scenario: A link that was already used
    Given the student opened a sign in link
    When the same link is opened again
    Then it is refused saying it has expired or was already used
    And no other session is opened

  Scenario: An expired link
    Given a sign in link was sent ten minutes ago
    When the link is opened
    Then it is refused saying it has expired or was already used

  Scenario: A token nobody issued
    When a link with an invented token is opened
    Then it is refused saying the link is not valid

  Scenario: Two confirmations of the same link at the same time open one session
    Given a sign in link was sent to the student
    When the link is opened twice at the same moment
    Then exactly one of them opens a session
    And the other is refused

  Scenario: An activation link of a new student is refused until activation exists
    Given nobody with the email of a new student has an account
    And an activation link was sent to that email
    When the link is opened
    Then it is refused saying creating accounts is not available yet
    And no account is created

  Scenario: The sign in link arrives by email and opens a session
    Given a student of "UPC" with the email "u202400001@upc.edu.pe"
    When access is requested for "u202400001@upc.edu.pe"
    Then an email arrives at that address with the link, saying it works once and expires in 10 minutes
    And opening the link from the email opens a session for that student
    And the notice is recorded as sent, without the link

  Scenario: A new student receives an activation email
    Given nobody with the email of a new student has an account
    When access is requested for that email
    Then an activation email arrives at that address
    And the notice is recorded with no recipient account

  Scenario: An email that cannot be delivered is recorded as failed
    Given the mail server cannot be reached
    When access is requested for "u202400001@upc.edu.pe"
    Then the request is still accepted
    And the notice is recorded as failed, with the reason

  @pending
  Scenario: First access of a new student with their academic profile
    Given an activation link was sent to a new student
    When the link is opened
    Then the account is created with the academic profile the university reports
    And StudentActivated is published
    # Not in this task: creating accounts from the academic system is a decision of its own.
