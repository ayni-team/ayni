package pe.ayni.wallet;

import java.util.UUID;

public class CampusBenefitNotFoundException extends RuntimeException {

  public CampusBenefitNotFoundException(UUID benefitId) {
    super("Campus benefit not found: " + benefitId);
  }
}
