package pe.ayni.recognition.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;
import pe.ayni.recognition.application.ResolveRecognitionRequestUseCase.Decision;
import pe.ayni.recognition.domain.model.NotACoordinator;
import pe.ayni.recognition.domain.model.RecognitionRequest;
import pe.ayni.recognition.domain.model.RecognitionRuleViolation;
import pe.ayni.recognition.domain.model.RecognitionStateConflict;
import pe.ayni.recognition.domain.model.RequestStatus;
import pe.ayni.recognition.domain.model.RequestedSession;
import pe.ayni.recognition.infrastructure.RecognitionRequestRepository;
import pe.ayni.shared.events.RecognitionResolved;
import pe.ayni.shared.tenancy.TenantContext;

/** US29, scenario 4: what deciding does, what it announces and what stops it. */
class ResolveRecognitionRequestUseCaseTest {

  private static final String UPC = "UPC";
  private static final Instant NOW = Instant.parse("2026-10-05T09:00:00Z");
  private static final UUID COORDINATOR = UUID.randomUUID();
  private static final UUID STUDENT = UUID.randomUUID();
  private static final UUID ANA = UUID.randomUUID();

  private final IdentityApi identity = mock(IdentityApi.class);
  private final RecognitionRequestRepository requests = mock(RecognitionRequestRepository.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final ResolveRecognitionRequestUseCase useCase =
      new ResolveRecognitionRequestUseCase(
          new CoordinatorGuard(identity), requests, events, Clock.fixed(NOW.plusSeconds(86400), ZoneOffset.UTC));

  private RecognitionRequest request;

  @BeforeEach
  void aRequestWaiting() {
    when(identity.requireUser(COORDINATOR))
        .thenReturn(new UserView(COORDINATOR, UPC, UserRole.COORDINATOR, "c@upc.edu.pe", null, "Carla Rios", null, null, null));
    when(identity.requireUser(STUDENT))
        .thenReturn(new UserView(STUDENT, UPC, UserRole.STUDENT, "s@upc.edu.pe", "U1", "S", null, null, null));
    RequestedSession session =
        RequestedSession.of(UUID.randomUUID(), UPC, 20, UUID.randomUUID(), NOW.minusSeconds(7200), NOW.minusSeconds(3600), null);
    request = RecognitionRequest.submit(UUID.randomUUID(), UPC, ANA, List.of(session), NOW);
    when(requests.lockByIdAndTenantId(request.getId(), UPC)).thenReturn(Optional.of(request));
  }

  private RecognitionRequest decide(UUID asking, Decision decision, String reason) {
    AtomicReference<RecognitionRequest> result = new AtomicReference<>();
    TenantContext.runAs(UPC, () -> result.set(useCase.execute(asking, request.getId(), decision, reason)));
    return result.get();
  }

  @Test
  @DisplayName("approving records the decision and announces it to the student with the reason")
  void approving() {
    RecognitionRequest decided = decide(COORDINATOR, Decision.APPROVE, "The hours match the programme");

    assertThat(decided.getStatus()).isEqualTo(RequestStatus.APPROVED);
    assertThat(decided.getReviewedBy()).isEqualTo(COORDINATOR);
    assertThat(decided.getReviewedAt()).isEqualTo(NOW.plusSeconds(86400));
    ArgumentCaptor<RecognitionResolved> event = ArgumentCaptor.forClass(RecognitionResolved.class);
    verify(events).publishEvent(event.capture());
    assertThat(event.getValue().approved()).isTrue();
    assertThat(event.getValue().reason()).isEqualTo("The hours match the programme");
    assertThat(event.getValue().studentId()).isEqualTo(ANA);
    assertThat(event.getValue().requestId()).isEqualTo(request.getId());
    assertThat(event.getValue().tenantId()).isEqualTo(UPC);
  }

  @Test
  @DisplayName("rejecting records the decision and announces it as not approved")
  void rejecting() {
    decide(COORDINATOR, Decision.REJECT, "The sessions are not related to the programme");

    assertThat(request.getStatus()).isEqualTo(RequestStatus.REJECTED);
    ArgumentCaptor<RecognitionResolved> event = ArgumentCaptor.forClass(RecognitionResolved.class);
    verify(events).publishEvent(event.capture());
    assertThat(event.getValue().approved()).isFalse();
  }

  @Test
  @DisplayName("without a reason nothing is decided and nothing is announced")
  void withoutAReason() {
    assertThatThrownBy(() -> decide(COORDINATOR, Decision.APPROVE, " ")).isInstanceOf(RecognitionRuleViolation.class);

    assertThat(request.getStatus()).isEqualTo(RequestStatus.SUBMITTED);
    verifyNoInteractions(events);
  }

  @Test
  @DisplayName("a request already decided is a conflict and keeps its first decision")
  void alreadyDecided() {
    decide(COORDINATOR, Decision.APPROVE, "Fine");

    assertThatThrownBy(() -> decide(COORDINATOR, Decision.REJECT, "Changed my mind"))
        .isInstanceOf(RecognitionStateConflict.class);

    assertThat(request.getStatus()).isEqualTo(RequestStatus.APPROVED);
    assertThat(request.getDecisionReason()).isEqualTo("Fine");
  }

  @Test
  @DisplayName("a request of another university is not found")
  void anotherUniversity() {
    UUID elsewhere = UUID.randomUUID();
    when(requests.lockByIdAndTenantId(elsewhere, UPC)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                TenantContext.runAs(UPC, () -> useCase.execute(COORDINATOR, elsewhere, Decision.APPROVE, "Fine")))
        .isInstanceOf(NoSuchElementException.class);
  }

  @Test
  @DisplayName("a student cannot decide, not even their own request, and nothing is read or announced")
  void aStudentCannotDecide() {
    assertThatThrownBy(() -> decide(STUDENT, Decision.APPROVE, "I deserve it")).isInstanceOf(NotACoordinator.class);

    assertThat(request.getStatus()).isEqualTo(RequestStatus.SUBMITTED);
    verifyNoInteractions(events);
  }
}
