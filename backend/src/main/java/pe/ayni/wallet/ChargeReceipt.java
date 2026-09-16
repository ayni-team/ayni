package pe.ayni.wallet;

import java.util.UUID;
import pe.ayni.shared.domain.Credits;

/** Proof that a booking was charged. */
public record ChargeReceipt(UUID bookingId, Credits charged) {}
