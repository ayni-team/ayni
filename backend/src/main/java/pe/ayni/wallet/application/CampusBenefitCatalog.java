package pe.ayni.wallet.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserRole;
import pe.ayni.shared.tenancy.CurrentUser;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.wallet.CampusBenefitNotFoundException;
import pe.ayni.wallet.WalletAccessDeniedException;
import pe.ayni.wallet.domain.model.CampusBenefit;
import pe.ayni.wallet.infrastructure.CampusBenefitRepository;

@Service
public class CampusBenefitCatalog {

  private final CampusBenefitRepository benefits;
  private final IdentityApi identity;
  private final Clock clock;

  CampusBenefitCatalog(
      CampusBenefitRepository benefits, IdentityApi identity, Clock clock) {
    this.benefits = benefits;
    this.identity = identity;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<CampusBenefitView> available() {
    return benefits.findByTenantIdAndActiveTrueOrderByNameAsc(TenantContext.require()).stream()
        .map(CampusBenefitView::of)
        .toList();
  }

  @Transactional
  public CampusBenefitView create(
      String name, String description, int creditsCost, boolean active) {
    requireCoordinator();
    String tenantId = TenantContext.require();
    CampusBenefit benefit =
        CampusBenefit.create(
            UUID.randomUUID(), tenantId, name, description, creditsCost, active, clock.instant());
    return CampusBenefitView.of(benefits.save(benefit));
  }

  @Transactional
  public CampusBenefitView update(
      UUID benefitId, String name, String description, int creditsCost, boolean active) {
    requireCoordinator();
    String tenantId = TenantContext.require();
    CampusBenefit benefit =
        benefits
            .lockByTenantIdAndId(tenantId, benefitId)
            .orElseThrow(() -> new CampusBenefitNotFoundException(benefitId));
    benefit.update(name, description, creditsCost, active, clock.instant());
    return CampusBenefitView.of(benefit);
  }

  private void requireCoordinator() {
    if (identity.requireUser(CurrentUser.require()).role() != UserRole.COORDINATOR) {
      throw new WalletAccessDeniedException();
    }
  }
}
