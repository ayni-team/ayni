package pe.ayni.sessions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.events.BookingConfirmed;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * {@link SessionsApi} against PostgreSQL, the way recognition will call it.
 *
 * <p>Sessions are created the real way, by a confirmed booking. Nothing moves a session to
 * COMPLETED yet (that arrives with US11), so the tests that need one mark it with SQL, as the
 * story will once it closes sessions.
 */
@SpringBootTest
class SessionsApiDatabaseTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = SessionsTestDatabase.INSTANCE;

  private static final String UPC = "UPC";

  private final UUID tutor = UUID.randomUUID();
  private final Instant tomorrow =
      Instant.now().truncatedTo(ChronoUnit.HOURS).plus(Duration.ofDays(1));

  @Autowired private SessionsApi sessionsApi;
  @Autowired private ApplicationEventPublisher events;
  @Autowired private TransactionTemplate transactions;
  @Autowired private JdbcTemplate jdbc;

  private <T> T in(String tenantId, Supplier<T> work) {
    AtomicReference<T> result = new AtomicReference<>();
    TenantContext.runAs(tenantId, () -> result.set(work.get()));
    return result.get();
  }

  /** A booking of that many hours, confirmed, which schedules its session. Returns the session. */
  private UUID aConfirmedBooking(String tenantId, UUID tutorId, Instant startsAt, int hours) {
    BookingConfirmed confirmed =
        new BookingConfirmed(
            tenantId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            tutorId,
            UUID.randomUUID(),
            startsAt,
            startsAt.plus(Duration.ofHours(hours)),
            List.of(UUID.randomUUID()),
            Credits.of(hours),
            Instant.now());
    transactions.executeWithoutResult(status -> events.publishEvent(confirmed));
    return jdbc.queryForObject(
        "select id from sessions.sessions where tenant_id = ? and booking_id = ?",
        UUID.class,
        tenantId,
        confirmed.bookingId());
  }

  private void completed(UUID sessionId) {
    jdbc.update(
        """
        update sessions.sessions
        set status = 'COMPLETED', started_at = scheduled_start, ended_at = scheduled_end
        where id = ?
        """,
        sessionId);
  }

  @Test
  @DisplayName("a scheduled session is read by id, within its university only")
  void aSessionIsReadWithinItsUniversity() {

    UUID session = aConfirmedBooking(UPC, tutor, tomorrow, 2);

    SessionView view = in(UPC, () -> sessionsApi.requireSession(session));

    assertThat(view.tutorId()).isEqualTo(tutor);
    assertThat(view.status()).isEqualTo(SessionStatus.SCHEDULED);
    assertThat(view.scheduledStart()).isEqualTo(tomorrow);
    TenantContext.runAs(
        "PUCP",
        () ->
            assertThatThrownBy(() -> sessionsApi.requireSession(session))
                .isInstanceOf(NoSuchElementException.class));
  }

  @Test
  @DisplayName("only a tutor's completed sessions of the university count, the earliest first")
  void onlyCompletedSessionsCount() {

    UUID later = aConfirmedBooking(UPC, tutor, tomorrow.plus(Duration.ofDays(2)), 1);
    UUID earlier = aConfirmedBooking(UPC, tutor, tomorrow, 2);
    UUID stillScheduled = aConfirmedBooking(UPC, tutor, tomorrow.plus(Duration.ofDays(3)), 1);
    UUID someoneElses = aConfirmedBooking(UPC, UUID.randomUUID(), tomorrow, 1);
    UUID elsewhere = aConfirmedBooking("PUCP", tutor, tomorrow, 1);
    completed(later);
    completed(earlier);
    completed(someoneElses);
    completed(elsewhere);

    List<SessionSummary> summaries = in(UPC, () -> sessionsApi.completedSessionsOf(tutor));

    assertThat(summaries)
        .extracting(SessionSummary::sessionId)
        .containsExactly(earlier, later)
        .doesNotContain(stillScheduled);
    assertThat(summaries).extracting(SessionSummary::hours).containsExactly(2, 1);
    assertThat(summaries.getFirst().startedAt()).isEqualTo(tomorrow);
  }

  @Test
  @DisplayName("an unverified session backs no recognition")
  void anUnverifiedSessionDoesNotCount() {

    UUID session = aConfirmedBooking(UPC, tutor, tomorrow, 1);
    jdbc.update("update sessions.sessions set status = 'UNVERIFIED' where id = ?", session);

    assertThat(in(UPC, () -> sessionsApi.completedSessionsOf(tutor))).isEmpty();
  }
}
