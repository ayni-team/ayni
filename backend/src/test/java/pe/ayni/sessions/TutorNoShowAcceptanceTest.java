package pe.ayni.sessions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import pe.ayni.sessions.application.CloseOverdueSessionsUseCase;
import pe.ayni.sessions.application.IssuePresenceCodesUseCase;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * US06 over HTTP against a real PostgreSQL, one test per scenario of {@code
 * features/US06-tutor-no-show.feature}, with the display name of the scenario, and the two ways a
 * session abandoned for a no-show must live next to the presence check and the closing job.
 *
 * <p>Ana books the hour the real way and the clock is moved to where each scenario needs it. The
 * job is not waited for: the tests call the use case it calls, which also tells them the session
 * was due. Wallet, booking, sessions and notifications are the real modules.
 */
class TutorNoShowAcceptanceTest extends TutorNoShowScenario {

  @Autowired private IssuePresenceCodesUseCase issuePresenceCodes;
  @Autowired private CloseOverdueSessionsUseCase closeOverdueSessions;

  /** What the person has earned teaching, according to wallet. */
  private int earnedBy(UUID userId) {
    int[] earned = new int[1];
    TenantContext.runAs(UPC, () -> earned[0] = wallet.earnedTotal(userId).amount());
    return earned[0];
  }

  @Test
  @DisplayName("The tutor does not check in and the attending student is refunded automatically")
  void theAttendingStudentIsRefundedAutomatically() throws Exception {

    UUID bookingId = anaBooksTheHour();
    UUID session = sessionOf(bookingId);
    anaCheckedIn(session);
    TenantContext.runAs(UPC, () -> assertThat(abandonNoShows.dueNow()).doesNotContain(session));

    clock.set(start.plus(Duration.ofMinutes(10)));
    assertThat(abandonDue(session)).isTrue();

    assertThat(sessionStatus(session)).isEqualTo("ABANDONED");
    assertThat(bookingStatus(bookingId)).isEqualTo("NO_SHOW");
    assertThat(balanceOf(ana)).as("the credit came back").isEqualTo(5);
    assertThat(refundsOf(bookingId)).isEqualTo(1);
    assertThat(announcements.abandonedOf(session))
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.studentCheckedIn()).isTrue();
              assertThat(event.tutorId()).isEqualTo(tutor);
              assertThat(event.studentId()).isEqualTo(ana);
              assertThat(event.bookingId()).isEqualTo(bookingId);
              assertThat(event.catalogItemId()).isEqualTo(subject);
            });

    // Ana is told: the notice is recorded, with the session and nothing personal, and sent.
    List<Map<String, Object>> notices =
        jdbc.queryForList(
            """
            select recipient_id, recipient_email, payload::text as payload, sent_at, failed_reason
            from notifications.notifications
            where tenant_id = ? and kind = 'TUTOR_NO_SHOW' and payload ->> 'sessionId' = ?
            """,
            UPC,
            session.toString());
    assertThat(notices).hasSize(1);
    assertThat(notices.getFirst())
        .containsEntry("recipient_id", ana)
        .containsEntry("recipient_email", ANAS_EMAIL);
    assertThat(notices.getFirst().get("sent_at")).isNotNull();
    assertThat(notices.getFirst().get("failed_reason")).isNull();
    assertThat((String) notices.getFirst().get("payload"))
        .contains(session.toString())
        .doesNotContain(ANAS_EMAIL);
    verify(emailDelivery)
        .send(
            argThat(
                email ->
                    email.to().equals(ANAS_EMAIL)
                        && email.subject().contains("devolvieron tus créditos")));
  }

  @Test
  @DisplayName("The tutor's no-show is recorded in their reliability history")
  void theNoShowIsRecordedInTheTutorsHistory() throws Exception {

    UUID bookingId = anaBooksTheHour();
    UUID session = sessionOf(bookingId);
    anaCheckedIn(session);

    clock.set(start.plus(Duration.ofMinutes(10)));
    assertThat(abandonDue(session)).isTrue();

    Map<String, Object> incident =
        jdbc.queryForMap(
            """
            select tutor_id, kind, occurred_at from booking.tutor_reliability
            where tenant_id = ? and booking_id = ?
            """,
            UPC,
            bookingId);
    assertThat(incident)
        .containsEntry("tutor_id", tutor)
        .containsEntry("kind", "NO_SHOW");
    assertThat(((Timestamp) incident.get("occurred_at")).toInstant())
        .isEqualTo(start.plus(Duration.ofMinutes(10)));
    assertThat(incidentsOf(bookingId, "LATE_CANCELLATION")).isZero();
  }

  @Test
  @DisplayName("Neither participant checks in, so there is no refund or tutor no-show")
  void neitherCheckedInSoNobodyIsRefundedOrBlamed() throws Exception {

    UUID bookingId = anaBooksTheHour();
    UUID session = sessionOf(bookingId);

    clock.set(start.plus(Duration.ofMinutes(10)));
    assertThat(abandonDue(session)).isTrue();

    assertThat(sessionStatus(session)).isEqualTo("ABANDONED");
    assertThat(bookingStatus(bookingId)).isEqualTo("NO_SHOW");
    assertThat(balanceOf(ana)).as("the credit is not returned").isEqualTo(4);
    assertThat(refundsOf(bookingId)).isZero();
    assertThat(incidentsOf(bookingId, "NO_SHOW")).isZero();
    assertThat(noShowNoticesOf(session)).isZero();
    verify(emailDelivery, never())
        .send(
            argThat(
                email ->
                    email.to().equals(ANAS_EMAIL)
                        && email.subject().contains("devolvieron tus créditos")));
    assertThat(announcements.abandonedOf(session))
        .singleElement()
        .satisfies(event -> assertThat(event.studentCheckedIn()).isFalse());
  }

  @Test
  @DisplayName("An abandoned session receives no presence codes and is never closed as unverified")
  void anAbandonedSessionIsLeftAloneByThePresenceCheckAndTheClosingJob() throws Exception {

    UUID bookingId = anaBooksTheHour();
    UUID session = sessionOf(bookingId);
    anaCheckedIn(session);
    clock.set(start.plus(Duration.ofMinutes(10)));
    assertThat(abandonDue(session)).isTrue();

    // The presence check would be due, and the closing job would be, if it were still open.
    clock.set(start.plus(Duration.ofMinutes(30)));
    TenantContext.runAs(
        UPC,
        () -> {
          assertThat(issuePresenceCodes.dueNow()).doesNotContain(session);
          assertThat(issuePresenceCodes.issueFor(session)).isFalse();
        });
    clock.set(start.plus(Duration.ofHours(1)).plus(Duration.ofMinutes(20)));
    TenantContext.runAs(
        UPC,
        () -> {
          assertThat(closeOverdueSessions.dueNow()).doesNotContain(session);
          assertThat(closeOverdueSessions.closeFor(session)).isFalse();
        });

    assertThat(presenceChecksOf(session)).isZero();
    assertThat(sessionStatus(session)).isEqualTo("ABANDONED");
    assertThat(announcements.unverifiedOf(session)).isEmpty();
    assertThat(refundsOf(bookingId)).as("one refund, not two").isEqualTo(1);
    assertThat(balanceOf(ana)).isEqualTo(5);
    // Nobody can join it or confirm anything in it either.
    join(session, ana).andExpect(status().isConflict());
    mockMvc
        .perform(
            post("/api/v1/sessions/{id}/presence", session)
                .header("X-Tenant-Id", UPC)
                .header("X-User-Id", ana)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"123456\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("The student does not check in and the attending tutor is neither penalized nor paid")
  void anAbsentStudentCostsTheAttendingTutorNothing() throws Exception {

    UUID bookingId = anaBooksTheHour();
    UUID session = sessionOf(bookingId);
    tutorCheckedIn(session);
    assertThat(sessionStatus(session)).as("one of two is not a start").isEqualTo("SCHEDULED");
    TenantContext.runAs(UPC, () -> assertThat(abandonNoShows.dueNow()).doesNotContain(session));

    clock.set(start.plus(Duration.ofMinutes(10)));
    assertThat(abandonDue(session)).isTrue();

    assertThat(sessionStatus(session)).isEqualTo("ABANDONED");
    assertThat(bookingStatus(bookingId)).isEqualTo("NO_SHOW");
    assertThat(announcements.abandonedOf(session))
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.studentCheckedIn()).isFalse();
              assertThat(event.tutorId()).isEqualTo(tutor);
              assertThat(event.studentId()).isEqualTo(ana);
            });

    // The student lost the hour: nothing came back and nobody was told.
    assertThat(balanceOf(ana)).as("the credit is not returned").isEqualTo(4);
    assertThat(refundsOf(bookingId)).isZero();
    assertThat(noShowNoticesOf(session)).isZero();
    verify(emailDelivery, never())
        .send(
            argThat(
                email ->
                    email.to().equals(ANAS_EMAIL)
                        && email.subject().contains("devolvieron tus créditos")));

    // The tutor came, so nothing is held against them, and they earn nothing either.
    assertThat(incidentsOf(bookingId, "NO_SHOW")).isZero();
    assertThat(incidentsOf(bookingId, "LATE_CANCELLATION")).isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from booking.tutor_reliability where tenant_id = ? and tutor_id = ?",
                Integer.class,
                UPC,
                tutor))
        .isZero();
    assertThat(earnedBy(tutor)).isZero();
    assertThat(ledgerEntriesOf(tutor)).isZero();
    assertThat(announcements.startedOf(session)).isEmpty();

    // Nothing later turns it into something else: no codes, no closing, no payment.
    clock.set(start.plus(Duration.ofHours(1)).plus(Duration.ofMinutes(20)));
    TenantContext.runAs(
        UPC,
        () -> {
          assertThat(issuePresenceCodes.issueFor(session)).isFalse();
          assertThat(closeOverdueSessions.closeFor(session)).isFalse();
        });
    assertThat(sessionStatus(session)).isEqualTo("ABANDONED");
    assertThat(announcements.unverifiedOf(session)).isEmpty();
    assertThat(earnedBy(tutor)).isZero();
    assertThat(ledgerEntriesOf(tutor)).isZero();
    assertThat(balanceOf(ana)).isEqualTo(4);
  }

  @Test
  @DisplayName("A session that never started is sent no codes before it is abandoned")
  void aSessionThatNeverStartedIsSentNoCodes() throws Exception {

    UUID bookingId = anaBooksTheHour();
    UUID session = sessionOf(bookingId);
    anaCheckedIn(session);

    // Five minutes in, the presence check would go out to both if the session had started.
    clock.set(start.plus(Duration.ofMinutes(5)));
    TenantContext.runAs(
        UPC,
        () -> {
          assertThat(issuePresenceCodes.dueNow()).doesNotContain(session);
          assertThat(issuePresenceCodes.issueFor(session)).isFalse();
        });
    assertThat(presenceChecksOf(session)).isZero();
    assertThat(sessionStatus(session)).isEqualTo("SCHEDULED");

    clock.set(start.plus(Duration.ofMinutes(10)));
    assertThat(abandonDue(session)).isTrue();

    clock.set(start.plus(Duration.ofHours(1)).plus(Duration.ofMinutes(20)));
    TenantContext.runAs(
        UPC,
        () -> {
          assertThat(closeOverdueSessions.dueNow()).doesNotContain(session);
          assertThat(closeOverdueSessions.closeFor(session)).isFalse();
        });

    assertThat(presenceChecksOf(session)).isZero();
    assertThat(sessionStatus(session)).isEqualTo("ABANDONED");
    assertThat(announcements.unverifiedOf(session)).isEmpty();
    assertThat(announcements.abandonedOf(session)).hasSize(1);
    assertThat(refundsOf(bookingId)).as("one refund, not two").isEqualTo(1);
    assertThat(balanceOf(ana)).isEqualTo(5);
  }

  @Test
  @DisplayName("A session whose tutor came in time is not abandoned")
  void aTutorWhoCameInTimeIsNotAbandoned() throws Exception {

    UUID bookingId = anaBooksTheHour();
    UUID session = sessionOf(bookingId);
    anaCheckedIn(session);
    clock.set(start.plus(Duration.ofMinutes(3)));
    join(session, tutor).andExpect(status().isOk());

    clock.set(start.plus(Duration.ofMinutes(15)));

    TenantContext.runAs(UPC, () -> assertThat(abandonNoShows.abandonFor(session)).isFalse());
    assertThat(sessionStatus(session)).isEqualTo("IN_PROGRESS");
    assertThat(bookingStatus(bookingId)).isEqualTo("CONFIRMED");
    assertThat(announcements.abandonedOf(session)).isEmpty();
    assertThat(refundsOf(bookingId)).isZero();
  }
}
