# TS03 - Access control of the API: every request proves who it is with its session token, and the
# route decides which kind of person may call it (ADR 0007).
#
# Each scenario runs over HTTP against a real PostgreSQL, with the same name, in
# AccessControlAcceptanceTest, which turns off the test filter that keeps the old identity headers
# working for the other modules' tests: these scenarios go through the real filter, with real
# tokens. The rules of the route groups are in RouteAccessTest and the expiry of a session in
# UserSessionTest. What Swagger UI is told is in OpenApiSessionTokenTest.

Feature: Access control of the API

  Scenario: A request without credentials is answered 401 and executes nothing
    Given a student who is not signed in
    When they try to change their profile without a session token, or with one nobody issued
    Then the answer is 401
    And the profile is as it was

  Scenario: A role that is not enough is answered 403 and executes nothing
    Given a signed in student
    When they call a route of the coordinators and a route of the platform administrator
    Then both answers are 403
    And nothing those routes would have changed has changed

  Scenario: An expired token is refused without reading any data
    Given a student whose session expired
    When they ask for their profile with that token
    Then the answer is 401
    And the use case that reads the profile was never called

  Scenario: A revoked token is refused without reading any data
    Given a student who signed out
    When they ask for their profile with the token they had
    Then the answer is 401
    And the use case that reads the profile was never called

  # Ayni has no certificates yet: recognition records the university's decision and issues
  # nothing. When a verification route exists it joins the public group of RouteAccess.
  @pending
  Scenario: A deliberately public resource answers without an account
    Given a certificate issued by a university
    When somebody without an account verifies it
    Then the answer says whether it is genuine

  # Not scenarios of the story: what the decision of ADR 0007 means for the routes that exist.
  Scenario: The university and the person of a request are the ones of its session
    Given a student who signed in with their link
    When they ask for their profile naming another university and another person in headers
    Then the answer is their own profile in their own university

  Scenario: Signing out closes the session
    Given a signed in student
    When they sign out
    Then the session is marked revoked
    And the token they had is answered 401

  Scenario: A platform administrator signs in by link and has no university
    Given an active platform administrator
    When they ask for a link and open it
    Then their session has no university and the role of an administrator
    And they reach the routes of the administrator and none of the students

  Scenario: A coordinator reaches the coordinator routes and not the students ones
    Given a signed in coordinator
    When they call a route of the coordinators and a route of the students
    Then the first one is not refused for its role and the second is answered 403

  Scenario: The public routes answer without an account
    When somebody without a session asks for a link, reads the API description and the health
    Then none of them is answered 401
