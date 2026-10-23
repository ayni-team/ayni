package pe.ayni.sessions.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.sessions.domain.model.PresenceCheck;
import pe.ayni.sessions.domain.model.PresenceCheckUnavailable;
import pe.ayni.sessions.domain.model.PresenceResendRefused;
import pe.ayni.sessions.domain.model.WrongPresenceCode;

/** US54 without Spring: a code expires, counts wrong attempts, and is dead after five. */
class PresenceCheckTest {

  private static final Instant ISSUED = Instant.parse("2026-09-30T20:05:00Z");
  private static final String CODE = "042917";

  private static PresenceCheck issued() {
    return PresenceCheck.issue(
        UUID.randomUUID(), "UPC", UUID.randomUUID(), UUID.randomUUID(), CODE, ISSUED);
  }

  @Test
  @DisplayName("a code is six digits, leading zeros included")
  void aCodeIsSixDigits() {

    Set<String> codes = new HashSet<>();
    for (int i = 0; i < 200; i++) {
      codes.add(PresenceCheck.newCode());
    }

    assertThat(codes).allMatch(code -> code.matches("\\d{6}"));
    assertThat(codes.size()).as("random, not a counter").isGreaterThan(190);
  }

  @Test
  @DisplayName("a code expires fifteen minutes after it is issued, with five attempts")
  void aCodeLastsFifteenMinutes() {

    PresenceCheck check = issued();

    assertThat(check.getExpiresAt()).isEqualTo(ISSUED.plus(Duration.ofMinutes(15)));
    assertThat(check.attemptsLeft()).isEqualTo(5);
    assertThat(check.isConfirmed()).isFalse();
  }

  @Test
  @DisplayName("the right code confirms presence, and typing it again changes nothing")
  void theRightCodeConfirms() {

    PresenceCheck check = issued();
    Instant typed = ISSUED.plus(Duration.ofMinutes(2));

    assertThat(check.confirm(CODE, typed)).isTrue();
    assertThat(check.getConfirmedAt()).isEqualTo(typed);

    // Not even a wrong code spends an attempt once presence is confirmed.
    assertThat(check.confirm("000000", typed.plusSeconds(30))).isFalse();
    assertThat(check.getConfirmedAt()).isEqualTo(typed);
    assertThat(check.attemptsLeft()).isEqualTo(5);
  }

  @Test
  @DisplayName("a wrong code spends an attempt and says how many are left")
  void aWrongCodeSpendsAnAttempt() {

    PresenceCheck check = issued();

    assertThatThrownBy(() -> check.confirm("123456", ISSUED.plusSeconds(60)))
        .isInstanceOf(WrongPresenceCode.class)
        .hasMessage("That is not the code you were sent. Attempts left: 4");
    assertThat(check.getAttempts()).isEqualTo(1);
    assertThat(check.isConfirmed()).isFalse();
  }

  @Test
  @DisplayName("after five wrong codes the code is useless, the right one included")
  void fiveWrongCodesKillIt() {

    PresenceCheck check = issued();
    for (int attempt = 1; attempt < 5; attempt++) {
      assertThatThrownBy(() -> check.confirm("123456", ISSUED.plusSeconds(60)))
          .isInstanceOf(WrongPresenceCode.class);
    }
    assertThatThrownBy(() -> check.confirm("123456", ISSUED.plusSeconds(60)))
        .isInstanceOf(WrongPresenceCode.class)
        .hasMessage("That is not the code you were sent, and it can no longer be used");

    assertThatThrownBy(() -> check.confirm(CODE, ISSUED.plusSeconds(90)))
        .isInstanceOf(PresenceCheckUnavailable.class)
        .hasMessageContaining("can no longer be used");
    assertThat(check.getAttempts()).isEqualTo(5);
    assertThat(check.isConfirmed()).isFalse();
  }

  @Test
  @DisplayName("an expired code is refused without spending an attempt")
  void anExpiredCodeIsRefused() {

    PresenceCheck check = issued();

    assertThatThrownBy(() -> check.confirm(CODE, ISSUED.plus(Duration.ofMinutes(15))))
        .isInstanceOf(PresenceCheckUnavailable.class)
        .hasMessageContaining("expired");
    assertThat(check.getAttempts()).isZero();
    assertThat(check.isConfirmed()).isFalse();
  }

  // US54-T4: resending the code

  private static final int MAX_RESENDS = 3;
  private static final Duration INTERVAL = Duration.ofSeconds(60);

  @Test
  @DisplayName("a resend replaces the code, which lives fifteen minutes from the resend")
  void aResendReplacesTheCode() {

    PresenceCheck check = issued();
    Instant resent = ISSUED.plus(Duration.ofMinutes(10));

    String fresh = check.reissue(resent, MAX_RESENDS, INTERVAL);

    assertThat(fresh).matches("\\d{6}");
    assertThat(check.getIssuedAt()).isEqualTo(resent);
    assertThat(check.getExpiresAt()).isEqualTo(resent.plus(Duration.ofMinutes(15)));
    assertThat(check.getResendCount()).isEqualTo(1);
    assertThat(check.getLastResentAt()).isEqualTo(resent);
    // The old code would have expired by now; the new one is typed in without trouble.
    assertThat(check.confirm(fresh, resent.plusSeconds(30))).isTrue();
  }

  @Test
  @DisplayName("once a code was sent again the old one is a wrong code, even by chance")
  void theOldCodeStopsWorking() {

    // A new code never equals the one it replaces, so this holds on every run, not on most.
    for (int run = 0; run < 300; run++) {
      PresenceCheck check = issued();
      String fresh = check.reissue(ISSUED.plusSeconds(60), MAX_RESENDS, INTERVAL);

      assertThat(fresh).isNotEqualTo(CODE);
      assertThatThrownBy(() -> check.confirm(CODE, ISSUED.plusSeconds(90)))
          .isInstanceOf(WrongPresenceCode.class);
      assertThat(check.isConfirmed()).isFalse();
    }
  }

  @Test
  @DisplayName("a resend keeps the attempts already spent")
  void aResendKeepsTheAttempts() {

    PresenceCheck check = issued();
    assertThatThrownBy(() -> check.confirm("123456", ISSUED.plusSeconds(30)))
        .isInstanceOf(WrongPresenceCode.class);
    assertThatThrownBy(() -> check.confirm("123456", ISSUED.plusSeconds(40)))
        .isInstanceOf(WrongPresenceCode.class);

    check.reissue(ISSUED.plusSeconds(60), MAX_RESENDS, INTERVAL);

    assertThat(check.getAttempts()).isEqualTo(2);
    assertThat(check.attemptsLeft()).isEqualTo(3);
  }

  @Test
  @DisplayName("a code is sent again at most three times, and the first needs no wait")
  void aCodeIsSentAgainAtMostThreeTimes() {

    PresenceCheck check = issued();
    // The first resend has no predecessor: right after the issue is fine.
    check.reissue(ISSUED.plusSeconds(1), MAX_RESENDS, INTERVAL);
    check.reissue(ISSUED.plusSeconds(61), MAX_RESENDS, INTERVAL);
    check.reissue(ISSUED.plusSeconds(121), MAX_RESENDS, INTERVAL);

    assertThatThrownBy(() -> check.reissue(ISSUED.plus(Duration.ofHours(1)), MAX_RESENDS, INTERVAL))
        .isInstanceOf(PresenceResendRefused.class)
        .hasMessageContaining("3 times");
    assertThat(check.getResendCount()).isEqualTo(3);
  }

  @Test
  @DisplayName("two resends less than the interval apart are refused, at the interval it passes")
  void twoResendsNeedTheIntervalBetweenThem() {

    PresenceCheck check = issued();
    Instant first = ISSUED.plusSeconds(10);
    check.reissue(first, MAX_RESENDS, INTERVAL);

    assertThatThrownBy(() -> check.reissue(first.plusSeconds(59), MAX_RESENDS, INTERVAL))
        .isInstanceOf(PresenceResendRefused.class)
        .hasMessageContaining(first.plus(INTERVAL).toString());
    assertThat(check.getResendCount()).isEqualTo(1);

    check.reissue(first.plus(INTERVAL), MAX_RESENDS, INTERVAL);
    assertThat(check.getResendCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("a confirmed presence and a code with no attempts left cannot be sent again")
  void aCodeThatCannotBeUsedCannotBeSentAgain() {

    PresenceCheck confirmed = issued();
    confirmed.confirm(CODE, ISSUED.plusSeconds(20));
    assertThatThrownBy(() -> confirmed.reissue(ISSUED.plusSeconds(90), MAX_RESENDS, INTERVAL))
        .isInstanceOf(PresenceResendRefused.class)
        .hasMessageContaining("already confirmed");

    PresenceCheck spent = issued();
    for (int attempt = 1; attempt <= 5; attempt++) {
      assertThatThrownBy(() -> spent.confirm("123456", ISSUED.plusSeconds(30)))
          .isInstanceOf(WrongPresenceCode.class);
    }
    assertThatThrownBy(() -> spent.reissue(ISSUED.plusSeconds(90), MAX_RESENDS, INTERVAL))
        .isInstanceOf(PresenceResendRefused.class)
        .hasMessageContaining("can no longer be used");
    assertThat(spent.getAttempts()).isEqualTo(5);
    assertThat(spent.getResendCount()).isZero();
  }
}
