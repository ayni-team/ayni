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
import pe.ayni.shared.events.BookingCancelled;
import pe.ayni.shared.events.BookingCancelled.CancelledBy;
import pe.ayni.shared.events.BookingConfirmed;

class DeliverBookingNoticesTest {

  private static final Instant STARTS_AT = Instant.parse("2026-10-21T14:00:00Z");
  private static final UUID BOOKING = UUID.randomUUID();

  private final NoticeHarness harness = new NoticeHarness();
  private final UUID student = harness.personAt("ana@upc.edu.pe");
  private final UUID tutor = harness.personAt("bruno@upc.edu.pe");

  private BookingConfirmed confirmed() {
    return new BookingConfirmed(
        NoticeHarness.TENANT,
        BOOKING,
        student,
        tutor,
        UUID.randomUUID(),
        STARTS_AT,
        STARTS_AT.plusSeconds(7200),
        List.of(UUID.randomUUID(), UUID.randomUUID()),
        Credits.of(2),
        NoticeHarness.NOW);
  }

  private BookingCancelled cancelled(CancelledBy by, boolean late) {
    return new BookingCancelled(
        NoticeHarness.TENANT, BOOKING, student, tutor, List.of(), by, late, NoticeHarness.NOW);
  }

  @Test
  @DisplayName("a confirmed booking tells the student and the tutor, each with their role")
  void aConfirmedBookingTellsBoth() {
    new DeliverBookingConfirmedNoticesUseCase(harness.sender).execute(confirmed());

    assertThat(harness.noticeOf(student).getKind()).isEqualTo(NotificationKind.BOOKING_CONFIRMED);
    assertThat(harness.noticeOf(student).getPayload())
        .isEqualTo(
            Map.of(
                "bookingId", BOOKING.toString(),
                "startsAt", "2026-10-21T14:00:00Z",
                "role", "STUDENT"));
    assertThat(harness.noticeOf(tutor).getPayload()).containsEntry("role", "TUTOR");

    Email toStudent = harness.emailTo("ana@upc.edu.pe");
    assertThat(toStudent.subject()).isEqualTo("Tu tutoría en Ayni quedó confirmada");
    assertThat(toStudent.body())
        .contains("miércoles 21 de octubre de 2026, de 09:00 a 11:00 (America/Lima)")
        .contains("Se descontaron 2 créditos")
        .contains(BOOKING.toString())
        .doesNotContain("http");
    Email toTutor = harness.emailTo("bruno@upc.edu.pe");
    assertThat(toTutor.subject()).isEqualTo("Tienes una nueva tutoría en Ayni");
    assertThat(toTutor.body()).contains("de 09:00 a 11:00").doesNotContain("descontaron");
  }

  @Test
  @DisplayName("a cancellation by the student tells the tutor and says if it was late")
  void aCancellationByTheStudentTellsTheTutor() {
    new DeliverBookingCancelledNoticeUseCase(harness.sender)
        .execute(cancelled(CancelledBy.STUDENT, true));

    assertThat(harness.recorded).hasSize(1);
    assertThat(harness.noticeOf(tutor).getKind()).isEqualTo(NotificationKind.BOOKING_CANCELLED);
    assertThat(harness.noticeOf(tutor).getPayload())
        .isEqualTo(
            Map.of("bookingId", BOOKING.toString(), "cancelledBy", "STUDENT", "late", "true"));
    Email email = harness.emailTo("bruno@upc.edu.pe");
    assertThat(email.subject()).isEqualTo("Un estudiante canceló su tutoría en Ayni");
    assertThat(email.body()).contains("con menos de doce horas de anticipación");
  }

  @Test
  @DisplayName("a cancellation by the tutor tells the student that the credits came back")
  void aCancellationByTheTutorTellsTheStudent() {
    new DeliverBookingCancelledNoticeUseCase(harness.sender)
        .execute(cancelled(CancelledBy.TUTOR, false));

    assertThat(harness.recorded).hasSize(1);
    assertThat(harness.noticeOf(student).getPayload())
        .containsEntry("cancelledBy", "TUTOR")
        .containsEntry("late", "false");
    Email email = harness.emailTo("ana@upc.edu.pe");
    assertThat(email.subject()).isEqualTo("Se canceló tu tutoría en Ayni");
    assertThat(email.body()).contains("El tutor canceló").contains("se te devolvieron");
  }

  @Test
  @DisplayName("a cancellation by the system tells the student too")
  void aCancellationByTheSystemTellsTheStudent() {
    new DeliverBookingCancelledNoticeUseCase(harness.sender)
        .execute(cancelled(CancelledBy.SYSTEM, false));

    assertThat(harness.recorded).hasSize(1);
    assertThat(harness.noticeOf(student).getPayload()).containsEntry("cancelledBy", "SYSTEM");
    assertThat(harness.emailTo("ana@upc.edu.pe").body()).contains("Ayni canceló");
  }
}
