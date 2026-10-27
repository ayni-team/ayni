package pe.ayni.notifications.application;

import java.time.Duration;
import java.time.Instant;
import pe.ayni.shared.events.PresenceCodeIssued;

/**
 * What the email carrying a presence code says.
 *
 * <p>In Spanish, like the access link. It says where to type the code, until when, and what happens
 * if nobody does: the participant is in a video call and reads this in a hurry. The code is in the
 * body and not in the subject, which mail servers log.
 */
final class PresenceCodeEmail {

  private PresenceCodeEmail() {}

  static Email of(PresenceCodeIssued issued, String recipientEmail) {
    long minutes = minutesUntil(issued.occurredOn(), issued.expiresAt());
    return new Email(
        recipientEmail,
        "Tu código de presencia en Ayni",
        """
        Hola:

        Tu código de presencia para la sesión en curso es:

            %s

        Escríbelo en la sesión de Ayni en los próximos %d %s. Si no lo confirmas, la sesión \
        quedará como no verificada y el tutor no recibirá sus créditos.

        Si no estás en una sesión de Ayni ahora mismo, ignora este correo.

        Ayni, banco de tiempo académico
        """
            .formatted(issued.code(), minutes, minutes == 1 ? "minuto" : "minutos"));
  }

  /** Whole minutes, rounded up, as for the access link. */
  private static long minutesUntil(Instant from, Instant until) {
    long seconds = Math.max(Duration.between(from, until).toSeconds(), 0);
    return Math.max((seconds + 59) / 60, 1);
  }
}
