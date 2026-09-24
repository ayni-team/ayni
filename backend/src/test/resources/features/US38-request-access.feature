# US38 - As a student of an affiliated university, I want to get in using only my institutional
# email, so that I do not create or remember another password and my identity is backed by my
# university.
#
# These are the scenarios of the story that requesting access covers (US38-T1). They are covered by
# RequestAccessUseCaseTest, AccessLinkTest and AccessControllerTest. Opening the link, the first
# sign in with the academic profile and an expired or used link arrive with the confirmation task.

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
