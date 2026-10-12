package pe.ayni.recognition.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;
import pe.ayni.recognition.application.RequestQueueQuery.QueuePage;
import pe.ayni.recognition.domain.model.NotACoordinator;
import pe.ayni.recognition.domain.model.RecognitionRequest;
import pe.ayni.recognition.domain.model.RequestStatus;
import pe.ayni.recognition.domain.model.RequestedSession;
import pe.ayni.recognition.infrastructure.RecognitionRequestRepository;
import pe.ayni.shared.tenancy.TenantContext;

/** US29, scenario 1: what the queue shows a coordinator and who may read it. */
class RequestQueueQueryTest {

  private static final String UPC = "UPC";
  private static final Instant NOW = Instant.parse("2026-10-05T09:00:00Z");
  private static final UUID COORDINATOR = UUID.randomUUID();
  private static final UUID STUDENT = UUID.randomUUID();
  private static final UUID ANA = UUID.randomUUID();
  private static final UUID LUIS = UUID.randomUUID();

  private final IdentityApi identity = mock(IdentityApi.class);
  private final RecognitionRequestRepository requests = mock(RecognitionRequestRepository.class);
  private final RequestQueueQuery query =
      new RequestQueueQuery(new CoordinatorGuard(identity), identity, requests);

  @BeforeEach
  void aCoordinatorAndTwoStudents() {
    when(identity.requireUser(COORDINATOR))
        .thenReturn(new UserView(COORDINATOR, UPC, UserRole.COORDINATOR, "c@upc.edu.pe", null, "Carla Rios", null, null, null));
    when(identity.requireUser(STUDENT))
        .thenReturn(new UserView(STUDENT, UPC, UserRole.STUDENT, "s@upc.edu.pe", "U1", "S", null, null, null));
    when(identity.requireUser(ANA))
        .thenReturn(new UserView(ANA, UPC, UserRole.STUDENT, "ana@upc.edu.pe", "U202310949", "Ana Torres", null, null, null));
    when(identity.requireUser(LUIS))
        .thenReturn(new UserView(LUIS, UPC, UserRole.STUDENT, "luis@upc.edu.pe", "U202110001", "Luis Vega", null, null, null));
  }

  private static RecognitionRequest requestOf(UUID student, Instant at) {
    RequestedSession session =
        RequestedSession.of(UUID.randomUUID(), UPC, 20, UUID.randomUUID(), NOW.minusSeconds(7200), NOW.minusSeconds(3600), null);
    return RecognitionRequest.submit(UUID.randomUUID(), UPC, student, List.of(session), at);
  }

  private QueuePage queue(UUID asking, RequestStatus status, int page, int size) {
    AtomicReference<QueuePage> result = new AtomicReference<>();
    TenantContext.runAs(UPC, () -> result.set(query.of(asking, status, page, size)));
    return result.get();
  }

  private Pageable asked() {
    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(requests).findByTenantIdAndStatusInOrderBySubmittedAtAsc(eq(UPC), any(), pageable.capture());
    return pageable.getValue();
  }

  @Test
  @DisplayName("each request comes with the student's name and code, and the hours it was submitted with")
  void eachRequestWithItsStudent() {
    RecognitionRequest first = requestOf(ANA, NOW.minusSeconds(86400 * 3));
    RecognitionRequest second = requestOf(LUIS, NOW.minusSeconds(86400));
    Page<RecognitionRequest> page = new PageImpl<>(List.of(first, second), PageRequest.of(0, 20), 2);
    when(requests.findByTenantIdAndStatusInOrderBySubmittedAtAsc(eq(UPC), any(), any())).thenReturn(page);

    QueuePage queue = queue(COORDINATOR, null, 0, 20);

    assertThat(queue.items())
        .extracting(item -> item.student().fullName(), item -> item.student().studentCode(), item -> item.request().getTotalHours())
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("Ana Torres", "U202310949", 20),
            org.assertj.core.groups.Tuple.tuple("Luis Vega", "U202110001", 20));
    assertThat(queue.total()).isEqualTo(2);
  }

  @Test
  @DisplayName("without a state it shows what waits: submitted and under review")
  void byDefaultWhatWaits() {
    when(requests.findByTenantIdAndStatusInOrderBySubmittedAtAsc(eq(UPC), any(), any()))
        .thenReturn(Page.empty());

    queue(COORDINATOR, null, 0, 20);

    ArgumentCaptor<java.util.Collection<RequestStatus>> states = ArgumentCaptor.forClass(java.util.Collection.class);
    verify(requests).findByTenantIdAndStatusInOrderBySubmittedAtAsc(eq(UPC), states.capture(), any());
    assertThat(Set.copyOf(states.getValue())).containsExactlyInAnyOrder(RequestStatus.SUBMITTED, RequestStatus.UNDER_REVIEW);
  }

  @Test
  @DisplayName("with a state it shows only that one")
  void aGivenState() {
    when(requests.findByTenantIdAndStatusInOrderBySubmittedAtAsc(eq(UPC), any(), any()))
        .thenReturn(Page.empty());

    queue(COORDINATOR, RequestStatus.REJECTED, 0, 20);

    ArgumentCaptor<java.util.Collection<RequestStatus>> states = ArgumentCaptor.forClass(java.util.Collection.class);
    verify(requests).findByTenantIdAndStatusInOrderBySubmittedAtAsc(eq(UPC), states.capture(), any());
    assertThat(Set.copyOf(states.getValue())).containsExactly(RequestStatus.REJECTED);
  }

  @Test
  @DisplayName("a page of more than a hundred, or less than one, is brought within limits")
  void pageSizeIsClamped() {
    when(requests.findByTenantIdAndStatusInOrderBySubmittedAtAsc(eq(UPC), any(), any()))
        .thenReturn(Page.empty());

    queue(COORDINATOR, null, -3, 5000);
    assertThat(asked()).isEqualTo(PageRequest.of(0, 100));
  }

  @Test
  @DisplayName("a student with two requests is looked up once")
  void aStudentIsLookedUpOnce() {
    Page<RecognitionRequest> page =
        new PageImpl<>(List.of(requestOf(ANA, NOW.minusSeconds(200)), requestOf(ANA, NOW.minusSeconds(100))));
    when(requests.findByTenantIdAndStatusInOrderBySubmittedAtAsc(eq(UPC), any(), any())).thenReturn(page);

    queue(COORDINATOR, null, 0, 20);

    verify(identity, times(1)).requireUser(ANA);
  }

  @Test
  @DisplayName("a student cannot read the queue and nothing is looked up")
  void aStudentCannotRead() {
    assertThatThrownBy(() -> queue(STUDENT, null, 0, 20)).isInstanceOf(NotACoordinator.class);

    verify(requests, never()).findByTenantIdAndStatusInOrderBySubmittedAtAsc(any(), any(), any());
  }

  @Test
  @DisplayName("a person who is not a user of the university is not found")
  void anUnknownPerson() {
    UUID stranger = UUID.randomUUID();
    when(identity.requireUser(stranger)).thenThrow(new NoSuchElementException("user not found"));

    assertThatThrownBy(() -> queue(stranger, null, 0, 20)).isInstanceOf(NoSuchElementException.class);
  }
}
