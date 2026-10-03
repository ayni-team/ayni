package pe.ayni.notifications.application;

/** Email notifying the student that a tutor did not check in and their credits were returned. */
final class TutorNoShowEmail {

  private TutorNoShowEmail() {}

  static Email to(String recipientEmail) {
    return new Email(
        recipientEmail,
        "Se devolvieron tus créditos por inasistencia del tutor",
        """
        Hola:

        El tutor no se registró en tu tutoría dentro de los primeros diez minutos.
        La sesión fue marcada como abandonada y el sistema gestionará la devolución automática de
        tus créditos. No necesitas presentar un reclamo.

        Ayni, banco de tiempo académico
        """);
  }
}
