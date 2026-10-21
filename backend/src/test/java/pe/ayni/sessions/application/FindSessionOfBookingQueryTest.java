package pe.ayni.sessions.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.sessions.SessionStatus;
import pe.ayni.sessions.domain.model.NotAParticipant;
import pe.ayni.sessions.domain.model.Session;
import pe.ayni.sessions.infrastructure.SessionRepository;
import pe.ayni.shared.tenancy.TenantContext;

class FindSessionOfBookingQueryTest {

  private static final String UPC = "UPC";
  private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");
  private static final Instant START = NOW.plus(Duration.ofDays(1));

  private final SessionRepository sessions = mock(SessionRepository.class);
  private final FindSessionOfBookingQuery query = new FindSessionOfBookingQuery(sessions);

  @Test
  @DisplayName("the student and the tutor are given the session of the booking")
  void givesTheSessionToItsParticipants() {
    UUID bookingId = UUID.randomUUID();
    Session session = session(bookingId);
    when(sessions.findByTenantIdAndBookingId(UPC, bookingId)).thenReturn(Optional.of(session));

    for (UUID participant : new UUID[] {session.getStudentId(), session.getTutorId()}) {
      SessionOfBooking found = TenantContext.callAs(UPC, () -> query.execute(bookingId, participant));

      assertThat(found.sessionId()).isEqualTo(session.getId());
      assertThat(found.bookingId()).isEqualTo(bookingId);
      assertThat(found.status()).isEqualTo(SessionStatus.SCHEDULED);
      assertThat(found.joinOpensAt()).isEqualTo(START.minus(Duration.ofMinutes(15)));
      assertThat(found.scheduledEnd()).isEqualTo(START.plus(Duration.ofHours(1)));
    }
  }

  @Test
  @DisplayName("somebody else is refused")
  void refusesAStranger() {
    UUID bookingId = UUID.randomUUID();
    when(sessions.findByTenantIdAndBookingId(UPC, bookingId))
        .thenReturn(Optional.of(session(bookingId)));

    assertThatThrownBy(
            () -> TenantContext.runAs(UPC, () -> query.execute(bookingId, UUID.randomUUID())))
        .isInstanceOf(NotAParticipant.class);
  }

  @Test
  @DisplayName("a booking without a session in the university is not found")
  void doesNotFindABookingWithoutASession() {
    UUID bookingId = UUID.randomUUID();
    when(sessions.findByTenantIdAndBookingId(UPC, bookingId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> TenantContext.runAs(UPC, () -> query.execute(bookingId, UUID.randomUUID())))
        .isInstanceOf(NoSuchElementException.class);
  }

  private static Session session(UUID bookingId) {
    return Session.schedule(
        UUID.randomUUID(),
        UPC,
        bookingId,
        UUID.randomUUID(),
        UUID.randomUUID(),
        START,
        START.plus(Duration.ofHours(1)),
        NOW);
  }
}
