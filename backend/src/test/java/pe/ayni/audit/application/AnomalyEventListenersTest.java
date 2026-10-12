package pe.ayni.audit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.audit.domain.model.Anomaly;
import pe.ayni.audit.domain.model.AnomalyKind;
import pe.ayni.audit.domain.model.AnomalySession;
import pe.ayni.audit.domain.model.Severity;
import pe.ayni.audit.infrastructure.AnomalyRepository;
import pe.ayni.audit.infrastructure.AnomalySessionRepository;
import pe.ayni.sessions.SessionSummary;
import pe.ayni.sessions.SessionsApi;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.events.SessionCompleted;
import pe.ayni.shared.events.SessionUnverified;

/** What each listener raises, with the real use case over repositories that only collect. */
class AnomalyEventListenersTest {

  private static final Instant AT = Instant.parse("2026-10-12T15:00:00Z");
  private static final Instant LATER = Instant.parse("2026-10-12T16:30:00Z");

  private final UUID tutor = UUID.randomUUID();
  private final UUID student = UUID.randomUUID();
  private final UUID session = UUID.randomUUID();

  private final List<Anomaly> raised = new ArrayList<>();
  private final List<AnomalySession> supporting = new ArrayList<>();
  private SessionsApi sessions;
  private AnomalyEventListeners listeners;

  @BeforeEach
  void listenersOverCollectingRepositories() {
    AnomalyRepository anomalies = mock(AnomalyRepository.class);
    when(anomalies.save(any(Anomaly.class)))
        .thenAnswer(
            call -> {
              raised.add(call.getArgument(0));
              return call.getArgument(0);
            });
    when(anomalies.existsById(any(UUID.class)))
        .thenAnswer(call -> raised.stream().anyMatch(found -> found.getId().equals(call.getArgument(0))));
    AnomalySessionRepository linked = mock(AnomalySessionRepository.class);
    when(linked.save(any(AnomalySession.class)))
        .thenAnswer(
            call -> {
              supporting.add(call.getArgument(0));
              return call.getArgument(0);
            });
    sessions = mock(SessionsApi.class);
    DetectSessionAnomaliesUseCase detect =
        new DetectSessionAnomaliesUseCase(anomalies, linked, sessions, Clock.fixed(LATER, ZoneOffset.UTC), 0.5);
    listeners = new AnomalyEventListeners(detect);
  }

  private SessionUnverified unverified(List<UUID> unconfirmed) {
    return new SessionUnverified("UPC", session, UUID.randomUUID(), tutor, student, unconfirmed, AT);
  }

  private SessionCompleted completed() {
    return new SessionCompleted("UPC", session, UUID.randomUUID(), tutor, student, UUID.randomUUID(), Credits.of(1), AT);
  }

  private void sessionLasted(long minutes, int hours) {
    when(sessions.completedSessionsOf(tutor))
        .thenReturn(
            List.of(new SessionSummary(session, UUID.randomUUID(), student, AT, AT.plusSeconds(minutes * 60), hours)));
  }

  @Test
  @DisplayName("A session that was not verified raises one alert about its tutor, with the student")
  void failedPresence() {
    listeners.on(unverified(List.of(student)));

    assertThat(raised).hasSize(1);
    Anomaly anomaly = raised.get(0);
    assertThat(anomaly.getKind()).isEqualTo(AnomalyKind.FAILED_PRESENCE);
    assertThat(anomaly.getTenantId()).isEqualTo("UPC");
    assertThat(anomaly.getSubjectId()).isEqualTo(tutor);
    assertThat(anomaly.getSeverity()).isEqualTo(Severity.MEDIUM);
    assertThat(anomaly.getDetectedAt()).isEqualTo(LATER);
    assertThat(anomaly.getDetail()).containsEntry("studentId", student.toString()).containsEntry("unconfirmedUserIds", student.toString());
    assertThat(supporting).extracting(AnomalySession::getSessionId).containsExactly(session);
    assertThat(supporting).extracting(AnomalySession::getAnomalyId).containsExactly(anomaly.getId());
  }

  @Test
  @DisplayName("The same event delivered twice raises one alert")
  void deliveredTwice() {
    listeners.on(unverified(List.of(student)));
    listeners.on(unverified(List.of(student)));

    assertThat(raised).hasSize(1);
    assertThat(supporting).hasSize(1);
  }

  @Test
  @DisplayName("A session that lasted a small fraction of the hour raises an alert about its duration")
  void shortDuration() {
    sessionLasted(12, 1);

    listeners.on(completed());

    assertThat(raised).hasSize(1);
    assertThat(raised.get(0).getKind()).isEqualTo(AnomalyKind.SHORT_DURATION);
    assertThat(raised.get(0).getSeverity()).isEqualTo(Severity.HIGH);
    assertThat(raised.get(0).getDetail()).containsEntry("bookedMinutes", "60").containsEntry("actualMinutes", "12");
  }

  @Test
  @DisplayName("The hours booked set what a session should have lasted")
  void bookedHours() {
    sessionLasted(50, 2);

    listeners.on(completed());

    assertThat(raised).hasSize(1);
    assertThat(raised.get(0).getDetail()).containsEntry("bookedMinutes", "120");
  }

  @Test
  @DisplayName("A session that lasted enough, or that sessions cannot measure, raises nothing")
  void nothingToRaise() {
    sessionLasted(30, 1);
    listeners.on(completed());

    when(sessions.completedSessionsOf(tutor)).thenReturn(List.of());
    listeners.on(completed());

    when(sessions.completedSessionsOf(tutor))
        .thenReturn(List.of(new SessionSummary(session, UUID.randomUUID(), student, null, null, 1)));
    listeners.on(completed());

    assertThat(raised).isEmpty();
  }

  @Test
  @DisplayName("An event that names nobody is left alone without a word")
  void incompleteEvents() {
    assertThatCode(
            () -> {
              listeners.on(new SessionUnverified("UPC", session, UUID.randomUUID(), null, null, null, AT));
              listeners.on(new SessionCompleted("UPC", null, null, null, null, null, null, AT));
            })
        .doesNotThrowAnyException();

    assertThat(raised).isEmpty();
    verify(sessions, never()).completedSessionsOf(any());
  }

  @Test
  @DisplayName("A failure to detect does not reach whoever published the event")
  void aFailureIsSwallowed() {
    when(sessions.completedSessionsOf(tutor)).thenThrow(new IllegalStateException("sessions is down"));

    assertThatCode(() -> listeners.on(completed())).doesNotThrowAnyException();
    assertThat(raised).isEmpty();
  }
}
