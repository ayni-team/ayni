package pe.ayni.recognition.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;
import pe.ayni.recognition.application.RequestCaseQuery.Case;
import pe.ayni.recognition.application.SessionAlerts.Alert;
import pe.ayni.recognition.domain.model.NotACoordinator;
import pe.ayni.recognition.domain.model.RecognitionRequest;
import pe.ayni.recognition.domain.model.RequestedSession;
import pe.ayni.recognition.infrastructure.RecognitionRequestRepository;
import pe.ayni.recognition.infrastructure.RequestedSessionRepository;
import pe.ayni.shared.tenancy.TenantContext;

/** US29, scenarios 2 and 3: what a coordinator sees when they open a request. */
class RequestCaseQueryTest {

  private static final String UPC = "UPC";
  private static final Instant NOW = Instant.parse("2026-10-05T09:00:00Z");
  private static final UUID COORDINATOR = UUID.randomUUID();
  private static final UUID STUDENT = UUID.randomUUID();
  private static final UUID ANA = UUID.randomUUID();

  private final IdentityApi identity = mock(IdentityApi.class);
  private final RecognitionRequestRepository requests = mock(RecognitionRequestRepository.class);
  private final RequestedSessionRepository requestedSessions = mock(RequestedSessionRepository.class);
  private final SessionAlerts alerts = mock(SessionAlerts.class);
  private final RequestCaseQuery query =
      new RequestCaseQuery(new CoordinatorGuard(identity), identity, requests, requestedSessions, alerts);

  private final RequestedSession first =
      RequestedSession.of(UUID.randomUUID(), UPC, 10, UUID.randomUUID(), NOW.minusSeconds(200000), NOW.minusSeconds(196000), 5);
  private final RequestedSession second =
      RequestedSession.of(UUID.randomUUID(), UPC, 10, UUID.randomUUID(), NOW.minusSeconds(100000), NOW.minusSeconds(96000), null);
  private final RecognitionRequest request =
      RecognitionRequest.submit(UUID.randomUUID(), UPC, ANA, List.of(first, second), NOW.minusSeconds(86400));

  @BeforeEach
  void aCaseWithTwoSessions() {
    when(identity.requireUser(COORDINATOR))
        .thenReturn(new UserView(COORDINATOR, UPC, UserRole.COORDINATOR, "c@upc.edu.pe", null, "Carla Rios", null, null, null));
    when(identity.requireUser(STUDENT))
        .thenReturn(new UserView(STUDENT, UPC, UserRole.STUDENT, "s@upc.edu.pe", "U1", "S", null, null, null));
    when(identity.requireUser(ANA))
        .thenReturn(new UserView(ANA, UPC, UserRole.STUDENT, "ana@upc.edu.pe", "U202310949", "Ana Torres", null, null, null));
    when(requests.findByIdAndTenantId(request.getId(), UPC)).thenReturn(Optional.of(request));
    when(requestedSessions.findByRequestIdOrderByStartedAtAsc(request.getId())).thenReturn(List.of(first, second));
    when(alerts.alertsOf(any())).thenReturn(Map.of());
  }

  private Case open(UUID asking, UUID requestId) {
    AtomicReference<Case> result = new AtomicReference<>();
    TenantContext.runAs(UPC, () -> result.set(query.of(asking, requestId)));
    return result.get();
  }

  @Test
  @DisplayName("the case has the student and the sessions as they were submitted")
  void theCaseAsSubmitted() {
    Case file = open(COORDINATOR, request.getId());

    assertThat(file.student().fullName()).isEqualTo("Ana Torres");
    assertThat(file.request().getTotalHours()).isEqualTo(20);
    assertThat(file.sessions()).extracting(reviewed -> reviewed.session().getSessionId())
        .containsExactly(first.getSessionId(), second.getSessionId());
    assertThat(file.sessions()).allSatisfy(reviewed -> assertThat(reviewed.alerts()).isEmpty());
  }

  @Test
  @DisplayName("the alerts of the audit are put next to the session they are about")
  void alertsNextToTheirSession() {
    Alert alert = new Alert("SHORT_SESSIONS", "HIGH", "Sessions ended within ten minutes");
    when(alerts.alertsOf(List.of(first.getSessionId(), second.getSessionId())))
        .thenReturn(Map.of(second.getSessionId(), List.of(alert)));

    Case file = open(COORDINATOR, request.getId());

    assertThat(file.sessions().get(0).alerts()).isEmpty();
    assertThat(file.sessions().get(1).alerts()).containsExactly(alert);
  }

  @Test
  @DisplayName("a request of another university is not found, and nothing else is read")
  void anotherUniversitysRequestIsNotFound() {
    UUID elsewhere = UUID.randomUUID();
    when(requests.findByIdAndTenantId(elsewhere, UPC)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> open(COORDINATOR, elsewhere)).isInstanceOf(NoSuchElementException.class);

    verifyNoInteractions(requestedSessions, alerts);
  }

  @Test
  @DisplayName("a student cannot open a request, not even their own, and nothing is read")
  void aStudentCannotOpenIt() {
    assertThatThrownBy(() -> open(STUDENT, request.getId())).isInstanceOf(NotACoordinator.class);

    verify(requests, never()).findByIdAndTenantId(any(), any());
  }
}
