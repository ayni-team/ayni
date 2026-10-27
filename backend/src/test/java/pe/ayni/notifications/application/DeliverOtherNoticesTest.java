package pe.ayni.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.notifications.domain.model.NotificationKind;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.events.CreditsExpiring;
import pe.ayni.shared.events.RecognitionResolved;
import pe.ayni.shared.events.SessionCompleted;
import pe.ayni.shared.events.SessionUnverified;
import pe.ayni.shared.events.ValidationResolved;

/** The notices of credits, sessions, recognition requests and validations. */
class DeliverOtherNoticesTest {

  private static final UUID SESSION = UUID.randomUUID();
  private static final UUID REQUEST = UUID.randomUUID();
  private static final UUID ITEM = UUID.randomUUID();

  private final NoticeHarness harness = new NoticeHarness();
  private final UUID student = harness.personAt("ana@upc.edu.pe");
  private final UUID tutor = harness.personAt("bruno@upc.edu.pe");

  @Test
  @DisplayName("expiring credits tell the student the amount and the day, in the university zone")
  void expiringCreditsTellTheStudent() {
    // 02:00 UTC is still the day before in Lima.
    Instant expiresAt = Instant.parse("2026-10-27T02:00:00Z");

    new DeliverCreditsExpiringNoticeUseCase(harness.sender)
        .execute(
            new CreditsExpiring(
                NoticeHarness.TENANT, student, Credits.of(5), expiresAt, NoticeHarness.NOW));

    assertThat(harness.noticeOf(student).getKind()).isEqualTo(NotificationKind.CREDITS_EXPIRING);
    assertThat(harness.noticeOf(student).getPayload())
        .isEqualTo(Map.of("amount", "5", "expiresAt", "2026-10-27T02:00:00Z"));
    Email email = harness.emailTo("ana@upc.edu.pe");
    assertThat(email.subject()).isEqualTo("Tus créditos de Ayni están por vencer");
    assertThat(email.body()).contains("5 créditos de tu saldo vencen el lunes 26 de octubre de 2026");
  }

  @Test
  @DisplayName("a completed session tells the student to rate it and the tutor what they earned")
  void aCompletedSessionTellsBoth() {
    new DeliverSessionClosedNoticesUseCase(harness.sender)
        .execute(
            new SessionCompleted(
                NoticeHarness.TENANT,
                SESSION,
                UUID.randomUUID(),
                tutor,
                student,
                ITEM,
                Credits.of(2),
                NoticeHarness.NOW));

    assertThat(harness.noticeOf(student).getKind()).isEqualTo(NotificationKind.SESSION_CLOSED);
    assertThat(harness.noticeOf(student).getPayload())
        .isEqualTo(
            Map.of("sessionId", SESSION.toString(), "outcome", "COMPLETED", "role", "STUDENT"));
    assertThat(harness.noticeOf(tutor).getPayload())
        .isEqualTo(
            Map.of(
                "sessionId", SESSION.toString(),
                "outcome", "COMPLETED",
                "role", "TUTOR",
                "creditsEarned", "2"));
    assertThat(harness.emailTo("ana@upc.edu.pe").body())
        .contains("califícala")
        .doesNotContain("http");
    assertThat(harness.emailTo("bruno@upc.edu.pe").body()).contains("Ganaste 2 créditos");
  }

  @Test
  @DisplayName("an unverified session tells the student they are refunded and the tutor they earn nothing")
  void anUnverifiedSessionTellsBoth() {
    new DeliverSessionClosedNoticesUseCase(harness.sender)
        .execute(
            new SessionUnverified(
                NoticeHarness.TENANT,
                SESSION,
                UUID.randomUUID(),
                tutor,
                student,
                List.of(tutor),
                NoticeHarness.NOW));

    assertThat(harness.noticeOf(student).getPayload())
        .isEqualTo(
            Map.of("sessionId", SESSION.toString(), "outcome", "UNVERIFIED", "role", "STUDENT"));
    assertThat(harness.noticeOf(tutor).getPayload())
        .containsEntry("outcome", "UNVERIFIED")
        .containsEntry("creditsEarned", "0");
    assertThat(harness.emailTo("ana@upc.edu.pe").body())
        .contains("se te devolvieron")
        .doesNotContain("califícala");
    assertThat(harness.emailTo("bruno@upc.edu.pe").body()).contains("No se acreditaron");
  }

  @Test
  @DisplayName("a resolved recognition request tells the student and never repeats the reason")
  void aResolvedRequestTellsTheStudentWithoutTheReason() {
    String reason = "Faltan evidencias de la sesión del martes";

    new DeliverRequestResolvedNoticeUseCase(harness.sender)
        .execute(
            new RecognitionResolved(
                NoticeHarness.TENANT, REQUEST, student, false, reason, NoticeHarness.NOW));

    assertThat(harness.noticeOf(student).getKind()).isEqualTo(NotificationKind.REQUEST_RESOLVED);
    assertThat(harness.noticeOf(student).getPayload())
        .isEqualTo(Map.of("requestId", REQUEST.toString(), "approved", "false"));
    Email email = harness.emailTo("ana@upc.edu.pe");
    assertThat(email.subject()).isEqualTo("Tu solicitud de reconocimiento no fue aprobada");
    assertThat(email.body()).contains("no la aprobó").doesNotContain(reason);
  }

  @Test
  @DisplayName("a resolved validation tells the tutor and never repeats the reason")
  void aResolvedValidationTellsTheTutorWithoutTheReason() {
    String reason = "El portafolio no muestra el uso de la herramienta";
    UUID validation = UUID.randomUUID();

    new DeliverValidationResolvedNoticeUseCase(harness.sender)
        .execute(
            new ValidationResolved(
                NoticeHarness.TENANT, validation, tutor, ITEM, true, reason, NoticeHarness.NOW));

    assertThat(harness.noticeOf(tutor).getKind()).isEqualTo(NotificationKind.VALIDATION_RESOLVED);
    assertThat(harness.noticeOf(tutor).getPayload())
        .isEqualTo(
            Map.of(
                "validationRequestId", validation.toString(),
                "catalogItemId", ITEM.toString(),
                "approved", "true"));
    Email email = harness.emailTo("bruno@upc.edu.pe");
    assertThat(email.subject()).isEqualTo("La evidencia de tu herramienta fue aprobada");
    assertThat(email.body()).contains("la aprobó").doesNotContain(reason);
  }
}
