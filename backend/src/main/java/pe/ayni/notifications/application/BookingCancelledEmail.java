package pe.ayni.notifications.application;

import pe.ayni.shared.events.BookingCancelled;

/**
 * What the email of a cancelled booking says to the other party.
 *
 * <p>The student is told when the tutor or the system cancelled, and always that the credits came
 * back: wallet refunds every cancellation except a late one by the student, who is not the one told.
 * The tutor is told when the student cancelled, and whether it was late.
 */
final class BookingCancelledEmail {

  private BookingCancelledEmail() {}

  static Email of(BookingCancelled cancelled, String recipientEmail) {
    return cancelled.cancelledBy() == BookingCancelled.CancelledBy.STUDENT
        ? toTutor(cancelled, recipientEmail)
        : toStudent(cancelled, recipientEmail);
  }

  private static Email toTutor(BookingCancelled cancelled, String recipientEmail) {
    return new Email(
        recipientEmail,
        "Un estudiante canceló su tutoría en Ayni",
        """
        Hola:

        El estudiante canceló la tutoría que había reservado contigo%s.
        Las horas vuelven a estar disponibles en tu agenda.

        Código de reserva: %s

        Ayni, banco de tiempo académico
        """
            .formatted(
                cancelled.late() ? ", con menos de doce horas de anticipación" : "",
                cancelled.bookingId()));
  }

  private static Email toStudent(BookingCancelled cancelled, String recipientEmail) {
    String who =
        cancelled.cancelledBy() == BookingCancelled.CancelledBy.TUTOR
            ? "El tutor canceló la tutoría que habías reservado."
            : "Ayni canceló la tutoría que habías reservado.";
    return new Email(
        recipientEmail,
        "Se canceló tu tutoría en Ayni",
        """
        Hola:

        %s
        Los créditos que pagaste por ella se te devolvieron. Puedes reservar otro horario desde Ayni.

        Código de reserva: %s

        Ayni, banco de tiempo académico
        """
            .formatted(who, cancelled.bookingId()));
  }
}
