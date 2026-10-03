package pe.ayni.reputation.application;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import pe.ayni.shared.events.SessionAbandoned;
import pe.ayni.shared.tenancy.TenantContext;

/** Updates reputation's compliance history from abandoned sessions. */
@Component
class TutorNoShowEventListener {

  private final RecordTutorNoShow recordTutorNoShow;

  TutorNoShowEventListener(RecordTutorNoShow recordTutorNoShow) {
    this.recordTutorNoShow = recordTutorNoShow;
  }

  @ApplicationModuleListener
  void on(SessionAbandoned event) {
    TenantContext.runAs(event.tenantId(), () -> recordTutorNoShow.record(event));
  }
}
