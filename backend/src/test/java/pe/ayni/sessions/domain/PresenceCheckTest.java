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
}
