package pe.ayni.recognition.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import pe.ayni.recognition.application.SupportingSessionsQuery.Support;
import pe.ayni.recognition.domain.model.NotACoordinator;
import pe.ayni.recognition.domain.model.RecognitionRequest;
import pe.ayni.recognition.domain.model.RequestedSession;
import pe.ayni.recognition.infrastructure.RecognitionRequestRepository;
import pe.ayni.recognition.infrastructure.RequestedSessionRepository;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.CatalogItemView;
import pe.ayni.skills.CatalogScope;
import pe.ayni.skills.SkillsApi;

/** US30, scenarios 1 and 3: the list of sessions behind a request and what was taught in each. */
class SupportingSessionsQueryTest {

  private static final String UPC = "UPC";
  private static final Instant NOW = Instant.parse("2026-10-05T09:00:00Z");
  private static final UUID COORDINATOR = UUID.randomUUID();
  private static final UUID STUDENT = UUID.randomUUID();

  private final IdentityApi identity = mock(IdentityApi.class);
  private final RecognitionRequestRepository requests = mock(RecognitionRequestRepository.class);
  private final RequestedSessionRepository requestedSessions = mock(RequestedSessionRepository.class);
  private final SkillsApi skills = mock(SkillsApi.class);
  private final SupportingSessionsQuery query =
      new SupportingSessionsQuery(new CoordinatorGuard(identity), requests, requestedSessions, skills);

  private final UUID databases = UUID.randomUUID();
  private final UUID figma = UUID.randomUUID();
  private final RequestedSession first =
      RequestedSession.of(UUID.randomUUID(), UPC, 12, databases, NOW.minusSeconds(200000), NOW.minusSeconds(150000), null);
  private final RequestedSession second =
      RequestedSession.of(UUID.randomUUID(), UPC, 8, figma, NOW.minusSeconds(100000), NOW.minusSeconds(60000), 4);
  private final RecognitionRequest request =
      RecognitionRequest.submit(UUID.randomUUID(), UPC, UUID.randomUUID(), List.of(first, second), NOW.minusSeconds(86400));

  @BeforeEach
  void aRequestWithTwoSessions() {
    when(identity.requireUser(COORDINATOR))
        .thenReturn(new UserView(COORDINATOR, UPC, UserRole.COORDINATOR, "c@upc.edu.pe", null, "Carla Rios", null, null, null));
    when(identity.requireUser(STUDENT))
        .thenReturn(new UserView(STUDENT, UPC, UserRole.STUDENT, "s@upc.edu.pe", "U1", "S", null, null, null));
    when(requests.findByIdAndTenantId(request.getId(), UPC)).thenReturn(Optional.of(request));
    when(requestedSessions.findByRequestIdOrderByStartedAtAsc(request.getId())).thenReturn(List.of(first, second));
    when(skills.requireItem(databases)).thenReturn(new CatalogItemView(databases, CatalogScope.UNIVERSITY, "Databases", "1ASI0616"));
    when(skills.requireItem(figma)).thenReturn(new CatalogItemView(figma, CatalogScope.GLOBAL, "Figma", null));
  }

  private Support support(UUID asking, UUID requestId) {
    AtomicReference<Support> result = new AtomicReference<>();
    TenantContext.runAs(UPC, () -> result.set(query.of(asking, requestId)));
    return result.get();
  }

  @Test
  @DisplayName("each session comes with the course or skill that was taught, in the order they were taught")
  void eachSessionWithWhatWasTaught() {
    Support support = support(COORDINATOR, request.getId());

    assertThat(support.sessions())
        .extracting(supporting -> supporting.session().getSessionId(), supporting -> supporting.taught().name())
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(first.getSessionId(), "Databases"),
            org.assertj.core.groups.Tuple.tuple(second.getSessionId(), "Figma"));
  }

  @Test
  @DisplayName("the hours of the sessions add up to the total of the request")
  void theHoursAddUp() {
    Support support = support(COORDINATOR, request.getId());

    assertThat(support.sessions().stream().mapToInt(supporting -> supporting.session().getHours()).sum())
        .isEqualTo(support.request().getTotalHours())
        .isEqualTo(20);
  }

  @Test
  @DisplayName("a skill taught in several sessions is looked up once")
  void aSkillIsLookedUpOnce() {
    RequestedSession again =
        RequestedSession.of(UUID.randomUUID(), UPC, 1, databases, NOW.minusSeconds(50000), NOW.minusSeconds(46000), null);
    when(requestedSessions.findByRequestIdOrderByStartedAtAsc(request.getId())).thenReturn(List.of(first, second, again));

    support(COORDINATOR, request.getId());

    verify(skills, times(1)).requireItem(databases);
  }

  @Test
  @DisplayName("a request of another university is not found and nothing else is read")
  void anotherUniversity() {
    UUID elsewhere = UUID.randomUUID();
    when(requests.findByIdAndTenantId(elsewhere, UPC)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> support(COORDINATOR, elsewhere)).isInstanceOf(NoSuchElementException.class);

    verifyNoInteractions(requestedSessions, skills);
  }

  @Test
  @DisplayName("a student cannot read the sessions of a request")
  void aStudentCannotRead() {
    assertThatThrownBy(() -> support(STUDENT, request.getId())).isInstanceOf(NotACoordinator.class);

    verify(requests, never()).findByIdAndTenantId(any(), any());
  }
}
