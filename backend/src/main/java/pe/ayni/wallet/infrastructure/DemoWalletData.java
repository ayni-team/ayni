package pe.ayni.wallet.infrastructure;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import pe.ayni.shared.domain.CreditType;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.wallet.WalletApi;
import pe.ayni.wallet.application.ExpireCredits;

/**
 * Two wallets to look at while identity does not exist.
 *
 * <p>Nothing grants credits today: the university's policy arrives with {@code StudentActivated},
 * which identity will publish. Until then the endpoints would answer an empty wallet to everyone
 * and there would be nothing to review, so under the {@code dev} profile the module fills two
 * accounts that between them show everything US23 asks for.
 *
 * <p>It is written through the published interface and the real use cases, never with direct
 * inserts: demonstration data that took a shortcut the application cannot take is data that proves
 * nothing. The expiries below are produced by running the real expiry.
 *
 * <p>It runs once. A second start finds the accounts already there and leaves them alone, so
 * restarting the backend does not double anybody's credits.
 */
@Component
@Profile("dev")
class DemoWalletData implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DemoWalletData.class);

  private static final String TENANT = "UPC";

  /** A student with credits of every origin, a cancelled booking and an expired grant. */
  private static final UUID ANA = UUID.fromString("11111111-1111-4111-8111-111111111111");

  /** A student whose only grant expired: an empty wallet with a history. */
  private static final UUID BRUNO = UUID.fromString("22222222-2222-4222-8222-222222222222");

  private static final UUID CREDIT_POLICY = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");
  private static final UUID CANCELLED_BOOKING =
      UUID.fromString("9f1c2d3e-4b5a-6c7d-8e9f-0a1b2c3d4e5f");
  private static final UUID KEPT_BOOKING = UUID.fromString("bbbbbbbb-0000-4000-8000-000000000002");
  private static final UUID TAUGHT_SESSION = UUID.fromString("cccccccc-0000-4000-8000-000000000003");
  private static final UUID PURCHASE = UUID.fromString("dddddddd-0000-4000-8000-000000000004");

  private final WalletApi wallet;
  private final ExpireCredits expireCredits;
  private final CreditAccountRepository accounts;
  private final Clock clock;

  DemoWalletData(
      WalletApi wallet,
      ExpireCredits expireCredits,
      CreditAccountRepository accounts,
      Clock clock) {
    this.wallet = wallet;
    this.expireCredits = expireCredits;
    this.accounts = accounts;
    this.clock = clock;
  }

  @Override
  public void run(ApplicationArguments args) {
    TenantContext.runAs(TENANT, this::fillWallets);
  }

  /**
   * A whole day away, at midnight UTC.
   *
   * <p>A credit policy expires on a date, not at whatever time of day the container happened to
   * start, and a round date is easier to read in Swagger.
   */
  private static Instant inDays(Instant now, int days) {
    return now.plus(Duration.ofDays(days)).truncatedTo(ChronoUnit.DAYS);
  }

  private void fillWallets() {
    if (accounts.existsByTenantIdAndUserId(TENANT, ANA)) {
      return;
    }

    Instant now = clock.instant();

    // Ana. The university's initial grant, and a targeted allocation on top of it.
    wallet.grant(ANA, Credits.of(8), CreditType.SEED, inDays(now, 30), CREDIT_POLICY);
    wallet.grant(ANA, Credits.of(4), CreditType.ALLOCATED, inDays(now, 90), CREDIT_POLICY);
    // A grant from last term that nobody spent. It is already dead; the expiry below records it.
    wallet.grant(ANA, Credits.of(3), CreditType.SEED, inDays(now, -5), CREDIT_POLICY);

    // Two bookings, one of them cancelled. The refund goes back to the group it came from.
    wallet.charge(ANA, Credits.of(2), CANCELLED_BOOKING);
    wallet.charge(ANA, Credits.of(3), KEPT_BOOKING);
    wallet.refund(CANCELLED_BOOKING);

    // A session taught and a purchase: neither of these ever expires, and only the first one
    // counts towards recognition.
    wallet.grant(ANA, Credits.of(4), CreditType.EARNED, null, TAUGHT_SESSION);
    wallet.grant(ANA, Credits.of(2), CreditType.PURCHASED, null, PURCHASE);

    // Bruno. One grant, which expired before he used any of it.
    wallet.grant(BRUNO, Credits.of(5), CreditType.SEED, inDays(now, -10), CREDIT_POLICY);

    // The real nightly job, run once by hand, so the expired grants leave the balance and appear
    // in the history as the entries that took them away.
    Credits expired = expireCredits.forCurrentUniversity();

    log.info(
        "Demo wallets ready for {}: {} has credits of four origins, {} has an empty wallet, "
            + "{} credits expired",
        TENANT,
        ANA,
        BRUNO,
        expired.amount());
  }
}
