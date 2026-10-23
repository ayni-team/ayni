package pe.ayni.notifications.application;

import java.time.ZoneId;
import pe.ayni.shared.events.CreditsExpiring;

/** What the email says to a student whose credits are about to expire. */
final class CreditsExpiringEmail {

  private CreditsExpiringEmail() {}

  static Email of(CreditsExpiring expiring, ZoneId zone, String recipientEmail) {
    int amount = expiring.amount().amount();
    return new Email(
        recipientEmail,
        "Tus créditos de Ayni están por vencer",
        """
        Hola:

        %d %s de tu saldo %s el %s.
        Resérvalos en una tutoría antes de esa fecha para no perderlos.

        Los créditos que ganas enseñando o que compras no vencen.

        Ayni, banco de tiempo académico
        """
            .formatted(
                amount,
                amount == 1 ? "crédito" : "créditos",
                amount == 1 ? "vence" : "vencen",
                NoticeDates.day(expiring.expiresAt(), zone)));
  }
}
