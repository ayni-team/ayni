package pe.ayni.recognition.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
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
import pe.ayni.identity.IdentityApi;
import pe.ayni.recognition.domain.model.InsufficientHours;
import pe.ayni.recognition.domain.model.RecognitionRequest;
import pe.ayni.recognition.domain.model.RecognitionRule;
import pe.ayni.recognition.domain.model.RecognitionStateConflict;
import pe.ayni.recognition.domain.model.RequestStatus;
import pe.ayni.recognition.domain.model.RequestedSession;
import pe.ayni.recognition.infrastructure.RecognitionRequestRepository;
import pe.ayni.recognition.infrastructure.RecognitionRuleRepository;
import pe.ayni.recognition.infrastructure.RequestedSessionRepository;
import pe.ayni.sessions.SessionSummary;
import pe.ayni.sessions.SessionsApi;
import pe.ayni.shared.events.RecognitionRequested;
import pe.ayni.shared.tenancy.TenantContext;

/** US28: which sessions back a request, what it copies, and what stops it. */
class SubmitRecognitionRequestUseCaseTest {

  private static final String UPC = "UPC";
  private static final Instant NOW = Instant.parse("2026-10-05T09:00:00Z");
  private static final UUID STUDENT = UUID.randomUUID();

  private final IdentityApi identity = mock(IdentityApi.class);
  private final BookingApi booking = mock(BookingApi.class);
  private final SessionsApi sessions = mock(SessionsApi.class);
  private final SessionRatings ratings = mock(SessionRatings.class);
  private final RecognitionRuleRepository rules = mock(RecognitionRuleRepository.class);
  private final RecognitionRequestRepository requests = mock(RecognitionRequestRepository.class);
  private final RequestedSessionRepository requestedSessions = mock(RequestedSessionRepository.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final SubmitRecognitionRequestUseCase useCase =
      new SubmitRecognitionRequestUseCase(
          identity,
          booking,
          new UnclaimedSessions(sessions, requestedSessions),
          ratings,
          rules,
          requests,
          requestedSessions,
          events,
          Clock.fixed(NOW, ZoneOffset.UTC));

  private final UUID skill = UUID.randomUUID();

  @BeforeEach
  void aRuleOfTwentyHours() {
    when(rules.findInForce(UPC, LocalDate.of(2026, 10, 5)))
        .thenReturn(
            Optional.of(new RecognitionRule(UUID.randomUUID(), UPC, 20, null, LocalDate.of(2026, 1, 1), NOW)));
    when(requests.save(any(RecognitionRequest.class))).thenAnswer(call -> call.getArgument(0));
    when(ratings.starsOf(any())).thenReturn(Map.of());
    when(booking.requireBooking(any(UUID.class)))
        .thenAnswer(
            call ->
                new BookingView(
                    call.getArgument(0), STUDENT, UUID.randomUUID(), skill, NOW, NOW, 1, "need", BookingStatus.COMPLETED));
  }

  private int counter;

  private SessionSummary session(int hours) {
    Instant start = NOW.minusSeconds(86400L * (100 - counter++));
    return new SessionSummary(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), start, start.plusSeconds(3600L * hours), hours);
  }

  private RequestFile submit() {
    AtomicReference<RequestFile> result = new AtomicReference<>();
    TenantContext.runAs(UPC, () -> result.set(useCase.execute(STUDENT)));
    return result.get();
  }

  @Test
  @DisplayName("the request is registered with the total of hours, the sessions that back it and the skill of each")
  void theRequestIsRegistered() {
    SessionSummary first = session(10);
    SessionSummary second = session(10);
    when(sessions.completedSessionsOf(STUDENT)).thenReturn(List.of(first, second));

    RequestFile submitted = submit();

    RecognitionRequest request = submitted.request();
    assertThat(request.getStatus()).isEqualTo(RequestStatus.SUBMITTED);
    assertThat(request.getStudentId()).isEqualTo(STUDENT);
    assertThat(request.getTotalHours()).isEqualTo(20);
    assertThat(request.getSessionsCount()).isEqualTo(2);
    assertThat(submitted.sessions())
        .extracting(RequestedSession::getSessionId, RequestedSession::getCatalogItemId)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(first.sessionId(), skill),
            org.assertj.core.groups.Tuple.tuple(second.sessionId(), skill));
    verify(requestedSessions).saveAll(submitted.sessions());
    verify(requests).lockStudent(UPC, STUDENT);
  }

  @Test
  @DisplayName("the ratings of the sessions are copied and averaged")
  void theRatingsAreCopied() {
    SessionSummary first = session(10);
    SessionSummary second = session(10);
    when(sessions.completedSessionsOf(STUDENT)).thenReturn(List.of(first, second));
    when(ratings.starsOf(List.of(first.sessionId(), second.sessionId())))
        .thenReturn(Map.of(first.sessionId(), 5, second.sessionId(), 4));

    RequestFile submitted = submit();

    assertThat(submitted.request().getAverageRating()).isEqualByComparingTo(new BigDecimal("4.50"));
    assertThat(submitted.sessions()).extracting(RequestedSession::getStars).containsExactly(5, 4);
  }

  @Test
  @DisplayName("only the oldest sessions needed to reach the hours are used; the rest stay for the next request")
  void onlyTheOldestSessionsNeededAreUsed() {
    SessionSummary a = session(8);
    SessionSummary b = session(8);
    SessionSummary c = session(8);
    SessionSummary d = session(8);
    when(sessions.completedSessionsOf(STUDENT)).thenReturn(List.of(a, b, c, d));

    RequestFile submitted = submit();

    assertThat(submitted.sessions())
        .extracting(RequestedSession::getSessionId)
        .containsExactly(a.sessionId(), b.sessionId(), c.sessionId());
    assertThat(submitted.request().getTotalHours()).isEqualTo(24);
  }

  @Test
  @DisplayName("sessions that already back another request are not used again")
  void usedSessionsAreNotUsedAgain() {
    SessionSummary used = session(15);
    SessionSummary free1 = session(10);
    SessionSummary free2 = session(10);
    when(sessions.completedSessionsOf(STUDENT)).thenReturn(List.of(used, free1, free2));
    when(requestedSessions.findClaimed(UPC, List.of(used.sessionId(), free1.sessionId(), free2.sessionId())))
        .thenReturn(List.of(used.sessionId()));

    RequestFile submitted = submit();

    assertThat(submitted.sessions())
        .extracting(RequestedSession::getSessionId)
        .containsExactly(free1.sessionId(), free2.sessionId());
  }

  @Test
  @DisplayName("it announces the request with the total of hours")
  void itAnnouncesTheRequest() {
    when(sessions.completedSessionsOf(STUDENT)).thenReturn(List.of(session(20)));

    RequestFile submitted = submit();

    ArgumentCaptor<RecognitionRequested> event = ArgumentCaptor.forClass(RecognitionRequested.class);
    verify(events).publishEvent(event.capture());
    assertThat(event.getValue().requestId()).isEqualTo(submitted.request().getId());
    assertThat(event.getValue().studentId()).isEqualTo(STUDENT);
    assertThat(event.getValue().tenantId()).isEqualTo(UPC);
    assertThat(event.getValue().totalHours()).isEqualTo(20);
    assertThat(event.getValue().occurredOn()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("with fewer hours than asked nothing is registered and the missing hours are said")
  void insufficientHours() {
    when(sessions.completedSessionsOf(STUDENT)).thenReturn(List.of(session(10), session(6)));

    assertThatThrownBy(this::submit)
        .isInstanceOfSatisfying(InsufficientHours.class, refusal -> assertThat(refusal.getMissingHours()).isEqualTo(4))
        .hasMessageContaining("4 more hours are needed");

    verify(requests, never()).save(any());
    verify(requestedSessions, never()).saveAll(any());
    verifyNoInteractions(events);
  }

  @Test
  @DisplayName("one missing hour is said in the singular")
  void oneMissingHour() {
    when(sessions.completedSessionsOf(STUDENT)).thenReturn(List.of(session(19)));

    assertThatThrownBy(this::submit).hasMessageContaining("1 more hour is needed");
  }

  @Test
  @DisplayName("a student who taught nothing lacks all the hours")
  void taughtNothing() {
    when(sessions.completedSessionsOf(STUDENT)).thenReturn(List.of());

    assertThatThrownBy(this::submit)
        .isInstanceOfSatisfying(InsufficientHours.class, refusal -> assertThat(refusal.getMissingHours()).isEqualTo(20));
  }

  @Test
  @DisplayName("a university with no rule has not opened recognition, and nothing is registered")
  void noRule() {
    when(rules.findInForce(any(), any())).thenReturn(Optional.empty());
    when(sessions.completedSessionsOf(STUDENT)).thenReturn(List.of(session(50)));

    assertThatThrownBy(this::submit)
        .isInstanceOf(RecognitionStateConflict.class)
        .isNotInstanceOf(InsufficientHours.class);

    verify(requests, never()).save(any());
  }

  @Test
  @DisplayName("a person who is not a user of the university is not found and nothing is read")
  void anUnknownPerson() {
    when(identity.requireUser(STUDENT)).thenThrow(new NoSuchElementException("user not found"));

    assertThatThrownBy(this::submit).isInstanceOf(NoSuchElementException.class);

    verifyNoInteractions(sessions, requests, rules, events);
  }
}
