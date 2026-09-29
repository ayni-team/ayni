package pe.ayni.sessions.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.sessions.SessionStatus;
import pe.ayni.sessions.SessionSummary;
import pe.ayni.sessions.SessionView;
import pe.ayni.sessions.domain.model.Session;
import pe.ayni.sessions.infrastructure.SessionRepository;
import pe.ayni.shared.tenancy.TenantContext;

/** What sessions tells the other modules, without Spring. */
class SessionsServiceTest {

  private static final String UPC = "UPC";
  private static final Instant NINE = Instant.parse("2026-10-01T14:00:00Z");

  private final SessionRepository repository = mock(SessionRepository.class);
  private final SessionsService sessions = new SessionsService(repository);
  private final UUID tutor = UUID.randomUUID();

  private <T> T inUpc(Supplier<T> work) {
    AtomicReference<T> result = new AtomicReference<>();
    TenantContext.runAs(UPC, () -> result.set(work.get()));
    return result.get();
  }

  @Test
  @DisplayName("a session is seen by id within its university, with its booking and hours")
  void aSessionIsSeenById() {

    Session scheduled =
        Session.schedule(
            UUID.randomUUID(), UPC, UUID.randomUUID(), UUID.randomUUID(), tutor, NINE,
            NINE.plus(Duration.ofHours(2)), NINE.minus(Duration.ofDays(1)));
    when(repository.findByTenantIdAndId(UPC, scheduled.getId())).thenReturn(Optional.of(scheduled));

    SessionView view = inUpc(() -> sessions.requireSession(scheduled.getId()));

    assertThat(view.id()).isEqualTo(scheduled.getId());
    assertThat(view.bookingId()).isEqualTo(scheduled.getBookingId());
    assertThat(view.tutorId()).isEqualTo(tutor);
    assertThat(view.scheduledEnd()).isEqualTo(NINE.plus(Duration.ofHours(2)));
    assertThat(view.status()).isEqualTo(SessionStatus.SCHEDULED);
  }

  @Test
  @DisplayName("a session that does not exist in the university is not found")
  void anUnknownSessionIsNotFound() {

    UUID unknown = UUID.randomUUID();
    when(repository.findByTenantIdAndId(UPC, unknown)).thenReturn(Optional.empty());

    TenantContext.runAs(
        UPC,
        () ->
            assertThatThrownBy(() -> sessions.requireSession(unknown))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining(unknown.toString()));
  }

  @Test
  @DisplayName("a completed session counts the hours that were booked, not the minutes it lasted")
  void aCompletedSessionCountsTheBookedHours() {

    Session completed = mock(Session.class);
    when(completed.getId()).thenReturn(UUID.randomUUID());
    when(completed.getBookingId()).thenReturn(UUID.randomUUID());
    when(completed.getStudentId()).thenReturn(UUID.randomUUID());
    when(completed.getScheduledStart()).thenReturn(NINE);
    when(completed.getScheduledEnd()).thenReturn(NINE.plus(Duration.ofHours(2)));
    when(completed.getStartedAt()).thenReturn(NINE.plus(Duration.ofMinutes(3)));
    // Ended ten minutes early: still the two hours the student paid for.
    when(completed.getEndedAt()).thenReturn(NINE.plus(Duration.ofMinutes(110)));
    when(repository.findByTenantIdAndTutorIdAndStatusOrderByScheduledStartAsc(
            UPC, tutor, SessionStatus.COMPLETED))
        .thenReturn(List.of(completed));

    List<SessionSummary> summaries = inUpc(() -> sessions.completedSessionsOf(tutor));

    assertThat(summaries).singleElement().satisfies(
        summary -> {
          assertThat(summary.sessionId()).isEqualTo(completed.getId());
          assertThat(summary.hours()).isEqualTo(2);
          assertThat(summary.startedAt()).isEqualTo(NINE.plus(Duration.ofMinutes(3)));
          assertThat(summary.endedAt()).isEqualTo(NINE.plus(Duration.ofMinutes(110)));
        });
  }
}
