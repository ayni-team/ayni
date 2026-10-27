# Mail routing - a demonstration can deliver for real to a few addresses while everybody else keeps
# going to Mailpit.
#
# This is not a story of the backlog, so it carries no US number. Under dev and demo every email
# leaves through spring.mail.* to Mailpit, which shows each message with its recipient: that is
# right for invented accounts. The lead wants to register with their own address and receive the
# link in their real mailbox, while the invented students, who carry the domain of real
# universities, never receive a real email: with a real relay behind every email, writing to an
# invented person at a real domain would reach a real one. So the real relay is an exception for
# an explicit list of addresses (ayni.notifications.real-relay.*), compared whole and never by
# domain, and nothing else is changed.
#
# Every scenario names the test that runs it, with the same name. The rule of who goes where is
# covered without Spring by RoutingEmailDeliveryTest. The scenarios run against two real SMTP
# servers inside the test, the main one and the real one, in MailRoutingTest, with the real
# configuration of the application; the one that fails a notice runs the whole application against
# a real PostgreSQL in MailRoutingFailedNoticeAcceptanceTest.

Feature: Delivering for real only to the allowed addresses

  Background:
    Given a main mail server, which stands for Mailpit
    And a real relay, which stands for a provider

  Scenario: An allowed recipient is delivered only through the real relay
    Given the real relay is configured for an allowed address
    When an email is sent to that address
    Then the real relay receives it and the main server receives nothing

  Scenario: A recipient that is not allowed is delivered only through the main server
    Given the real relay is configured for an allowed address
    When an email is sent to another address of the same domain
    Then the main server receives it and the real relay has nothing

  Scenario: The allowed list is compared without regard to letter case
    Given the real relay is configured for an allowed address written in lower case
    When an email is sent to that address written in upper case
    Then the real relay receives it and the main server receives nothing

  Scenario: Without a real relay every email goes through the main server
    Given no real relay is configured
    When an email is sent to any address
    Then the main server receives it and the real relay has nothing

  Scenario: A real relay configuration that is incomplete stops the startup
    Given a list of allowed addresses with no host, or a host with no list
    When the application starts
    Then it does not start and the message says what is missing without naming any address

  Scenario: A real relay that is down fails the notice and sends nothing to the main server
    Given the real relay is configured for the address of a student and it is not running
    When that student asks for an access link
    Then the notice of the link is recorded as failed and the main server receives nothing

  Scenario: The log counts the emails of each path and names no address
    Given the real relay is configured for an allowed address
    When one email goes to that address and one to another
    Then the log says how many left through each server and contains neither address
