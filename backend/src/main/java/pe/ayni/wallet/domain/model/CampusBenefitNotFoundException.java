package pe.ayni.wallet.domain.model;

import java.util.UUID;

public class CampusBenefitNotFoundException extends RuntimeException {

  public CampusBenefitNotFoundException(UUID benefitId) {
    super("Campus benefit not found: " + benefitId);
  }
}
