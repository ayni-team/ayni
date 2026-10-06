package pe.ayni.notifications.application;

import pe.ayni.shared.events.PurchaseConfirmed;

final class PurchaseConfirmedEmail {

  private PurchaseConfirmedEmail() {}

  static Email of(PurchaseConfirmed purchase, String recipientEmail) {
    int amount = purchase.credits().amount();
    return new Email(
        recipientEmail,
        "Tus créditos ya están disponibles en Ayni",
        """
        Hola:

        El pago de tu compra se confirmó y ya acreditamos %d %s en tu saldo de Ayni.
        Estos créditos no vencen y puedes usarlos para reservar una tutoría.

        Código de compra: %s

        Si no reconoces esta compra, contacta al soporte de tu universidad.

        Ayni, banco de tiempo académico
        """
            .formatted(amount, amount == 1 ? "crédito" : "créditos", purchase.purchaseId()));
  }
}
