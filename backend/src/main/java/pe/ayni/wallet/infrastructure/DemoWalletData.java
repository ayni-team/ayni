package pe.ayni.wallet.infrastructure;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import pe.ayni.shared.demo.DemoCast;
import pe.ayni.shared.demo.DemoCast.Person;
import pe.ayni.shared.domain.CreditType;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.wallet.WalletApi;
import pe.ayni.wallet.application.ExpireCredits;
import pe.ayni.wallet.application.NotifyExpiringCredits;

/**
 * The wallets of the people of the demonstration.
 *
 * <p>In normal operation {@code StudentActivated} grants the university's baseline credits. The
 * people of {@code DemoCast} are activated without it, so under the {@code dev} profile this runner
 * gives them their credits instead: Ana every credit origin, an expiry and a refund; Bruno an empty
 * wallet with a history; and in each university a student with no credits, one with a single
 * credit left, one with five, one with fifteen, one with credits of three origins, one whose credits
 * expire in two days (so the warning shows), and the tutors who also book something to book with.
 * The balances are those of the first start; the bookings of {@code DemoBookingData} and the history
 * of the {@code demo} profile spend some of them, and {@code docs/demo.md} has the table.
 *
 * <p>It is written through the published interface and the real use cases, never with direct
 * inserts: demonstration data that took a shortcut the application cannot take is data that proves
 * nothing. The expiries below are produced by running the real expiry.
 *
 * <p>It runs once for each person. A second start finds their account already there and leaves it
 * alone, so restarting the backend does not double anybody's credits. The expiry and its warnings
 * are the real nightly jobs, run once by hand at the end, so that the warning of the credits about
 * to expire is already in the person's notices.
 */
@Component
@Profile("dev")
class DemoWalletData implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DemoWalletData.class);

  private static final String TENANT = DemoCast.UPC;

  /** A student with credits of every origin, a cancelled booking and an expired grant. */
  private static final UUID ANA = DemoCast.ANA.id();

  /** A student whose only grant expired: an empty wallet with a history. */
  private static final UUID BRUNO = DemoCast.BRUNO.id();

  private static final UUID CREDIT_POLICY = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");
  private static final UUID USIL_CREDIT_POLICY =
      UUID.fromString("aaaaaaaa-0000-4000-8000-000000000002");
  private static final UUID CANCELLED_BOOKING =
      UUID.fromString("9f1c2d3e-4b5a-6c7d-8e9f-0a1b2c3d4e5f");
  private static final UUID KEPT_BOOKING = UUID.fromString("bbbbbbbb-0000-4000-8000-000000000002");
  private static final UUID TAUGHT_SESSION = UUID.fromString("cccccccc-0000-4000-8000-000000000003");
  private static final UUID PURCHASE = UUID.fromString("dddddddd-0000-4000-8000-000000000004");

  private final WalletApi wallet;
  private final ExpireCredits expireCredits;
  private final NotifyExpiringCredits notifyExpiringCredits;
  private final CreditAccountRepository accounts;
  private final Clock clock;

  DemoWalletData(
      WalletApi wallet,
      ExpireCredits expireCredits,
      NotifyExpiringCredits notifyExpiringCredits,
      CreditAccountRepository accounts,
      Clock clock) {
    this.wallet = wallet;
    this.expireCredits = expireCredits;
    this.notifyExpiringCredits = notifyExpiringCredits;
    this.accounts = accounts;
    this.clock = clock;
  }

  /** One grant of a person's wallet: how much, of what origin, and in how many days it expires. */
  private record Grant(int credits, CreditType type, Integer expiresInDays) {}

  /** The wallets of the people other than Ana and Bruno, by university. */
  private static final Map<String, Map<Person, List<Grant>>> WALLETS =
      Map.of(
          DemoCast.UPC,
          wallets(
              DemoCast.ANDRES_ZEGARRA, DemoCast.ANA_FLORES, DemoCast.JULIAN_ARCE,
              DemoCast.VALERIA_MEDINA, DemoCast.MILAGROS_CASTILLO, DemoCast.ALEJANDRA_CHAVEZ,
              DemoCast.ISABELLA_TAPIA, DemoCast.DANIELA_SALAS),
          DemoCast.USIL,
          wallets(
              DemoCast.BRUNO_CASTILLO, DemoCast.SEBASTIAN_GUTIERREZ, DemoCast.LUCIA_SALAZAR,
              DemoCast.DIEGO_ESPINOZA, DemoCast.ROCIO_PALOMINO, DemoCast.GABRIEL_NAVARRO,
              DemoCast.PIERO_DELGADO, DemoCast.DANIELA_TAPIA));

  /**
   * The same eight wallets in each university, in this order: no credits (a grant that expired),
   * one credit, five, fifteen, three origins, two credits that expire in two days, and the two tutors
   * who book.
   */
  private static Map<Person, List<Grant>> wallets(
      Person none, Person one, Person five, Person fifteen, Person mixed, Person expiring,
      Person bookingTutor, Person otherBookingTutor) {
    Map<Person, List<Grant>> wallets = new LinkedHashMap<>();
    wallets.put(none, List.of(new Grant(5, CreditType.SEED, -10)));
    wallets.put(one, List.of(new Grant(1, CreditType.SEED, 30)));
    wallets.put(five, List.of(new Grant(5, CreditType.SEED, 30)));
    wallets.put(
        fifteen, List.of(new Grant(5, CreditType.SEED, 30), new Grant(10, CreditType.ALLOCATED, 90)));
    wallets.put(
        mixed,
        List.of(
            new Grant(5, CreditType.SEED, 30),
            new Grant(6, CreditType.ALLOCATED, 90),
            new Grant(3, CreditType.PURCHASED, null)));
    wallets.put(
        expiring,
        List.of(new Grant(2, CreditType.SEED, 2), new Grant(4, CreditType.ALLOCATED, 90)));
    wallets.put(bookingTutor, List.of(new Grant(5, CreditType.ALLOCATED, 90)));
    wallets.put(otherBookingTutor, List.of(new Grant(3, CreditType.ALLOCATED, 90)));
    return wallets;
  }

  @Override
  public void run(ApplicationArguments args) {
    for (String university : List.of(DemoCast.UPC, DemoCast.USIL)) {
      try {
        TenantContext.runAs(university, () -> fill(university));
      } catch (RuntimeException failure) {
        // Demonstration data must not keep the application from starting.
        log.error("Could not fill the demo wallets of {}", university, failure);
      }
    }
  }

  private void fill(String university) {
    if (DemoCast.UPC.equals(university)) {
      fillWallets();
    }
    UUID policy = DemoCast.UPC.equals(university) ? CREDIT_POLICY : USIL_CREDIT_POLICY;
    Instant now = clock.instant();
    int filled = 0;
    for (Map.Entry<Person, List<Grant>> account : WALLETS.get(university).entrySet()) {
      UUID person = account.getKey().id();
      if (accounts.existsByTenantIdAndUserId(university, person)) {
        continue;
      }
      for (Grant grant : account.getValue()) {
        Instant expiresAt = null;
        if (grant.expiresInDays() != null) {
          // The one that is about to expire does so two whole days from now, not at a midnight
          // that might be tomorrow already.
          expiresAt =
              grant.expiresInDays() == 2
                  ? now.plus(Duration.ofDays(2)).truncatedTo(ChronoUnit.HOURS)
                  : inDays(now, grant.expiresInDays());
        }
        UUID source = grant.type() == CreditType.PURCHASED ? UUID.randomUUID() : policy;
        wallet.grant(person, Credits.of(grant.credits()), grant.type(), expiresAt, source);
      }
      filled++;
    }

    // The real nightly jobs, run once by hand: the expired grants leave the balance and appear in
    // the history, and the credits about to expire are announced.
    Credits expired = expireCredits.forCurrentUniversity();
    int warned = notifyExpiringCredits.forCurrentUniversity();
    log.info(
        "Demo wallets of {}: {} filled, {} credits expired, {} expiry warnings",
        university,
        filled,
        expired.amount(),
        warned);
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

    log.info(
        "Demo wallets ready for {}: {} has credits of four origins, {} has an empty wallet",
        TENANT,
        ANA,
        BRUNO);
  }
}
