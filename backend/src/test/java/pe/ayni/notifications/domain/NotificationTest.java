package pe.ayni.notifications.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.notifications.domain.model.Notification;
import pe.ayni.notifications.domain.model.NotificationKind;

class NotificationTest {

  private static final Instant NOW = Instant.parse("2026-09-29T15:00:00Z");

  private Notification pending() {
    return Notification.pending(
        UUID.randomUUID(),
        "UPC",
        null,
        "u202400001@upc.edu.pe",
        NotificationKind.ACCESS_LINK,
        Map.of("purpose", "LOGIN"),
        NOW);
  }

  @Test
  @DisplayName("a notice starts pending and is marked sent once")
  void aNoticeIsSentOnce() {

    Notification notice = pending();
    assertThat(notice.isPending()).isTrue();

    notice.markSent(NOW.plusSeconds(1));

    assertThat(notice.isPending()).isFalse();
    assertThat(notice.getSentAt()).isEqualTo(NOW.plusSeconds(1));
    assertThatThrownBy(() -> notice.markSent(NOW)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> notice.markFailed("late")).isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("a failed notice keeps why, cut to what the column holds")
  void aFailedNoticeKeepsWhy() {

    Notification notice = pending();

    notice.markFailed("x".repeat(800));

    assertThat(notice.getFailedReason()).hasSize(500);
    assertThat(notice.getSentAt()).isNull();
    assertThatThrownBy(() -> notice.markSent(NOW)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("a failure with no reason still says something")
  void aFailureWithoutReason() {

    Notification notice = pending();

    notice.markFailed(" ");

    assertThat(notice.getFailedReason()).isEqualTo("Unknown delivery failure");
  }

  @Test
  @DisplayName("an activation notice may have no recipient account, but always an address")
  void aRecipientWithoutAccount() {

    Notification notice = pending();

    assertThat(notice.getRecipientId()).isNull();
    assertThat(notice.getRecipientEmail()).isEqualTo("u202400001@upc.edu.pe");
    assertThatThrownBy(
            () ->
                Notification.pending(
                    UUID.randomUUID(), "UPC", null, null, NotificationKind.ACCESS_LINK,
                    Map.of(), NOW))
        .isInstanceOf(NullPointerException.class);
  }
}
