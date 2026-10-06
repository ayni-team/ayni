package pe.ayni.payments.infrastructure;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pe.ayni.payments.application.PendingPurchaseReconciler;

@Component
class PendingPurchaseReconciliationJob {

  private final PendingPurchaseReconciler reconciler;

  PendingPurchaseReconciliationJob(PendingPurchaseReconciler reconciler) {
    this.reconciler = reconciler;
  }

  @Scheduled(fixedDelayString = "${ayni.payments.reconciliation-delay:PT1M}")
  void reconcilePendingPurchases() {
    reconciler.reconcile();
  }
}
