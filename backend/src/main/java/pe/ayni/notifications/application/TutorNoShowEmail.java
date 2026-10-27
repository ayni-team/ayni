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

        El tutor no se presentó a tu tutoría dentro de los primeros diez minutos. La sesión se
        dio por abandonada y los créditos que pagaste por ella se te devolvieron. No necesitas
        presentar ningún reclamo.

        Ayni, banco de tiempo académico
        """);
  }
}
