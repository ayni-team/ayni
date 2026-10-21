# US33 - As a tutor who wants to keep receiving bookings, I want to see how I am being rated and what
# is said about my sessions, so that I correct what is needed before my skills lose their
# enablement.
#
# GET /api/v1/tutor/reputation answers the caller's own reputation, one entry per skill, never one
# figure overall: a skill they taught, or one they are enabled for and never taught. Below three
# ratings there is no average, only the provisional average of the ratings so far, which only the
# tutor sees, and how many ratings are missing. Nothing in the answer says who rated.
#
# The risk is a mark and nothing more, as the lead decided: the card asks for a notice and for the
# effect on the skill, and neither is built on purpose. No skill is withdrawn, no event is published
# and no notice is sent; the minimum is the property ayni.reputation.minimum-average, 3.0 by
# default.
#
# Each scenario is covered by a test method with the same display name in
# TutorReputationAcceptanceTest, over HTTP against a real PostgreSQL and signed in with real session
# tokens (TS03). Sessions complete by publishing SessionCompleted, as sessions does, and students
# rate them over HTTP; skills is the seam, mocked with the skills the tutor is enabled for and their
# names.

Feature: The tutor's own reputation

  Background:
    Given a tutor of the university "UPC" who teaches "Calculus I"

  Scenario: A summary of my reputation
    Given I taught sessions that students rated
    When I look at my reputation as a tutor
    Then I see my average, how many ratings I received and the tags students gave me most

  Scenario: My reputation in each skill
    Given I offer several skills
    When I look at my reputation
    Then I see how I am rated in each one separately

  Scenario: A reputation at risk is marked
    Given my average in a skill falls below the minimum required
    When I look at my reputation
    Then that skill is marked at risk and the others are not

  Scenario: A tutor without enough ratings
    Given I do not have the minimum number of ratings yet
    When I look at my reputation
    Then I see how many ratings are missing before an average is calculated, and a provisional average instead

  # Not on the card: what the answer never shows.

  Scenario: Only my own ratings count in my reputation
    Given another tutor of my university and a tutor of another university were rated in "Calculus I"
    When I look at my reputation
    Then none of their ratings or tags count in mine

  Scenario: A skill I am enabled for and never taught has nothing counted
    Given I am enabled for "Physics I" and never taught it
    When I look at my reputation
    Then "Physics I" appears with no sessions, no ratings and three ratings missing
