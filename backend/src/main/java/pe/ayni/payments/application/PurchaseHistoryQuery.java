package pe.ayni.payments.application;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.payments.infrastructure.PurchaseRepository;
import pe.ayni.shared.tenancy.TenantContext;

@Service
public class PurchaseHistoryQuery {

  private final PurchaseRepository purchases;

  PurchaseHistoryQuery(PurchaseRepository purchases) {
    this.purchases = purchases;
  }

  @Transactional(readOnly = true)
  public List<PurchaseOutcome> forStudent(UUID studentId) {
    String tenantId = TenantContext.require();
    return purchases.findByTenantIdAndStudentIdOrderByCreatedAtDesc(tenantId, studentId).stream()
        .map(purchase -> PurchaseOutcome.of(purchase, false))
        .toList();
  }

  @Transactional(readOnly = true)
  public PurchaseOutcome detailForStudent(UUID studentId, UUID purchaseId) {
    String tenantId = TenantContext.require();
    return purchases
        .findByTenantIdAndStudentIdAndId(tenantId, studentId, purchaseId)
        .map(purchase -> PurchaseOutcome.of(purchase, false))
        .orElseThrow(() -> new PurchaseNotFoundException(purchaseId));
  }
}
