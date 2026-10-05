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
import pe.ayni.recognition.application.SessionEvidenceQuery.Evidence;
import pe.ayni.recognition.domain.model.NotACoordinator;
import pe.ayni.recognition.domain.model.RecognitionRequest;
import pe.ayni.recognition.domain.model.RequestedSession;
import pe.ayni.recognition.infrastructure.RecognitionRequestRepository;
import pe.ayni.recognition.infrastructure.RequestedSessionRepository;
import pe.ayni.sessions.SessionStatus;
import pe.ayni.sessions.SessionView;
import pe.ayni.sessions.SessionsApi;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.CatalogItemView;
import pe.ayni.skills.CatalogScope;
import pe.ayni.skills.SkillsApi;

/** US30, scenario 2: the evidence of one session and which sessions can be reached through a request. */
class SessionEvidenceQueryTest {

  private static final String UPC = "UPC";
  private static final Instant NOW = Instant.parse("2026-10-05T09:00:00Z");
  private static final UUID COORDINATOR = UUID.randomUUID();
  private static final UUID STUDENT = UUID.randomUUID();
  private static final UUID TUTOR = UUID.randomUUID();
  private static final UUID LEARNER = UUID.randomUUID();

  private final IdentityApi identity = mock(IdentityApi.class);
  private final SessionsApi sessions = mock(SessionsApi.class);
  private final SkillsApi skills = mock(SkillsApi.class);
  private final RecognitionRequestRepository requests = mock(RecognitionRequestRepository.class);
  private final RequestedSessionRepository requestedSessions = mock(RequestedSessionRepository.class);
  private final SessionEvidenceQuery query =
      new SessionEvidenceQuery(new CoordinatorGuard(identity), identity, sessions, skills, requests, requestedSessions);

  private final UUID databases = UUID.randomUUID();
  private final RequestedSession backing =
      RequestedSession.of(UUID.randomUUID(), UPC, 2, databases, NOW.minusSeconds(200000), NOW.minusSeconds(192600), 5);
  private final RecognitionRequest request =
      RecognitionRequest.submit(UUID.randomUUID(), UPC, TUTOR, List.of(backing), NOW.minusSeconds(86400));

  @BeforeEach
  void aSessionThatBacksARequest() {
    when(identity.requireUser(COORDINATOR))
        .thenReturn(new UserView(COORDINATOR, UPC, UserRole.COORDINATOR, "c@upc.edu.pe", null, "Carla Rios", null, null, null));
    when(identity.requireUser(STUDENT))
        .thenReturn(new UserView(STUDENT, UPC, UserRole.STUDENT, "s@upc.edu.pe", "U9", "S", null, null, null));
    when(identity.requireUser(TUTOR))
        .thenReturn(new UserView(TUTOR, UPC, UserRole.STUDENT, "ana@upc.edu.pe", "U202310949", "Ana Torres", null, null, null));
    when(identity.requireUser(LEARNER))
        .thenReturn(new UserView(LEARNER, UPC, UserRole.STUDENT, "luis@upc.edu.pe", "U202110001", "Luis Vega", null, null, null));
    when(requests.findByIdAndTenantId(request.getId(), UPC)).thenReturn(Optional.of(request));
    when(requestedSessions.findById(backing.getSessionId())).thenReturn(Optional.of(backing));
    when(sessions.requireSession(backing.getSessionId()))
        .thenReturn(
            new SessionView(
                backing.getSessionId(), UUID.randomUUID(), LEARNER, TUTOR, NOW.minusSeconds(200000), NOW.minusSeconds(193000), SessionStatus.COMPLETED));
    when(skills.requireItem(databases)).thenReturn(new CatalogItemView(databases, CatalogScope.UNIVERSITY, "Databases", "1ASI0616"));
  }

  private Evidence evidence(UUID asking, UUID requestId, UUID sessionId) {
    AtomicReference<Evidence> result = new AtomicReference<>();
    TenantContext.runAs(UPC, () -> result.set(query.of(asking, requestId, sessionId)));
    return result.get();
  }

  @Test
  @DisplayName("both participants appear with their names, and presence is verified for a completed session")
  void bothParticipantsVerified() {
    Evidence evidence = evidence(COORDINATOR, request.getId(), backing.getSessionId());

    assertThat(evidence.tutor().user().fullName()).isEqualTo("Ana Torres");
    assertThat(evidence.student().user().fullName()).isEqualTo("Luis Vega");
    assertThat(evidence.tutor().presenceVerified()).isTrue();
    assertThat(evidence.student().presenceVerified()).isTrue();
    assertThat(evidence.taught().name()).isEqualTo("Databases");
    assertThat(evidence.session().getStars()).isEqualTo(5);
  }

  @Test
  @DisplayName("presence is not verified when the session did not end completed")
  void notVerifiedWhenNotCompleted() {
    when(sessions.requireSession(backing.getSessionId()))
        .thenReturn(
            new SessionView(
                backing.getSessionId(), UUID.randomUUID(), LEARNER, TUTOR, NOW.minusSeconds(200000), NOW.minusSeconds(193000), SessionStatus.UNVERIFIED));

    Evidence evidence = evidence(COORDINATOR, request.getId(), backing.getSessionId());

    assertThat(evidence.tutor().presenceVerified()).isFalse();
    assertThat(evidence.student().presenceVerified()).isFalse();
  }

  @Test
  @DisplayName("a session that does not back this request is not found through it")
  void aSessionOfAnotherRequestIsNotFound() {
    RequestedSession foreign =
        RequestedSession.of(UUID.randomUUID(), UPC, 1, databases, NOW.minusSeconds(5000), NOW.minusSeconds(1400), null);
    RecognitionRequest other = RecognitionRequest.submit(UUID.randomUUID(), UPC, TUTOR, List.of(foreign), NOW);
    when(requestedSessions.findById(foreign.getSessionId())).thenReturn(Optional.of(foreign));

    assertThatThrownBy(() -> evidence(COORDINATOR, request.getId(), foreign.getSessionId()))
        .isInstanceOf(NoSuchElementException.class);
    assertThat(other.getId()).isNotEqualTo(request.getId());
    verifyNoInteractions(sessions);
  }

  @Test
  @DisplayName("a session that backs no request is not found")
  void aSessionOfNoRequestIsNotFound() {
    UUID stranger = UUID.randomUUID();
    when(requestedSessions.findById(stranger)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> evidence(COORDINATOR, request.getId(), stranger)).isInstanceOf(NoSuchElementException.class);
  }

  @Test
  @DisplayName("a request of another university is not found")
  void anotherUniversity() {
    UUID elsewhere = UUID.randomUUID();
    when(requests.findByIdAndTenantId(elsewhere, UPC)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> evidence(COORDINATOR, elsewhere, backing.getSessionId()))
        .isInstanceOf(NoSuchElementException.class);

    verifyNoInteractions(sessions, skills);
  }

  @Test
  @DisplayName("a student cannot read the evidence, not even of their own session")
  void aStudentCannotRead() {
    assertThatThrownBy(() -> evidence(STUDENT, request.getId(), backing.getSessionId())).isInstanceOf(NotACoordinator.class);

    verify(requests, never()).findByIdAndTenantId(any(), any());
    verifyNoInteractions(sessions);
  }
}
