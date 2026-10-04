package pe.ayni.wallet;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.shared.domain.CreditType;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.events.CreditsExpiring;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.wallet.application.ExpireCredits;
import pe.ayni.wallet.application.NotifyExpiringCredits;

/** US26's wallet behavior against PostgreSQL; notifications delivery remains in that module. */
@SpringBootTest
@RecordApplicationEvents
class WalletCreditExpiryNoticeAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = WalletTestDatabase.INSTANCE;

  private static final String UPC = "UPC";

  private final UUID student = UUID.randomUUID();

  @Autowired private WalletApi wallet;
  @Autowired private NotifyExpiringCredits notifyExpiringCredits;
  @Autowired private ExpireCredits expireCredits;
  @Autowired private ApplicationEvents events;
  @Autowired private JdbcTemplate jdbc;

  @Test
  @DisplayName("An advance notice reports the remaining amount and expiry date")
  void anAdvanceNoticeReportsTheRemainingAmountAndExpiryDate() {
    Instant expiresAt = Instant.now().plus(Duration.ofDays(5)).truncatedTo(ChronoUnit.MICROS);
    grant(5, CreditType.ALLOCATED, expiresAt);

    evaluateNotices();

    List<CreditsExpiring> notices = noticesForStudent();
    assertThat(notices).hasSize(1);
    assertThat(notices.getFirst().amount()).isEqualTo(Credits.of(5));
    assertThat(notices.getFirst().expiresAt()).isEqualTo(expiresAt);
  }

  @Test
  @DisplayName("Expired credits are removed from the balance and recorded in history")
  void expiredCreditsAreRemovedFromBalanceAndRecordedInHistory() {
    grant(5, CreditType.SEED, Instant.now().minus(Duration.ofDays(1)));

    TenantContext.runAs(UPC, () -> expireCredits.forCurrentUniversity());

    assertThat(balance()).isEqualTo(Credits.ZERO);
    assertThat(expiryMovementAmount()).isEqualTo(5);
  }

  @Test
  @DisplayName("Earned and purchased credits do not trigger expiry notices")
  void earnedAndPurchasedCreditsDoNotTriggerExpiryNotices() {
    grant(4, CreditType.EARNED, null);
    grant(2, CreditType.PURCHASED, null);

    evaluateNotices();

    assertThat(noticesForStudent()).isEmpty();
  }

  @Test
  @DisplayName("A credit group is notified only once")
  void creditGroupIsNotifiedOnlyOnce() {
    grant(5, CreditType.SEED, Instant.now().plus(Duration.ofDays(5)));

    evaluateNotices();
    evaluateNotices();

    assertThat(noticesForStudent()).hasSize(1);
  }

  private void grant(int amount, CreditType type, Instant expiresAt) {
    TenantContext.runAs(
        UPC, () -> wallet.grant(student, Credits.of(amount), type, expiresAt, UUID.randomUUID()));
  }

  private void evaluateNotices() {
    TenantContext.runAs(UPC, () -> notifyExpiringCredits.forCurrentUniversity());
  }

  private Credits balance() {
    Credits[] result = new Credits[1];
    TenantContext.runAs(UPC, () -> result[0] = wallet.balanceOf(student).available());
    return result[0];
  }

  private int expiryMovementAmount() {
    Integer amount =
        jdbc.queryForObject(
            """
            select coalesce(sum(entry.amount), 0)
            from wallet.ledger_entries entry
            join wallet.credit_accounts account
              on account.tenant_id = entry.tenant_id and account.id = entry.account_id
            where account.tenant_id = ? and account.user_id = ? and entry.reason = 'EXPIRY'
            """,
            Integer.class,
            UPC,
            student);
    return amount == null ? 0 : amount;
  }

  private List<CreditsExpiring> noticesForStudent() {
    return events.stream(CreditsExpiring.class)
        .filter(event -> event.userId().equals(student))
        .toList();
  }
}
