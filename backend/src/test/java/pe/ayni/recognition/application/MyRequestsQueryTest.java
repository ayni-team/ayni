package pe.ayni.recognition.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.identity.IdentityApi;
import pe.ayni.recognition.domain.model.RecognitionRequest;
import pe.ayni.recognition.domain.model.RequestedSession;
import pe.ayni.recognition.infrastructure.RecognitionRequestRepository;
import pe.ayni.recognition.infrastructure.RequestedSessionRepository;
import pe.ayni.shared.tenancy.TenantContext;

/** US28, scenario 5: a student reads their own requests with the sessions of each. */
class MyRequestsQueryTest {

  private static final String UPC = "UPC";
  private static final Instant NOW = Instant.parse("2026-10-05T09:00:00Z");
  private static final UUID STUDENT = UUID.randomUUID();

  private final IdentityApi identity = mock(IdentityApi.class);
  private final RecognitionRequestRepository requests = mock(RecognitionRequestRepository.class);
  private final RequestedSessionRepository requestedSessions = mock(RequestedSessionRepository.class);
  private final MyRequestsQuery query = new MyRequestsQuery(identity, requests, requestedSessions);

  private static RequestedSession session(int hours) {
    return RequestedSession.of(UUID.randomUUID(), UPC, hours, UUID.randomUUID(), NOW.minusSeconds(7200), NOW.minusSeconds(3600), null);
  }

  private static RecognitionRequest requestOf(Instant at, RequestedSession... sessions) {
    return RecognitionRequest.submit(UUID.randomUUID(), UPC, STUDENT, List.of(sessions), at);
  }

  private List<RequestFile> mine() {
    AtomicReference<List<RequestFile>> result = new AtomicReference<>();
    TenantContext.runAs(UPC, () -> result.set(query.of(STUDENT)));
    return result.get();
  }

  @Test
  @DisplayName("each request comes with its own sessions, in the order the repository gave them")
  void eachRequestWithItsSessions() {
    RequestedSession a1 = session(10);
    RequestedSession a2 = session(10);
    RequestedSession b1 = session(12);
    RecognitionRequest newer = requestOf(NOW, b1);
    RecognitionRequest older = requestOf(NOW.minusSeconds(86400), a1, a2);
    when(requests.findByTenantIdAndStudentIdOrderBySubmittedAtDesc(UPC, STUDENT)).thenReturn(List.of(newer, older));
    when(requestedSessions.findByRequestIdInOrderByStartedAtAsc(List.of(newer.getId(), older.getId())))
        .thenReturn(List.of(a1, b1, a2));

    List<RequestFile> files = mine();

    assertThat(files).extracting(file -> file.request().getId()).containsExactly(newer.getId(), older.getId());
    assertThat(files.get(0).sessions()).containsExactly(b1);
    assertThat(files.get(1).sessions()).containsExactly(a1, a2);
  }

  @Test
  @DisplayName("a student without requests gets an empty list and no sessions are read")
  void noRequests() {
    when(requests.findByTenantIdAndStudentIdOrderBySubmittedAtDesc(UPC, STUDENT)).thenReturn(List.of());

    assertThat(mine()).isEmpty();

    verifyNoInteractions(requestedSessions);
  }

  @Test
  @DisplayName("a person who is not a user of the university is not found")
  void anUnknownPerson() {
    when(identity.requireUser(STUDENT)).thenThrow(new NoSuchElementException("user not found"));

    assertThatThrownBy(this::mine).isInstanceOf(NoSuchElementException.class);

    verifyNoInteractions(requests);
  }
}
