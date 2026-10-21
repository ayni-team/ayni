package pe.ayni.notifications.application;

import java.time.ZoneId;
import pe.ayni.shared.events.BookingConfirmed;

/**
 * What the email of a confirmed booking says, to the student and to the tutor.
 *
 * <p>In Spanish, like the others. It gives the day and the hours in the university's zone and says
 * where the session is entered, in the application: no link to the room travels by email.
 */
final class BookingConfirmedEmail {

  private BookingConfirmedEmail() {}

  static Email of(BookingConfirmed booking, Participant role, ZoneId zone, String recipientEmail) {
    String when = NoticeDates.span(booking.startsAt(), booking.endsAt(), zone);
    return role == Participant.STUDENT
        ? new Email(
            recipientEmail,
            "Tu tutoría en Ayni quedó confirmada",
            """
            Hola:

            Tu tutoría quedó confirmada para el %s.
            Se descontaron %d %s de tu saldo.

            Cuando llegue la hora, entra a la sesión desde Ayni, en tus próximas tutorías. Si
            necesitas cancelarla, hazlo desde Ayni con al menos doce horas de anticipación para
            que se te devuelvan los créditos.

            Código de reserva: %s

            Ayni, banco de tiempo académico
            """
                .formatted(
                    when,
                    booking.creditsCharged().amount(),
                    booking.creditsCharged().amount() == 1 ? "crédito" : "créditos",
                    booking.bookingId()))
        : new Email(
            recipientEmail,
            "Tienes una nueva tutoría en Ayni",
            """
            Hola:

            Un estudiante reservó una tutoría contigo para el %s.

            Cuando llegue la hora, entra a la sesión desde Ayni, en tus próximas tutorías. Si no
            puedes dar la tutoría, cancélala desde Ayni cuanto antes: una cancelación con menos de
            doce horas de anticipación queda registrada.

            Código de reserva: %s

            Ayni, banco de tiempo académico
            """
                .formatted(when, booking.bookingId()));
  }
}
