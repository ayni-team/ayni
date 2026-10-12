package pe.ayni.sessions.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import pe.ayni.booking.BookingApi;
import pe.ayni.booking.BookingStatus;
import pe.ayni.booking.BookingView;
import pe.ayni.sessions.SessionStatus;
import pe.ayni.sessions.domain.model.ParticipantRole;
import pe.ayni.sessions.domain.model.Participation;
import pe.ayni.sessions.domain.model.Session;
import pe.ayni.sessions.infrastructure.ParticipationRepository;
import pe.ayni.sessions.infrastructure.SessionRepository;
import pe.ayni.shared.events.SessionAbandoned;
import pe.ayni.shared.tenancy.TenantContext;

class AbandonTutorNoShowUseCaseTest {

  private static final String TENANT = "UPC";
  private static final Instant START = Instant.parse("2026-09-30T20:00:00Z");
  private static final Instant NOW = START.plus(Duration.ofMinutes(10));
  private static final UUID STUDENT = UUID.randomUUID();
  private static final UUID TUTOR = UUID.randomUUID();
  private static final UUID BOOKING_ID = UUID.randomUUID();
  private static final UUID SESSION_ID = UUID.randomUUID();
  private static final UUID CATALOG_ITEM = UUID.randomUUID();

  private final SessionRepository sessions = mock(SessionRepository.class);
  private final ParticipationRepository participations = mock(ParticipationRepository.class);
  private final BookingApi booking = mock(BookingApi.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private AbandonTutorNoShowUseCase useCase;
  private Session session;

  @BeforeEach
  void setUp() {
    useCase =
        new AbandonTutorNoShowUseCase(
            sessions,
            participations,
            booking,
            events,
            Clock.fixed(NOW, ZoneOffset.UTC));
    session =
        Session.schedule(
            SESSION_ID,
            TENANT,
            BOOKING_ID,
            STUDENT,
            TUTOR,
            START,
            START.plus(Duration.ofHours(1)),
            START.minus(Duration.ofDays(1)));
    when(sessions.lockByTenantIdAndId(TENANT, SESSION_ID)).thenReturn(Optional.of(session));
    when(booking.requireBooking(BOOKING_ID))
        .thenReturn(
            new BookingView(
                BOOKING_ID,
                STUDENT,
                TUTOR,
                CATALOG_ITEM,
                START,
                START.plus(Duration.ofHours(1)),
                1,
                "Limits",
                BookingStatus.CONFIRMED));
  }

  private void checkIn(UUID participant, ParticipantRole role, Instant joinedAt) {
    when(participations.findByTenantIdAndSessionIdAndUserId(TENANT, SESSION_ID, participant))
        .thenReturn(
            Optional.of(
                Participation.arrived(
                    UUID.randomUUID(), TENANT, SESSION_ID, participant, role, joinedAt)));
  }

  private boolean abandon() {
    boolean[] result = new boolean[1];
    TenantContext.runAs(TENANT, () -> result[0] = useCase.abandonFor(SESSION_ID));
    return result[0];
  }

  @Test
  @DisplayName("publishes a tutor no-show event when the student checked in")
  void publishesNoShowWithStudentAttendance() {
    checkIn(STUDENT, ParticipantRole.STUDENT, NOW);

    assertThat(abandon()).isTrue();

    assertThat(session.getStatus()).isEqualTo(SessionStatus.ABANDONED);
    ArgumentCaptor<SessionAbandoned> event = ArgumentCaptor.forClass(SessionAbandoned.class);
    verify(events).publishEvent(event.capture());
    assertThat(event.getValue().tenantId()).isEqualTo(TENANT);
    assertThat(event.getValue().sessionId()).isEqualTo(SESSION_ID);
    assertThat(event.getValue().bookingId()).isEqualTo(BOOKING_ID);
    assertThat(event.getValue().catalogItemId()).isEqualTo(CATALOG_ITEM);
    assertThat(event.getValue().studentCheckedIn()).isTrue();
    assertThat(event.getValue().occurredOn()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("when neither participant checks in, the abandoned event records no student attendance")
  void publishesNoShowForTwoAbsences() {
    assertThat(abandon()).isTrue();

    ArgumentCaptor<SessionAbandoned> event = ArgumentCaptor.forClass(SessionAbandoned.class);
    verify(events).publishEvent(event.capture());
    assertThat(event.getValue().studentCheckedIn()).isFalse();
  }

  @Test
  @DisplayName("a tutor who joined by the deadline is not marked absent without a presence code")
  void tutorCheckInWithoutConfirmedCodePreventsNoShow() {
    checkIn(TUTOR, ParticipantRole.TUTOR, NOW);

    assertThat(abandon()).isFalse();

    assertThat(session.getStatus()).isEqualTo(SessionStatus.SCHEDULED);
    verify(events, never()).publishEvent(any());
  }

  @Test
  @DisplayName("a tutor joining after the deadline is still recorded as absent")
  void tutorJoiningAfterDeadlineDoesNotPreventNoShow() {
    checkIn(TUTOR, ParticipantRole.TUTOR, NOW.plusSeconds(1));

    assertThat(abandon()).isTrue();

    assertThat(session.getStatus()).isEqualTo(SessionStatus.ABANDONED);
    ArgumentCaptor<SessionAbandoned> event = ArgumentCaptor.forClass(SessionAbandoned.class);
    verify(events).publishEvent(event.capture());
    assertThat(event.getValue().studentCheckedIn()).isFalse();
  }

  @Test
  @DisplayName("a session is not abandoned before the ten-minute deadline")
  void waitsUntilNoShowDeadline() {
    when(sessions.lockByTenantIdAndId(TENANT, SESSION_ID)).thenReturn(Optional.of(session));
    useCase =
        new AbandonTutorNoShowUseCase(
            sessions,
            participations,
            booking,
            events,
            Clock.fixed(NOW.minusSeconds(1), ZoneOffset.UTC));

    assertThat(abandon()).isFalse();
    verify(events, never()).publishEvent(any());
  }

  @Test
  @DisplayName("a booking already cancelled is not changed into a no-show")
  void ignoresNonConfirmedBooking() {
    when(booking.requireBooking(BOOKING_ID))
        .thenReturn(
            new BookingView(
                BOOKING_ID,
                STUDENT,
                TUTOR,
                CATALOG_ITEM,
                START,
                START.plus(Duration.ofHours(1)),
                1,
                "Limits",
                BookingStatus.CANCELLED));

    assertThat(abandon()).isFalse();
    assertThat(session.getStatus()).isEqualTo(SessionStatus.SCHEDULED);
    verify(events, never()).publishEvent(any());
  }

  @Test
  @DisplayName("a due sweep uses the current tenant and ten-minute threshold")
  void findsDueSessions() {
    when(sessions.findDueForTutorNoShow(TENANT, START)).thenReturn(List.of(SESSION_ID));

    AtomicReference<List<UUID>> due = new AtomicReference<>();
    TenantContext.runAs(TENANT, () -> due.set(useCase.dueNow()));
    assertThat(due.get()).containsExactly(SESSION_ID);

    verify(sessions).findDueForTutorNoShow(TENANT, START);
  }
}
