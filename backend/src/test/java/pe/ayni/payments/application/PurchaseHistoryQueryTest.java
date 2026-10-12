package pe.ayni.payments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pe.ayni.payments.domain.model.Purchase;
import pe.ayni.payments.domain.model.PurchaseStatus;
import pe.ayni.payments.infrastructure.PurchaseRepository;
import pe.ayni.shared.tenancy.TenantContext;

class PurchaseHistoryQueryTest {

  private static final String TENANT = "UPC";

  private final PurchaseRepository purchases = mock(PurchaseRepository.class);
  private final PurchaseHistoryQuery query = new PurchaseHistoryQuery(purchases);

  @Test
  void returnsDetailsForTheCurrentStudentAndTenant() {
    UUID studentId = UUID.randomUUID();
    UUID purchaseId = UUID.randomUUID();
    Instant createdAt = Instant.parse("2026-10-06T12:00:00Z");
    Purchase purchase =
        Purchase.pending(
            purchaseId,
            TENANT,
            studentId,
            2,
            new BigDecimal("10.00"),
            "PEN",
            "receipt-key",
            createdAt.plusSeconds(3600),
            createdAt);
    purchase.confirm("provider-reference", createdAt.plusSeconds(2));
    when(purchases.findByTenantIdAndStudentIdAndId(TENANT, studentId, purchaseId))
        .thenReturn(Optional.of(purchase));

    PurchaseOutcome[] found = new PurchaseOutcome[1];
    TenantContext.runAs(TENANT, () -> found[0] = query.detailForStudent(studentId, purchaseId));

    assertThat(found[0].id()).isEqualTo(purchaseId);
    assertThat(found[0].credits()).isEqualTo(2);
    assertThat(found[0].amount()).isEqualByComparingTo("10.00");
    assertThat(found[0].status()).isEqualTo(PurchaseStatus.CONFIRMED);
    assertThat(found[0].confirmedAt()).isEqualTo(createdAt.plusSeconds(2));
    verify(purchases).findByTenantIdAndStudentIdAndId(TENANT, studentId, purchaseId);
  }

  @Test
  void rejectsPurchaseDetailsThatDoNotBelongToTheStudent() {
    UUID studentId = UUID.randomUUID();
    UUID purchaseId = UUID.randomUUID();
    when(purchases.findByTenantIdAndStudentIdAndId(TENANT, studentId, purchaseId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> TenantContext.runAs(TENANT, () -> query.detailForStudent(studentId, purchaseId)))
        .isInstanceOf(PurchaseNotFoundException.class)
        .hasMessage("Purchase not found: " + purchaseId);
    verify(purchases).findByTenantIdAndStudentIdAndId(TENANT, studentId, purchaseId);
  }
}
