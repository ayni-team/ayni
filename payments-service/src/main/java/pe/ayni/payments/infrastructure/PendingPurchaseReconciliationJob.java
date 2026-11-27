package pe.ayni.payments.infrastructure;

import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pe.ayni.payments.application.PendingPurchaseReconciler;

/** Eager even under lazy initialization: a job nobody asks for would otherwise never be scheduled. */
@Component
@Lazy(false)
class PendingPurchaseReconciliationJob {

  private final PendingPurchaseReconciler reconciler;

  PendingPurchaseReconciliationJob(PendingPurchaseReconciler reconciler) {
    this.reconciler = reconciler;
  }

  // The first run waits one delay too, instead of running the moment the service starts.
  @Scheduled(
      initialDelayString = "${ayni.payments.reconciliation-delay:PT1M}",
      fixedDelayString = "${ayni.payments.reconciliation-delay:PT1M}")
  void reconcilePendingPurchases() {
    reconciler.reconcile();
  }
}
