package pe.ayni.wallet.application;

import java.time.Instant;
import java.util.UUID;
import pe.ayni.shared.domain.CreditType;
import pe.ayni.shared.domain.Credits;
import pe.ayni.wallet.domain.model.LedgerDirection;
import pe.ayni.wallet.domain.model.LedgerReason;
import pe.ayni.wallet.domain.model.ReferenceType;

/**
 * One line of the history, as the student reads it.
 *
 * <p>It carries the type and expiry of the group the credits moved in or out of, because "two
 * credits spent" means something different depending on whether they were about to expire.
 *
 * @param creditType {@code null} only for an adjustment that belongs to no group
 * @param expiresAt when the group those credits belong to dies, {@code null} when it never does
 */
public record MovementView(
    long sequenceNumber,
    Instant occurredAt,
    LedgerDirection direction,
    Credits amount,
    LedgerReason reason,
    CreditType creditType,
    Instant expiresAt,
    ReferenceType referenceType,
    UUID referenceId) {}
