package pe.ayni.wallet.domain;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import pe.ayni.shared.domain.CreditType;
import pe.ayni.shared.domain.Credits;
import pe.ayni.wallet.domain.model.CreditLot;
import pe.ayni.wallet.domain.model.CreditSource;

/**
 * Groups of credits for the tests of the rules, built around a clock that does not move.
 *
 * <p>Every rule in wallet is about time: what expires first, what has expired already, what never
 * expires. Read from the real clock, none of them could be tested without waiting, so the tests
 * decide what "now" is and the rules are given it.
 */
final class WalletFixtures {

  static final Instant NOW = Instant.parse("2026-09-20T12:00:00Z");
  static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  static final String UPC = "UPC";
  static final UUID ACCOUNT = UUID.fromString("11111111-1111-4111-8111-111111111111");

  private WalletFixtures() {}

  /** A grant from the university that expires in the given number of days. */
  static CreditLot seedExpiringInDays(int amount, int days) {
    return CreditLot.granted(
        UPC,
        ACCOUNT,
        Credits.of(amount),
        CreditType.SEED,
        NOW.plusSeconds(days * 86_400L),
        CreditSource.POLICY,
        UUID.randomUUID(),
        NOW.minusSeconds(86_400L));
  }

  /** A grant that died before now, still holding credits nobody spent. */
  static CreditLot seedExpiredDaysAgo(int amount, int days) {
    return CreditLot.granted(
        UPC,
        ACCOUNT,
        Credits.of(amount),
        CreditType.SEED,
        NOW.minusSeconds(days * 86_400L),
        CreditSource.POLICY,
        UUID.randomUUID(),
        NOW.minusSeconds(365 * 86_400L));
  }

  /** Credits earned by teaching. They never expire. */
  static CreditLot earned(int amount) {
    return CreditLot.granted(
        UPC,
        ACCOUNT,
        Credits.of(amount),
        CreditType.EARNED,
        null,
        CreditSource.SESSION,
        UUID.randomUUID(),
        NOW.minusSeconds(2 * 86_400L));
  }

  /** Credits the student paid for. They never expire either. */
  static CreditLot purchased(int amount) {
    return CreditLot.granted(
        UPC,
        ACCOUNT,
        Credits.of(amount),
        CreditType.PURCHASED,
        null,
        CreditSource.PURCHASE,
        UUID.randomUUID(),
        NOW.minusSeconds(3 * 86_400L));
  }
}
