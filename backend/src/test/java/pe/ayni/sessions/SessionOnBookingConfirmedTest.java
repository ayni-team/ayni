package pe.ayni.sessions;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.sessions.domain.model.Session;
import pe.ayni.sessions.infrastructure.SessionRepository;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.events.BookingConfirmed;

/**
 * A confirmed booking creates its session, as the backend guide says sessions must.
 *
 * <p>The event is published inside a transaction, as booking publishes it: the listener runs once
 * that transaction commits, and never when it rolls back.
 */
@SpringBootTest
class SessionOnBookingConfirmedTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = SessionsTestDatabase.INSTANCE;

  private static final String UPC = "UPC";

  private final Instant nine = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(Duration.ofDays(1));

  @Autowired private ApplicationEventPublisher events;
  @Autowired private TransactionTemplate transactions;
  @Autowired private SessionRepository sessions;

  private BookingConfirmed aConfirmedBooking() {
    return new BookingConfirmed(
        UPC,
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        nine,
        nine.plus(Duration.ofHours(2)),
        List.of(UUID.randomUUID(), UUID.randomUUID()),
        Credits.of(2),
        Instant.now());
  }

  @Test
  @DisplayName("a confirmed booking gets a scheduled session with its hours and participants")
  void aConfirmedBookingGetsItsSession() {

    BookingConfirmed confirmed = aConfirmedBooking();

    transactions.executeWithoutResult(status -> events.publishEvent(confirmed));

    Session session = sessions.findByTenantIdAndBookingId(UPC, confirmed.bookingId()).orElseThrow();
    assertThat(session.getStatus()).isEqualTo(SessionStatus.SCHEDULED);
    assertThat(session.getStudentId()).isEqualTo(confirmed.studentId());
    assertThat(session.getTutorId()).isEqualTo(confirmed.tutorId());
    assertThat(session.getScheduledStart()).isEqualTo(confirmed.startsAt());
    assertThat(session.getScheduledEnd()).isEqualTo(confirmed.endsAt());
    assertThat(session.getRoomName()).matches("ayni-[0-9a-f]{32}");
    assertThat(sessions.findByTenantIdAndBookingId("PUCP", confirmed.bookingId())).isEmpty();
  }

  @Test
  @DisplayName("the same confirmation heard twice still makes one session")
  void hearingItTwiceMakesOneSession() {

    BookingConfirmed confirmed = aConfirmedBooking();

    transactions.executeWithoutResult(status -> events.publishEvent(confirmed));
    String firstRoom =
        sessions.findByTenantIdAndBookingId(UPC, confirmed.bookingId()).orElseThrow().getRoomName();
    transactions.executeWithoutResult(status -> events.publishEvent(confirmed));

    assertThat(sessions.findAll())
        .filteredOn(session -> session.getBookingId().equals(confirmed.bookingId()))
        .singleElement()
        .extracting(Session::getRoomName)
        .isEqualTo(firstRoom);
  }

  @Test
  @DisplayName("a confirmation whose transaction rolled back creates nothing")
  void aRolledBackConfirmationCreatesNothing() {

    BookingConfirmed confirmed = aConfirmedBooking();

    transactions.executeWithoutResult(
        status -> {
          events.publishEvent(confirmed);
          status.setRollbackOnly();
        });

    assertThat(sessions.existsByTenantIdAndBookingId(UPC, confirmed.bookingId())).isFalse();
  }
}
