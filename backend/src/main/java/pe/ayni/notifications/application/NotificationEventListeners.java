package pe.ayni.notifications.application;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import pe.ayni.shared.events.AccessRequested;
import pe.ayni.shared.events.PresenceCodeIssued;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * What notifications does about things that happened elsewhere: it tells the people concerned.
 *
 * <p>Nothing depends on this module, as the backend guide says. It reacts to events, and asks
 * identity only for the address of a person an event names. Each listener runs after the
 * publisher's transaction commits, so a link or a code whose transaction rolled back is never sent.
 *
 * <p>The university is bound from the event before anything is done; it may be none, for a
 * platform administrator.
 */
@Component
class NotificationEventListeners {

  private final DeliverAccessLinkUseCase deliverAccessLink;
  private final DeliverPresenceCodeUseCase deliverPresenceCode;

  NotificationEventListeners(
      DeliverAccessLinkUseCase deliverAccessLink, DeliverPresenceCodeUseCase deliverPresenceCode) {
    this.deliverAccessLink = deliverAccessLink;
    this.deliverPresenceCode = deliverPresenceCode;
  }

  @ApplicationModuleListener
  void on(AccessRequested event) {
    TenantContext.runAs(event.tenantId(), () -> deliverAccessLink.execute(event));
  }

  @ApplicationModuleListener
  void on(PresenceCodeIssued event) {
    TenantContext.runAs(event.tenantId(), () -> deliverPresenceCode.execute(event));
  }
}
