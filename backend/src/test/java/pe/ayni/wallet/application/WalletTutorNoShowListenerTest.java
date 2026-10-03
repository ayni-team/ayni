package pe.ayni.wallet.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.shared.events.SessionAbandoned;
import pe.ayni.shared.tenancy.TenantContext;

class WalletTutorNoShowListenerTest {

  private static final String TENANT = "UPC";
  private static final UUID SESSION = UUID.randomUUID();
  private static final UUID BOOKING = UUID.randomUUID();
  private static final UUID TUTOR = UUID.randomUUID();
  private static final UUID STUDENT = UUID.randomUUID();

  @Test
  @DisplayName("refunds a student who checked in when the tutor is a no-show")
  void refundsAttendingStudent() {
    GrantCredits grant = mock(GrantCredits.class);
    RefundBooking refund = mock(RefundBooking.class);
    WalletEventListeners listener = new WalletEventListeners(grant, refund);

    TenantContext.runAs(TENANT, () -> listener.on(event(true)));

    verify(refund).refund(BOOKING);
  }

  @Test
  @DisplayName("does not refund when neither participant checked in")
  void doesNotRefundTwoAbsences() {
    GrantCredits grant = mock(GrantCredits.class);
    RefundBooking refund = mock(RefundBooking.class);
    WalletEventListeners listener = new WalletEventListeners(grant, refund);

    TenantContext.runAs(TENANT, () -> listener.on(event(false)));

    verify(refund, never()).refund(BOOKING);
  }

  private static SessionAbandoned event(boolean studentCheckedIn) {
    return new SessionAbandoned(
        TENANT,
        SESSION,
        BOOKING,
        TUTOR,
        STUDENT,
        UUID.randomUUID(),
        studentCheckedIn,
        Instant.parse("2026-09-30T20:10:00Z"));
  }
}
