package pe.ayni.sessions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pe.ayni.booking.BookingScenario;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;
import pe.ayni.notifications.application.EmailDelivery;
import pe.ayni.sessions.application.AbandonTutorNoShowUseCase;
import pe.ayni.shared.events.SessionAbandoned;
import pe.ayni.shared.events.SessionUnverified;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * The world every tutor no-show test starts from: Ana has booked Bruno's nine o'clock hour
 * tomorrow, for real, over HTTP, and the clock is the one the tests move.
 *
 * <p>Booking, sessions, wallet and notifications are the real modules on a real PostgreSQL;
 * identity and skills are the seams {@link BookingScenario} mocks, and the carrier of the email is
 * mocked here so the test can read what the student was sent. The tutor of the scenario, Bruno, is
 * {@link #tutor}.
 */
@Import(TutorNoShowScenario.Config.class)
abstract class TutorNoShowScenario extends BookingScenario {

  protected static final String ANAS_EMAIL = "u202400001@upc.edu.pe";

  @Autowired protected AbandonTutorNoShowUseCase abandonNoShows;
  @Autowired protected Announcements announcements;
  @MockitoBean protected EmailDelivery emailDelivery;

  /** The hour Ana booked, taken before a test moves the clock. */
  protected Instant start;

  /**
   * Hears what sessions announced from whichever thread announced it, once its transaction
   * committed. {@code ApplicationEvents} only sees the test thread, and the race test publishes
   * from worker threads.
   */
  @TestConfiguration(proxyBeanMethods = false)
  static class Config {

    @Bean
    Announcements announcements() {
      return new Announcements();
    }
  }

  /** The events sessions published and committed, by session. */
  static class Announcements {

    private final List<SessionAbandoned> abandoned = new CopyOnWriteArrayList<>();
    private final List<SessionUnverified> unverified = new CopyOnWriteArrayList<>();

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(SessionAbandoned event) {
      abandoned.add(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(SessionUnverified event) {
      unverified.add(event);
    }

    List<SessionAbandoned> abandonedOf(UUID sessionId) {
      return abandoned.stream().filter(event -> event.sessionId().equals(sessionId)).toList();
    }

    List<SessionUnverified> unverifiedOf(UUID sessionId) {
      return unverified.stream().filter(event -> event.sessionId().equals(sessionId)).toList();
    }
  }

  @BeforeEach
  void anaIsKnownAndHoldsTheHour() {
    start = tomorrowAt(9);
    when(identity.requireUser(ana))
        .thenReturn(
            new UserView(
                ana, UPC, UserRole.STUDENT, ANAS_EMAIL, "U202400001", "Ana Torres",
                "Software Engineering", "2026-2", null));
  }

  /**
   * Ana books the hour with five credits, the real way: a hold and a booking over HTTP.
   *
   * @return the booking, which has charged her one credit
   */
  protected UUID anaBooksTheHour() throws Exception {
    clock.set(start.minus(Duration.ofDays(1)));
    grant(ana, 5);
    hold(ana, start, 1).andExpect(status().isCreated());
    String answer =
        book(ana, start, 1, "Normal forms")
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID bookingId = UUID.fromString(JsonPath.read(answer, "$.id"));
    assertThat(balanceOf(ana)).as("the hour was charged").isEqualTo(4);
    return bookingId;
  }

  protected UUID sessionOf(UUID bookingId) {
    return jdbc.queryForObject(
        "select id from sessions.sessions where tenant_id = ? and booking_id = ?",
        UUID.class,
        UPC,
        bookingId);
  }

  protected ResultActions join(UUID sessionId, UUID asUser) throws Exception {
    return mockMvc.perform(
        post("/api/v1/sessions/{id}/join", sessionId)
            .header("X-Tenant-Id", UPC)
            .header("X-User-Id", asUser));
  }

  /** Ana is in the room five minutes before the start. */
  protected void anaCheckedIn(UUID sessionId) throws Exception {
    clock.set(start.minus(Duration.ofMinutes(5)));
    join(sessionId, ana).andExpect(status().isOk());
  }

  /** The job's work: finds what is due in the university and abandons one session. */
  protected boolean abandonDue(UUID sessionId) {
    boolean[] abandoned = new boolean[1];
    TenantContext.runAs(
        UPC,
        () -> {
          assertThat(abandonNoShows.dueNow()).contains(sessionId);
          abandoned[0] = abandonNoShows.abandonFor(sessionId);
        });
    return abandoned[0];
  }

  protected String sessionStatus(UUID sessionId) {
    return jdbc.queryForObject(
        "select status from sessions.sessions where id = ?", String.class, sessionId);
  }

  protected String bookingStatus(UUID bookingId) {
    return jdbc.queryForObject(
        "select status from booking.bookings where id = ?", String.class, bookingId);
  }

  protected int refundsOf(UUID bookingId) {
    return jdbc.queryForObject(
        """
        select count(*) from wallet.ledger_entries
        where tenant_id = ? and reference_type = 'BOOKING' and reference_id = ?
          and reason = 'BOOKING_REFUND'
        """,
        Integer.class,
        UPC,
        bookingId);
  }

  /** What booking holds against the tutor for this booking, by kind. */
  protected int incidentsOf(UUID bookingId, String kind) {
    return jdbc.queryForObject(
        """
        select count(*) from booking.tutor_reliability
        where tenant_id = ? and tutor_id = ? and booking_id = ? and kind = ?
        """,
        Integer.class,
        UPC,
        tutor,
        bookingId,
        kind);
  }

  protected int presenceChecksOf(UUID sessionId) {
    return jdbc.queryForObject(
        "select count(*) from sessions.presence_checks where tenant_id = ? and session_id = ?",
        Integer.class,
        UPC,
        sessionId);
  }

  protected int noShowNoticesOf(UUID sessionId) {
    return jdbc.queryForObject(
        """
        select count(*) from notifications.notifications
        where tenant_id = ? and kind = 'TUTOR_NO_SHOW' and payload ->> 'sessionId' = ?
        """,
        Integer.class,
        UPC,
        sessionId.toString());
  }
}
