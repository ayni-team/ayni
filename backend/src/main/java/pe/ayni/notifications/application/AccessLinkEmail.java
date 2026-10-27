package pe.ayni.notifications.application;

import java.time.Duration;
import java.time.Instant;
import pe.ayni.shared.events.AccessRequested;

/**
 * What the email carrying an access link says.
 *
 * <p>In Spanish, like everything else the student reads in the web application. It says how long
 * the link lasts, that it works once, and what to do if the student did not ask for it: the three
 * things somebody receiving a key by email needs to know.
 */
final class AccessLinkEmail {

  private AccessLinkEmail() {}

  static Email of(AccessRequested request) {
    long minutes = minutesUntil(request.occurredOn(), request.expiresAt());
    return switch (request.purpose()) {
      case "ACTIVATION" ->
          new Email(
              request.email(),
              "Activa tu cuenta de Ayni",
              body("Usa este enlace para activar tu cuenta de Ayni:", request, minutes));
      case "COORDINATOR_INVITE" ->
          new Email(
              request.email(),
              "Tu invitación como coordinador de Ayni",
              body(
                  "Te invitaron a coordinar Ayni en tu universidad. Usa este enlace para aceptar:",
                  request,
                  minutes));
      default ->
          new Email(
              request.email(),
              "Tu enlace para ingresar a Ayni",
              body("Usa este enlace para ingresar a Ayni:", request, minutes));
    };
  }

  private static String body(String opening, AccessRequested request, long minutes) {
    return """
        Hola:

        %s

        %s

        El enlace funciona una sola vez y vence en %d %s. Si no lo pediste, ignora este correo: \
        nadie puede entrar a tu cuenta sin él.

        Ayni, banco de tiempo académico
        """
        .formatted(opening, request.accessLink(), minutes, minutes == 1 ? "minuto" : "minutos");
  }

  /** Whole minutes, rounded up: a link that lasts nine and a half minutes is not "9 minutes". */
  private static long minutesUntil(Instant from, Instant until) {
    long seconds = Math.max(Duration.between(from, until).toSeconds(), 0);
    return Math.max((seconds + 59) / 60, 1);
  }
}
