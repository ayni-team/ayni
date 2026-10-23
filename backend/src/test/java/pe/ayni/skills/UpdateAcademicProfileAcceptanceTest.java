package pe.ayni.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import pe.ayni.config.SignedInPeople.Person;
import pe.ayni.shared.domain.CreditType;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.wallet.WalletApi;

/**
 * US41 over HTTP against a real PostgreSQL, signed in with real session tokens: one test per
 * scenario of {@code features/US41-update-academic-profile.feature}, with the same name.
 *
 * <p>The booking of the last scenario is made the way a student makes one, holding and confirming
 * the hours of a tutor who was enabled by their own onboarding, so what stays untouched is a real
 * confirmed booking with its credits charged.
 */
class UpdateAcademicProfileAcceptanceTest extends OnboardingScenario {

  @Autowired private Clock clock;
  @Autowired private WalletApi wallet;

  @Test
  @DisplayName("Updating my needs")
  void updatingMyNeeds() throws Exception {
    // Given the courses I need help with changed
    Person student = aNewStudent();
    onboard(student, List.of(physics.getId()), List.of());

    // When I update them
    String answer =
        replaceNeeds(student, List.of(calculus.getId(), python.getId()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Then what I am offered from now on reads my new needs: the whole set was replaced
    assertThat(JsonPath.<List<String>>read(answer, "$[*].id"))
        .containsExactlyInAnyOrder(calculus.getId().toString(), python.getId().toString());
    assertThat(JsonPath.<List<String>>read(read(student, "/api/v1/me/learning-interests"), "$[*].id"))
        .containsExactlyInAnyOrder(calculus.getId().toString(), python.getId().toString());
    assertThat(storedNeedsOf(student)).containsExactlyInAnyOrder(calculus.getId(), python.getId());

    // An empty set leaves none, and a body without the list is refused
    replaceNeeds(student, List.of()).andExpect(status().isOk());
    assertThat(storedNeedsOf(student)).isEmpty();
    mockMvc
        .perform(
            as(student, put("/api/v1/me/learning-interests"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());

    // The step of the onboarding is not touched
    assertThat(stepOf(student)).isEqualTo("COMPLETED");
  }

  @Test
  @DisplayName("Newly approved courses")
  void newlyApprovedCourses() throws Exception {
    // Given I approved physics in the last term
    Person student = aNewStudent();
    onboard(student, List.of(physics.getId()), List.of(databases.getId()));
    approved(
        student,
        courseRecord(databases, "17.00"),
        courseRecord(calculus, "11.00"),
        courseRecord(physics, "16.00"));

    // When I bring my record up to date in the new term
    importRecord(student);

    // Then physics is among what I can offer as a tutoring
    String suggestions = read(student, "/api/v1/tutor/skills/suggestions");
    assertThat(JsonPath.<List<String>>read(suggestions, "$[*].item.id"))
        .containsExactly(physics.getId().toString());
    mockMvc
        .perform(
            as(student, post("/api/v1/tutor/skills"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"catalogItemId\": \"%s\"}".formatted(physics.getId())))
        .andExpect(status().isCreated());
    assertThat(JsonPath.<List<String>>read(read(student, "/api/v1/tutor/skills"), "$[*].catalogItemId"))
        .containsExactlyInAnyOrder(databases.getId().toString(), physics.getId().toString());
    assertThat(stepOf(student)).isEqualTo("COMPLETED");
  }

  @Test
  @DisplayName("Data that comes from my university")
  void dataThatComesFromMyUniversity() throws Exception {
    // Given my name, code and career come from my university
    Person student = aNewStudent();
    onboard(student, List.of(physics.getId()), List.of());
    String before = read(student, "/api/v1/me");

    // When I review my profile and try to change them
    mockMvc
        .perform(
            as(student, put("/api/v1/me/profile"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"photoUrl": null, "bio": "Now in the eighth term", "fullName": "Someone else",
                     "studentCode": "X-1", "career": "Law", "email": "other@example.com"}
                    """))
        .andExpect(status().isOk());

    // Then I see them, but the platform does not change them
    String after = read(student, "/api/v1/me");
    for (String field : List.of("$.fullName", "$.studentCode", "$.career", "$.currentTerm", "$.email")) {
      assertThat(JsonPath.<Object>read(after, field)).isEqualTo(JsonPath.read(before, field));
    }
    assertThat(JsonPath.<String>read(after, "$.fullName")).isNotBlank();
    assertThat(JsonPath.<String>read(after, "$.studentCode")).isNotBlank();
    assertThat(JsonPath.<String>read(after, "$.bio")).isEqualTo("Now in the eighth term");
    assertThat(JsonPath.<String>read(after, "$.onboardingStep")).isEqualTo("COMPLETED");
  }

  @Test
  @DisplayName("Changes do not affect what is already committed")
  void changesDoNotAffectWhatIsAlreadyCommitted() throws Exception {
    // Given a tutor enabled by their onboarding and a student with a confirmed tutoring with them
    Person tutor = aNewStudent();
    onboard(tutor, List.of(physics.getId()), List.of(databases.getId()));
    Person student = aNewStudent();
    onboard(student, List.of(databases.getId()), List.of());
    UUID booking = book(student, tutor, tomorrowAt(10));
    Map<String, Object> confirmed = bookingRow(booking);
    assertThat(confirmed.get("status")).isEqualTo("CONFIRMED");

    // When both update what they need, and the tutor brings the record up to date
    replaceNeeds(student, List.of(calculus.getId())).andExpect(status().isOk());
    replaceNeeds(tutor, List.of()).andExpect(status().isOk());
    importRecord(tutor);

    // Then the tutoring stays as it was
    assertThat(bookingRow(booking)).isEqualTo(confirmed);
    String upcoming = read(student, "/api/v1/bookings/mine");
    assertThat(JsonPath.<List<String>>read(upcoming, "$.items[*].bookingId")).containsExactly(booking.toString());
    assertThat(JsonPath.<List<String>>read(upcoming, "$.items[*].status")).containsExactly("CONFIRMED");
  }

  private Instant tomorrowAt(int hour) {
    LocalDate today = LocalDate.now(clock.withZone(OnboardingTestConfig.LIMA));
    return ZonedDateTime.of(today.plusDays(1), LocalTime.of(hour, 0), OnboardingTestConfig.LIMA)
        .toInstant();
  }

  /** The tutor publishes tomorrow morning and the student holds and confirms an hour of it. */
  private UUID book(Person student, Person tutor, Instant start) throws Exception {
    LocalDate today = LocalDate.now(clock.withZone(OnboardingTestConfig.LIMA));
    mockMvc
        .perform(
            as(tutor, post("/api/v1/tutor/availability"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"dayOfWeek":"%s","startsAtTime":"09:00:00","endsAtTime":"12:00:00",
                     "validFrom":"%s"}
                    """
                        .formatted(today.plusDays(1).getDayOfWeek().name(), today)))
        .andExpect(status().isCreated());
    TenantContext.runAs(
        tenant,
        () -> wallet.grant(student.id(), Credits.of(5), CreditType.EARNED, null, UUID.randomUUID()));
    mockMvc
        .perform(
            as(student, post("/api/v1/bookings/holds"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"tutorId\":\"%s\",\"start\":\"%s\",\"hours\":1}".formatted(tutor.id(), start)))
        .andExpect(status().isCreated());
    String booked =
        mockMvc
            .perform(
                as(student, post("/api/v1/bookings"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"tutorId":"%s","catalogItemId":"%s","start":"%s","hours":1,
                         "needDescription":"Normal forms before the exam"}
                        """
                            .formatted(tutor.id(), databases.getId(), start)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(JsonPath.read(booked, "$.id"));
  }

  private Map<String, Object> bookingRow(UUID booking) {
    return jdbc.queryForMap(
        "select status, starts_at, hours, tutor_id, student_id, catalog_item_id, updated_at"
            + " from booking.bookings where id = ?",
        booking);
  }
}
