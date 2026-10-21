package pe.ayni.notifications.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.notifications.domain.model.NotificationKind;

class NotificationKindTest {

  @Test
  @DisplayName("an access link and a presence code travel by email and are not listed")
  void secretsAreNotListed() {

    assertThat(NotificationKind.ACCESS_LINK.isListed()).isFalse();
    assertThat(NotificationKind.PRESENCE_CODE.isListed()).isFalse();
    assertThat(NotificationKind.listed())
        .doesNotContain(NotificationKind.ACCESS_LINK, NotificationKind.PRESENCE_CODE);
  }

  @Test
  @DisplayName("every other kind is a notice of the application")
  void everyOtherKindIsListed() {

    assertThat(NotificationKind.listed())
        .containsExactlyInAnyOrderElementsOf(
            Arrays.stream(NotificationKind.values())
                .filter(
                    kind ->
                        kind != NotificationKind.ACCESS_LINK
                            && kind != NotificationKind.PRESENCE_CODE)
                .toList());
    assertThat(NotificationKind.listed())
        .contains(
            NotificationKind.TUTOR_NO_SHOW,
            NotificationKind.PURCHASE_CONFIRMED,
            NotificationKind.BOOKING_CONFIRMED);
  }
}
