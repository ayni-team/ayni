package pe.ayni.notifications.application;

import java.util.UUID;

/**
 * What the email says to a student whose recognition request was decided.
 *
 * <p>It does not repeat the coordinator's reason: that is free text, and the student reads it in the
 * application, with their other requests.
 */
final class RequestResolvedEmail {

  private RequestResolvedEmail() {}

  static Email of(UUID requestId, boolean approved, String recipientEmail) {
    return new Email(
        recipientEmail,
        approved
            ? "Tu solicitud de reconocimiento fue aprobada"
            : "Tu solicitud de reconocimiento no fue aprobada",
        """
        Hola:

        El coordinador de tu universidad revisó tu solicitud de reconocimiento y %s.
        Puedes leer el motivo de la decisión en Ayni, en tus solicitudes de reconocimiento.

        Código de solicitud: %s

        Ayni, banco de tiempo académico
        """
            .formatted(approved ? "la aprobó" : "no la aprobó", requestId));
  }
}
