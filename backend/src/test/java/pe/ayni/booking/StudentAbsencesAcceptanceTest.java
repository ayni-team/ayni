package pe.ayni.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import pe.ayni.booking.infrastructure.StudentAbsenceRepository;
import pe.ayni.notifications.application.EmailDelivery;
import pe.ayni.shared.events.SessionAbandoned;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * US34, scenario 4 of {@code features/US32-rate-session.feature}, over HTTP against a real
 * PostgreSQL: the absences of a student are recorded when sessions announces {@code
 * SessionAbandoned}, and the tutor's agenda shows how many there are.
 *
 * <p>Nothing is inserted by hand: the real event is published, as sessions publishes it, and the
 * listeners of every module react. The listeners are asynchronous, so what must not happen is
 * proven by asking that the figure holds for two seconds.
 */
class StudentAbsencesAcceptanceTest extends SignedInBookingScenario {

  @Autowired private ApplicationEventPublisher events;
  @Autowired private TransactionTemplate transactions;
  @Autowired private StudentAbsenceRepository absences;
  // The notice of a tutor no-show is sent by the real notifications module; nothing leaves the test.
  @MockitoBean private EmailDelivery emailDelivery;

  private SessionAbandoned abandoned(String tenantId, UUID bookingId, boolean studentCheckedIn) {
    return new SessionAbandoned(
        tenantId,
        UUID.randomUUID(),
        bookingId,
        tutor.id(),
        ana.id(),
        subject,
        studentCheckedIn,
        clock.instant());
  }

  private void publish(SessionAbandoned event) {
    transactions.executeWithoutResult(status -> events.publishEvent(event));
  }

  private String agendaOf(LocalDate day) throws Exception {
    return mockMvc
        .perform(as(tutor, get("/api/v1/tutor/agenda?from=" + day + "&to=" + day)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  /** The absences the agenda shows next to the student of that booking, or null if not listed. */
  private Integer shownInAgenda(UUID bookingId) throws Exception {
    List<Integer> shown =
        JsonPath.read(
            agendaOf(today().plusDays(1)),
            "$.lines[?(@.booking.bookingId == '" + bookingId + "')].booking.student.absenceCount");
    return shown.isEmpty() ? null : shown.get(0);
  }

  /** The absences the history shows next to the student of that booking, or null if not listed. */
  private Integer shownInHistory(UUID bookingId) throws Exception {
    String history =
        mockMvc
            .perform(as(tutor, get("/api/v1/tutor/agenda/history")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<Integer> shown =
        JsonPath.read(
            history, "$.items[?(@.bookingId == '" + bookingId + "')].student.absenceCount");
    return shown.isEmpty() ? null : shown.get(0);
  }

  private void waitUntilHistoryShows(UUID bookingId, int absences) {
    await()
        .atMost(Duration.ofSeconds(20))
        .until(() -> Integer.valueOf(absences).equals(shownInHistory(bookingId)));
  }

  private void historyKeepsShowing(UUID bookingId, int absences) {
    await()
        .atMost(Duration.ofSeconds(20))
        .during(Duration.ofSeconds(2))
        .until(() -> Integer.valueOf(absences).equals(shownInHistory(bookingId)));
  }

  @Test
  @DisplayName("A tutor sees the student's absences before the session")
  void aTutorSeesTheStudentsAbsencesBeforeTheSession() throws Exception {
    UUID missed = book(ana, tomorrowAt(9), 1, "Normal forms before Friday");
    publish(abandoned(tenant, missed, false));
    waitUntilHistoryShows(missed, 1);

    UUID next = book(ana, tomorrowAt(11), 1, "Joins before the lab");
    UUID brunos = book(bruno, tomorrowAt(10), 1, "Indexes");

    assertThat(shownInAgenda(next)).isEqualTo(1);
    assertThat(shownInAgenda(brunos)).isZero();
    String day = agendaOf(today().plusDays(1));
    assertThat(
            JsonPath.<List<String>>read(
                day, "$.lines[?(@.booking.bookingId == '" + next + "')].booking.student.id"))
        .containsExactly(ana.id().toString());
  }

  @Test
  @DisplayName("A student who has never missed a session shows no absences")
  void aStudentWhoNeverMissedShowsNoAbsences() throws Exception {
    UUID booked = book(ana, tomorrowAt(9), 1, "Normal forms before Friday");

    assertThat(shownInAgenda(booked)).isZero();
  }

  @Test
  @DisplayName("A session the tutor missed does not count against the student")
  void aSessionTheTutorMissedDoesNotCountAgainstTheStudent() throws Exception {
    UUID tutorsFault = book(ana, tomorrowAt(9), 1, "Normal forms before Friday");

    publish(abandoned(tenant, tutorsFault, true));

    // The booking becomes a no-show, which puts it in the history, and the student gets nothing.
    await().atMost(Duration.ofSeconds(20)).until(() -> shownInHistory(tutorsFault) != null);
    historyKeepsShowing(tutorsFault, 0);
  }

  @Test
  @DisplayName("The same abandoned session delivered twice counts one absence")
  void theSameAbandonedSessionDeliveredTwiceCountsOneAbsence() throws Exception {
    UUID missed = book(ana, tomorrowAt(9), 1, "Normal forms before Friday");
    SessionAbandoned event = abandoned(tenant, missed, false);

    publish(event);
    publish(event);

    waitUntilHistoryShows(missed, 1);
    historyKeepsShowing(missed, 1);
  }

  @Test
  @DisplayName("Another university neither sees nor counts the absences")
  void anotherUniversityNeitherSeesNorCountsTheAbsences() throws Exception {
    String other = people.aUniversity("B");
    UUID missed = book(ana, tomorrowAt(9), 1, "Normal forms before Friday");

    publish(abandoned(tenant, missed, false));
    waitUntilHistoryShows(missed, 1);

    assertThat(
            TenantContext.callAs(other, () -> absences.countByStudent(other, List.of(ana.id()))))
        .isEmpty();
    assertThat(
            TenantContext.callAs(tenant, () -> absences.countByStudent(tenant, List.of(ana.id()))))
        .hasSize(1);
  }
}
