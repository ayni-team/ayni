package pe.ayni.notifications.application;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import pe.ayni.shared.events.AccessRequested;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * What notifications does about things that happened elsewhere: it tells the people concerned.
 *
 * <p>Nothing depends on this module and it depends on nothing but events, as the backend guide
 * says. Each listener runs after the publisher's transaction commits, so a link whose request
 * rolled back is never sent.
 *
 * <p>The university is bound from the event before anything is done; it may be none, for a
 * platform administrator.
 */
@Component
class NotificationEventListeners {

  private final DeliverAccessLinkUseCase deliverAccessLink;

  NotificationEventListeners(DeliverAccessLinkUseCase deliverAccessLink) {
    this.deliverAccessLink = deliverAccessLink;
  }

  @ApplicationModuleListener
  void on(AccessRequested event) {
    TenantContext.runAs(event.tenantId(), () -> deliverAccessLink.execute(event));
  }
}
