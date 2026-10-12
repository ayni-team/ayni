package pe.ayni.wallet.interfaces.rest;

import java.time.Instant;
import java.util.UUID;
import pe.ayni.wallet.application.CampusBenefitView;

public record CampusBenefitResponse(
    UUID id,
    String name,
    String description,
    int creditsCost,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {

  static CampusBenefitResponse of(CampusBenefitView benefit) {
    return new CampusBenefitResponse(
        benefit.id(),
        benefit.name(),
        benefit.description(),
        benefit.creditsCost(),
        benefit.active(),
        benefit.createdAt(),
        benefit.updatedAt());
  }
}
