# Deployment - As the team, I want an image and a profile to run Ayni outside a developer's
# machine, so that an empty installation can be configured from its environment alone and bring in
# its first university.
#
# No story or Trello card: this is the packaging the deployment builds on. Each scenario below with
# the same name runs in:
#
# - ProductionProfileTest (pe.ayni.config): the prod profile, over a PostgreSQL of its own that
#   starts empty, configured only with the variables the README documents.
# - PlatformAdminBootstrapTest (pe.ayni.identity): the first platform administrator.
# - CorsOriginsTest (pe.ayni.config) and WhiteboardOriginTest (pe.ayni.sessions): the origins.
# - DevProfileTest (pe.ayni.config): the dev profile keeps its demo catalogue.
#
# That the image builds and answers its health check against a PostgreSQL is checked by hand with
# docker build -f backend/Dockerfile.prod backend, as the pull request says.

Feature: Packaging Ayni for production

  Scenario: The prod profile starts with only the documented variables
    Given an empty PostgreSQL
    And only the variables the Production section of the README lists
    When the backend starts with the prod profile
    Then readiness is UP and depends on the database only
    And the mail server is not part of the health
    And Swagger UI and the OpenAPI description are not served
    And the information endpoint says which version runs

  Scenario: A missing required variable stops the startup and names it
    Given every required variable but one
    When the backend starts with the prod profile
    Then it refuses to start before connecting to anything
    And the message names the variable that is missing

  Scenario: An empty installation creates its first university
    Given an empty installation whose first administrator is named in its environment
    When the administrator asks for an access link and opens the one in the email
    Then they are signed in as the platform administrator
    And they register the first university

  Scenario: The global categories exist in every installation
    Given an empty installation
    Then the categories Technology, Languages and Design exist with their fixed identifiers

  Scenario: The first administrator is created once
    Given an address in ayni.bootstrap.admin-email
    When the backend starts
    Then an active platform administrator with that address and the configured name exists

  Scenario: A second start does not duplicate the administrator
    Given the administrator of ayni.bootstrap.admin-email exists
    When the backend starts again
    Then there is still one administrator with that address

  Scenario: Without the address nothing is created
    Given ayni.bootstrap.admin-email is missing or blank
    When the backend starts
    Then no platform administrator is created

  Scenario: A malformed address stops the startup
    Given an ayni.bootstrap.admin-email that is not an address
    When the backend starts
    Then the startup fails
    And no administrator is created

  Scenario: Another origin gets no CORS header
    Given the allowed origins are configured
    When a browser on another origin asks to call the API
    Then the answer carries no CORS header and is refused
    And an allowed origin is answered with its CORS header

  Scenario: An allowed origin reads the name of a downloaded file
    Given the allowed origins are configured
    When the web application, served from an allowed origin, downloads an attachment
    Then the answer lets the browser read its Content-Disposition

  Scenario: Another origin cannot open the live channel of a whiteboard
    Given the allowed origins are configured and a session in progress
    When a participant opens the live channel from another origin
    Then the handshake is refused
    And from an allowed origin it opens

  Scenario: The dev profile still fills its demo catalogue
    Given the global categories already exist from the migration
    When the backend starts with the dev profile
    Then the demo global tools and the courses of the demo university exist
    And the category of university courses was created
