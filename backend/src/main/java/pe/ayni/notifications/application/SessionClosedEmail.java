package pe.ayni.notifications.application;

/**
 * What the email of a closed session says to each participant.
 *
 * <p>A completed session tells the student to rate it, which they do in the application; the
 * address of that screen is the frontend's, so none travels here. An unverified one says the same to
 * both, that nobody earns anything and the student is refunded, and invites nobody to rate.
 */
final class SessionClosedEmail {

  private SessionClosedEmail() {}

  /** @param creditsEarned what the tutor earned, read only when the notice is the tutor's */
  static Email of(
      Participant role, SessionOutcome outcome, int creditsEarned, String recipientEmail) {
    if (outcome == SessionOutcome.UNVERIFIED) {
      return new Email(
          recipientEmail,
          "Tu sesión de Ayni terminó sin verificarse",
          """
          Hola:

          La sesión terminó sin que ambos confirmaran su presencia, así que no pudo verificarse. %s

          Ayni, banco de tiempo académico
          """
              .formatted(
                  role == Participant.STUDENT
                      ? "Los créditos que pagaste por ella se te devolvieron."
                      : "No se acreditaron créditos por esta sesión."));
    }
    return role == Participant.STUDENT
        ? new Email(
            recipientEmail,
            "Tu sesión de Ayni terminó: califícala",
            """
            Hola:

            Tu sesión terminó y quedó verificada. Entra a Ayni y califícala: tu opinión ayuda a
            otros estudiantes a elegir con quién estudiar.

            Ayni, banco de tiempo académico
            """)
        : new Email(
            recipientEmail,
            "Tu sesión de Ayni terminó y ganaste créditos",
            """
            Hola:

            Tu sesión terminó y quedó verificada. Ganaste %d %s, que no vencen.

            Ayni, banco de tiempo académico
            """
                .formatted(creditsEarned, creditsEarned == 1 ? "crédito" : "créditos"));
  }
}
