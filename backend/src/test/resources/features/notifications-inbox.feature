# Notices inbox - the people who use Ayni, students and coordinators, see the notices the application
# keeps for them, mark them as read and know how many are left.
#
# This is not a story of the backlog, so it carries no US number: it is the endpoint
# GET /api/v1/notifications that every notice of the other stories ends in (US06, US36, and the
# ones still to come), and the two ways of marking a notice as read. The notices themselves are
# written by the listeners of each story; here they are the ones already in the table.
#
# Only the notices of the application are listed. The access link and the presence code travel by
# email and carry a secret, so they are not notices to show. A notice is listed even when its email
# did not arrive: the list belongs to the application, not to the delivery.
#
# Every scenario names the test that runs it, against a real PostgreSQL and with real session
# tokens, in NotificationsInboxAcceptanceTest, with the same name. The rule of reading a notice
# once is covered without Spring by NotificationTest, and the kinds that are listed by
# NotificationKindTest.

Feature: Notices inbox

  Background:
    Given a university with two students, Ana and Bruno, and a coordinator, Carlos
    And all of them signed in

  Scenario: A student sees only their own notices
    Given Ana has two notices and Bruno has one
    When Ana lists her notices
    Then she sees her two notices with their kind, payload, creation date and reading date
    And neither the address nor the delivery failure is part of the answer

  Scenario: A coordinator sees their own notices
    Given Carlos has one notice and Ana has two
    When Carlos lists his notices
    Then he sees his one notice

  Scenario: Another student of the same university does not see them
    Given Ana has two notices
    When Bruno lists his notices
    Then he sees none and has none unread

  Scenario: Another university sees nothing of them
    Given Ana has two notices
    And Dora is a student of another university
    When Dora lists her notices
    Then she sees none and has none unread

  Scenario: Emailed links and codes are not listed
    Given Ana has an access link, a presence code and one notice of the application
    When Ana lists her notices
    Then she sees only the notice of the application
    And the unread count does not include the link or the code

  Scenario: A notice is listed even when its email failed
    Given Ana has a notice whose email could not be delivered
    When Ana lists her notices
    Then she sees it, with no reason for the failure

  Scenario: Notices come newest first, a page at a time
    Given Ana has twenty-five notices
    When she lists her notices without asking for a size
    Then she gets the newest twenty, the total is twenty-five and the size is twenty
    And the second page has the other five, older than those of the first

  Scenario: A page or a size outside the limits is refused
    When Ana asks for a size of 101, a size of 0 or a page below 0
    Then each answer is 400 and has the shape of every error

  Scenario: Only the unread ones on request
    Given Ana has three notices and has read one
    When she lists her unread notices
    Then she sees the two she has not read
    And the unread count is two

  Scenario: Reading a notice lowers the unread count
    Given Ana has two unread notices
    When she marks one as read
    Then the answer is 204
    And the notice shows when it was read
    And the unread count is one

  Scenario: Reading a notice again keeps its first date
    Given Ana read a notice a while ago
    When she marks it as read again
    Then the answer is 204
    And the notice keeps the date of the first time

  Scenario: Two requests marking the same notice take turns and keep the first date
    Given a notice that one request is marking as read and has not finished
    When a second request marks it as read
    Then the second waits for the first
    And the notice keeps the date of the first

  Scenario: Reading all leaves none unread
    Given Ana has three notices and has read one a while ago, and Bruno has one
    When she marks all as read
    Then the answer is 204
    And none of hers is unread and the one she had read keeps its date
    And Bruno's notice is still unread

  Scenario: Somebody else's notice cannot be marked as read
    Given Ana has a notice, and so do Bruno and a student of another university
    When Ana marks Bruno's notice, the one of the other university and one that does not exist
    Then each answer is 404
    And neither notice was marked

  Scenario: An emailed link cannot be marked as read
    Given Ana has an access link
    When she marks it as read
    Then the answer is 404

  Scenario: A request without a session is refused
    When somebody lists notices without a session token, or with one nobody issued
    Then the answer is 401

  Scenario: A platform administrator is refused
    Given a signed in platform administrator
    When they list notices or mark all as read
    Then both answers are 403
