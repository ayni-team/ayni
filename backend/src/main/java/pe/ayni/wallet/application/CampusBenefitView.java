package pe.ayni.wallet.application;

import java.time.Instant;
import java.util.UUID;
import pe.ayni.wallet.domain.model.CampusBenefit;

public record CampusBenefitView(
    UUID id,
    String name,
    String description,
    int creditsCost,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {

  static CampusBenefitView of(CampusBenefit benefit) {
    return new CampusBenefitView(
        benefit.id(),
        benefit.name(),
        benefit.description(),
        benefit.creditsCost(),
        benefit.active(),
        benefit.createdAt(),
        benefit.updatedAt());
  }
}
