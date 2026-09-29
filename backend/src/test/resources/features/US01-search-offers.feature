# US01 - As a student struggling in a course, I want to see which tutoring blocks are available for
# that course at the times I can make, so that I get help before my next assessment.
#
# Includes the part of US02 the search owns: when several tutors are free at the same hour, all of
# them come back with their standing in the course, and the student chooses.
#
# Every scenario names the test that runs it, against a real PostgreSQL. The acceptance tests fill
# the projection by publishing booking's events; the end to end tests go through skills, booking and
# wallet over HTTP and write nothing by hand.
#
#   Search with results                         SearchOffersAcceptanceTest.searchWithResults
#   Several tutors at the same hour             SearchOffersAcceptanceTest.severalTutorsAtTheSameHour
#                                               NearestOffersTest, AvailableOfferTest.searchOrder
#   No availability in the chosen window        SearchOffersAcceptanceTest.noAvailabilityInTheChosenWindow
#                                               SearchOffersUseCaseTest.anEmptyWindowFallsBackToTheClosest
#   No tutor at all for the course              SearchOffersAcceptanceTest.noTutorAtAll
#   Times are stored in UTC and shown in the university's time zone
#                                               SearchOffersAcceptanceTest.timesAreStoredInUtcAndShownInTheUniversitysTimeZone
#   Hours that have already started are not offered
#                                               SearchOffersAcceptanceTest.hoursThatStartedAreNotOffered
#   Only the offers of the student's university SearchOffersAcceptanceTest.onlyTheOffersOfTheStudentsUniversity
#   A tutor searching the course does not find their own hours
#                                               SearchOffersAcceptanceTest.aTutorDoesNotFindTheirOwnHours
#   A long list comes in pages                  SearchOffersAcceptanceTest.aLongListComesInPages
#   A tutor with an enabled course declares availability, is found, and booked hours leave
#                                               SearchOffersEndToEndTest.aTutorIsFoundUntilTheHoursAreBooked
#   A tutor who declares availability before their first course is found after the night
#                                               SearchOffersEndToEndTest.availabilityBeforeTheFirstCourseWaitsForTheNightlyJob
#   How the search is kept up to date           OfferProjectionListenersTest, one test per event

Feature: Finding tutoring available for a course

  Background:
    Given a student Ana of the university "UPC", whose time zone is America/Lima
    And the course "Databases I" in the catalogue of "UPC"

  Scenario: Search with results
    Given Bruno, enabled for "Databases I", is free tomorrow at 15:00 and 16:00
    When Ana searches "Databases I" tomorrow from 14:00 to 18:00
    Then she sees two one hour blocks, each with its date, its start and end, and Bruno as the tutor
    And each block shows Bruno's average, ratings and sessions taught in "Databases I"

  Scenario: Several tutors at the same hour
    Given Mia with an average of 4.90, Zoe with 4.20 and Aaron, a new tutor, are free tomorrow at 15:00
    When Ana searches "Databases I" tomorrow from 14:00 to 18:00
    Then she sees the three of them at 15:00, and chooses herself
    And Mia comes first, then Zoe, then Aaron
    And Aaron shows no average, only the new tutor mark

  Scenario: No availability in the chosen window
    Given Bruno is free tomorrow at 09:00, 20:00 and 22:00, and nobody else is
    When Ana searches "Databases I" tomorrow from 14:00 to 18:00
    Then the answer is not empty: it holds the closest blocks outside her window
    And it says they are not an exact match
    And when fewer fit in the page, the closest ones are kept

  Scenario: No tutor at all for the course
    Given no tutor is free for "Databases I" at any time
    When Ana searches "Databases I"
    Then the answer is empty and says it is not an exact match

  Scenario: Times are stored in UTC and shown in the university's time zone
    Given Bruno is free tomorrow at 20:00, Lima time
    When Ana searches "Databases I" around that hour
    Then the block's start travels in UTC, at 01:00 of the next day
    And the answer says the university's time zone is America/Lima, to show it as 20:00

  Scenario: Hours that have already started are not offered
    Given Bruno had an hour that ended, has one in progress and one that starts later
    When Ana searches a window covering the three
    Then she only sees the one that has not started

  Scenario: Only the offers of the student's university
    Given a tutor of another university is free at the same hour as Bruno
    When Ana searches "Databases I"
    Then she only sees Bruno

  Scenario: A tutor searching the course does not find their own hours
    Given Ana also tutors "Databases I" and is free tomorrow at 15:00, like Bruno
    When Ana searches "Databases I" tomorrow afternoon
    Then she only sees Bruno

  Scenario: A long list comes in pages
    Given Bruno is free tomorrow at 14:00, 15:00 and 16:00
    When Ana asks for the second page of two
    Then she sees the block at 16:00, and that there are three in two pages

  Scenario: A tutor with an enabled course declares availability, is found, and booked hours leave
    Given Carla offers "Databases I", which her grade enables
    When Carla declares she is free tomorrow from 09:00 to 12:00
    Then Ana finds three blocks of Carla tomorrow, Carla shown as a new tutor
    When Ana holds the blocks at 09:00 and 10:00
    Then they are still in the search while she writes what she needs
    When Ana books them
    Then only the block at 11:00 is left in the search

  Scenario: A tutor who declares availability before their first course is found after the night
    Given Carla declares she is free tomorrow from 09:00 to 12:00 before she can teach anything
    And no hours are generated for her
    When Carla offers "Databases I"
    Then Ana still finds nothing, because Carla has no hours yet
    When the nightly job generates the hours of the coming weeks
    Then Ana finds three blocks of Carla tomorrow

  Scenario: How the search is kept up to date
    Then new hours are offered for every course the tutor has enabled
    And a newly enabled course is offered in the hours the tutor already has open
    And a withdrawn course, withdrawn hours and booked hours leave the search
    And hours a cancellation gives back return if booking still has them open
    And a student's rating refreshes the tutor's standing in that course
    And a hold does not take hours out of the search: booking checks them again anyway
    And every event can be heard twice without duplicating or losing anything
