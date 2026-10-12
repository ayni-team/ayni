package pe.ayni.recognition.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.recognition.domain.model.RecognitionRequest;
import pe.ayni.recognition.domain.model.RequestStatus;
import pe.ayni.recognition.domain.model.RequestedSession;

/** US28: the figures a request copies from its sessions, without Spring. */
class RecognitionRequestTest {

  private static final String UPC = "UPC";
  private static final Instant NOW = Instant.parse("2026-10-05T09:00:00Z");

  private static RequestedSession session(int hours, Integer stars) {
    return RequestedSession.of(
        UUID.randomUUID(), UPC, hours, UUID.randomUUID(), NOW.minusSeconds(7200), NOW.minusSeconds(7200 - 3600L * hours), stars);
  }

  private static RecognitionRequest submit(RequestedSession... sessions) {
    return RecognitionRequest.submit(UUID.randomUUID(), UPC, UUID.randomUUID(), List.of(sessions), NOW);
  }

  @Test
  @DisplayName("the total of hours and the number of sessions are copied from the sessions")
  void theTotalsAreCopied() {
    RecognitionRequest request = submit(session(2, 5), session(1, 4), session(3, null));

    assertThat(request.getTotalHours()).isEqualTo(6);
    assertThat(request.getSessionsCount()).isEqualTo(3);
  }

  @Test
  @DisplayName("the average rating is the mean of the ratings there are, to two decimals, ignoring sessions with none")
  void theAverageIgnoresUnratedSessions() {
    RecognitionRequest request = submit(session(1, 5), session(1, 4), session(1, 4), session(1, null));

    assertThat(request.getAverageRating()).isEqualByComparingTo(new BigDecimal("4.33"));
  }

  @Test
  @DisplayName("without any rating the average is absent, not zero")
  void noRatingsNoAverage() {
    assertThat(submit(session(1, null), session(2, null)).getAverageRating()).isNull();
  }

  @Test
  @DisplayName("a new request is submitted, with the moment and without a decision")
  void aNewRequestIsSubmitted() {
    RecognitionRequest request = submit(session(1, null));

    assertThat(request.getStatus()).isEqualTo(RequestStatus.SUBMITTED);
    assertThat(request.getSubmittedAt()).isEqualTo(NOW);
    assertThat(request.getReviewedBy()).isNull();
    assertThat(request.getReviewedAt()).isNull();
    assertThat(request.getDecisionReason()).isNull();
  }

  @Test
  @DisplayName("every session is attached to the request that was built")
  void sessionsAreAttached() {
    RequestedSession first = session(1, null);
    RequestedSession second = session(1, null);
    RecognitionRequest request =
        RecognitionRequest.submit(UUID.randomUUID(), UPC, UUID.randomUUID(), List.of(first, second), NOW);

    assertThat(first.getRequestId()).isEqualTo(request.getId());
    assertThat(second.getRequestId()).isEqualTo(request.getId());
  }

  @Test
  @DisplayName("a request without sessions is refused")
  void noSessions() {
    assertThatThrownBy(
            () -> RecognitionRequest.submit(UUID.randomUUID(), UPC, UUID.randomUUID(), List.of(), NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("a session needs positive hours, a period that does not end before it starts and a rating from 1 to 5")
  void invalidSessions() {
    UUID id = UUID.randomUUID();
    UUID item = UUID.randomUUID();
    assertThatThrownBy(() -> RequestedSession.of(id, UPC, 0, item, NOW, NOW, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> RequestedSession.of(id, UPC, 1, item, NOW, NOW.minusSeconds(1), null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> RequestedSession.of(id, UPC, 1, item, NOW, NOW, 6))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> RequestedSession.of(id, UPC, 1, item, NOW, NOW, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
