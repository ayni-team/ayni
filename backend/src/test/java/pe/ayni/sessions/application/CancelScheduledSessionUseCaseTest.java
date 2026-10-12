package pe.ayni.sessions.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.sessions.SessionStatus;
import pe.ayni.sessions.domain.model.Session;
import pe.ayni.sessions.infrastructure.SessionRepository;
import pe.ayni.shared.tenancy.TenantContext;

class CancelScheduledSessionUseCaseTest {

  private static final String UPC = "UPC";
  private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");
  private static final Instant START = NOW.plus(Duration.ofDays(1));

  private final SessionRepository sessions = mock(SessionRepository.class);
  private final CancelScheduledSessionUseCase useCase =
      new CancelScheduledSessionUseCase(sessions);

  @Test
  @DisplayName("cancelling a booking changes its scheduled session to cancelled")
  void cancelsTheScheduledSession() {
    UUID bookingId = UUID.randomUUID();
    Session session = session(bookingId);
    when(sessions.lockByTenantIdAndBookingId(UPC, bookingId)).thenReturn(Optional.of(session));

    TenantContext.runAs(UPC, () -> useCase.execute(bookingId, NOW));

    assertThat(session.getStatus()).isEqualTo(SessionStatus.CANCELLED);
    assertThat(session.getEndedAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("a session that already started is not cancelled")
  void leavesAnInProgressSessionAlone() {
    UUID bookingId = UUID.randomUUID();
    Session session = session(bookingId);
    session.join(session.getStudentId(), START);
    when(sessions.lockByTenantIdAndBookingId(UPC, bookingId)).thenReturn(Optional.of(session));

    TenantContext.runAs(UPC, () -> useCase.execute(bookingId, START.plusSeconds(1)));

    assertThat(session.getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
    assertThat(session.getEndedAt()).isNull();
  }

  @Test
  @DisplayName("a missing session for a cancelled booking is harmless")
  void toleratesMissingSession() {
    UUID bookingId = UUID.randomUUID();
    when(sessions.lockByTenantIdAndBookingId(UPC, bookingId)).thenReturn(Optional.empty());

    TenantContext.runAs(UPC, () -> useCase.execute(bookingId, NOW));
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
        NOW.minus(Duration.ofDays(1)));
  }
}
