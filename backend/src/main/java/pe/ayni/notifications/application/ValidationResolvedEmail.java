package pe.ayni.notifications.application;

import java.util.UUID;

/**
 * What the email says to a tutor whose evidence for a global tool was decided.
 *
 * <p>The reason is free text and stays in the application, where the tutor reads it with their
 * skills.
 */
final class ValidationResolvedEmail {

  private ValidationResolvedEmail() {}

  static Email of(UUID validationRequestId, boolean approved, String recipientEmail) {
    return new Email(
        recipientEmail,
        approved
            ? "La evidencia de tu herramienta fue aprobada"
            : "La evidencia de tu herramienta no fue aprobada",
        approved
            ? """
              Hola:

              El coordinador de tu universidad revisó la evidencia que presentaste y la aprobó.
              Tu herramienta ya está habilitada y los estudiantes pueden reservar tutorías contigo.

              Código de solicitud: %s

              Ayni, banco de tiempo académico
              """
                .formatted(validationRequestId)
            : """
              Hola:

              El coordinador de tu universidad revisó la evidencia que presentaste y no la aprobó.
              Puedes leer el motivo en Ayni, en tus habilidades, y presentar nueva evidencia.

              Código de solicitud: %s

              Ayni, banco de tiempo académico
              """
                .formatted(validationRequestId));
  }
}
