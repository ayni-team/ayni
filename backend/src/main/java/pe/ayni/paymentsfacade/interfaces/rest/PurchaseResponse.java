package pe.ayni.paymentsfacade.interfaces.rest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A purchase as the payments service answers it, read and written again unchanged. The record is
 * here so the OpenAPI description of the monolith stays the one the web application is built
 * against (docs/api/openapi.json).
 */
public record PurchaseResponse(
    UUID id,
    int credits,
    BigDecimal amount,
    String currency,
    PurchaseStatus status,
    String providerReference,
    Instant createdAt,
    Instant confirmedAt,
    Instant expiresAt,
    boolean providerUnavailable,
    String guidance) {}
