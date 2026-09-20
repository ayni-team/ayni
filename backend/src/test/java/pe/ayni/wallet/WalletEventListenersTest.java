package pe.ayni.wallet;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.shared.domain.CreditType;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.events.BookingCancelled;
import pe.ayni.shared.events.PurchaseConfirmed;
import pe.ayni.shared.events.SessionCompleted;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * What wallet does when something happens in another module.
 *
 * <p>The events are published inside a transaction, as the module that owns them would: the
 * listeners run once it commits, so a session that is rolled back never credits anybody. Publishing
 * them outside one would silently do nothing, which is worth knowing before writing a listener of
 * your own.
 */
@SpringBootTest
class WalletEventListenersTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = WalletTestDatabase.INSTANCE;

  private static final String UPC = "UPC";

  private final UUID student = UUID.randomUUID();
  private final UUID tutor = UUID.randomUUID();

  @Autowired private WalletApi wallet;
  @Autowired private ApplicationEventPublisher events;
  @Autowired private TransactionTemplate transactions;

  @Test
  @DisplayName("a completed session credits the tutor with credits that never expire")
  void aCompletedSessionCreditsTheTutor() {

    publish(
        new SessionCompleted(
            UPC,
            UUID.randomUUID(),
            UUID.randomUUID(),
            tutor,
            student,
            UUID.randomUUID(),
            Credits.of(2),
            Instant.now()));

    assertThat(balanceOf(tutor)).isEqualTo(Credits.of(2));
    // Earned, so they count towards recognition and nothing takes them away.
    assertThat(earnedTotalOf(tutor)).isEqualTo(Credits.of(2));
  }

  @Test
  @DisplayName("a cancelled booking returns the credits it took")
  void aCancelledBookingReturnsTheCredits() {

    UUID booking = UUID.randomUUID();

    TenantContext.runAs(
        UPC,
        () -> {
          wallet.grant(
              student,
              Credits.of(6),
              CreditType.SEED,
              Instant.now().plus(Duration.ofDays(30)),
              UUID.randomUUID());
          wallet.charge(student, Credits.of(4), booking);
        });

    assertThat(balanceOf(student)).isEqualTo(Credits.of(2));

    publish(
        new BookingCancelled(
            UPC,
            booking,
            student,
            tutor,
            List.of(UUID.randomUUID()),
            BookingCancelled.CancelledBy.STUDENT,
            false,
            Instant.now()));

    assertThat(balanceOf(student)).isEqualTo(Credits.of(6));
  }

  @Test
  @DisplayName("a confirmed purchase places credits that never expire")
  void aConfirmedPurchasePlacesCredits() {

    publish(
        new PurchaseConfirmed(UPC, UUID.randomUUID(), student, Credits.of(3), Instant.now()));

    assertThat(balanceOf(student)).isEqualTo(Credits.of(3));
    // Paid for, so they do not count towards recognition however many there are.
    assertThat(earnedTotalOf(student)).isEqualTo(Credits.ZERO);
  }

  /** Publishes as the owning module would: inside a transaction that then commits. */
  private void publish(Object event) {
    transactions.executeWithoutResult(status -> events.publishEvent(event));
  }

  private Credits balanceOf(UUID userId) {
    Credits[] available = new Credits[1];
    TenantContext.runAs(UPC, () -> available[0] = wallet.balanceOf(userId).available());
    return available[0];
  }

  private Credits earnedTotalOf(UUID userId) {
    Credits[] earned = new Credits[1];
    TenantContext.runAs(UPC, () -> earned[0] = wallet.earnedTotal(userId));
    return earned[0];
  }
}
