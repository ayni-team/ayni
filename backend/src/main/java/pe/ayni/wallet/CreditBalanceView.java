package pe.ayni.wallet;

import java.util.UUID;
import pe.ayni.shared.domain.Credits;

/** What a user can spend right now. Expired credits are not included. */
public record CreditBalanceView(UUID userId, Credits available) {}
