package pe.ayni.notifications.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.notifications.application.Email;
import pe.ayni.notifications.application.EmailDelivery;
import pe.ayni.notifications.application.EmailNotDelivered;

/** The rule of which server carries an email, without Spring and without a mail server. */
class RoutingEmailDeliveryTest {

  private static final String ALLOWED = "lead@example.org";

  private final Recording main = new Recording();
  private final Recording real = new Recording();
  private final RoutingEmailDelivery routing =
      new RoutingEmailDelivery(main, real, Set.of(ALLOWED));

  private static Email to(String address) {
    return new Email(address, "Subject", "Body");
  }

  @Test
  @DisplayName("sends an allowed address through the real relay only")
  void sendsAnAllowedAddressThroughTheRealRelayOnly() {
    routing.send(to(ALLOWED));

    assertThat(real.sent).extracting(Email::to).containsExactly(ALLOWED);
    assertThat(main.sent).isEmpty();
  }

  @Test
  @DisplayName("sends any other address through the main server only")
  void sendsAnyOtherAddressThroughTheMainServerOnly() {
    routing.send(to("someone@elsewhere.test"));

    assertThat(main.sent).hasSize(1);
    assertThat(real.sent).isEmpty();
  }

  @Test
  @DisplayName("never compares the domain: another address of the same domain stays on the main server")
  void neverComparesTheDomain() {
    routing.send(to("invented.student@example.org"));

    assertThat(main.sent).hasSize(1);
    assertThat(real.sent).isEmpty();
  }

  @Test
  @DisplayName("ignores letter case and surrounding spaces in the recipient")
  void ignoresLetterCaseAndSurroundingSpaces() {
    routing.send(to("  LEAD@Example.ORG "));

    assertThat(real.sent).hasSize(1);
    assertThat(main.sent).isEmpty();
  }

  @Test
  @DisplayName("does not hand an email to the main server when the real relay fails")
  void doesNotFallBackToTheMainServerWhenTheRealRelayFails() {
    real.failWith(new EmailNotDelivered("The real relay did not take the email", null));

    assertThatThrownBy(() -> routing.send(to(ALLOWED))).isInstanceOf(EmailNotDelivered.class);

    assertThat(main.sent).isEmpty();
  }

  @Test
  @DisplayName("normalises an address the way the list is compared")
  void normalisesAnAddress() {
    assertThat(RoutingEmailDelivery.normalise("  Lead@Example.ORG ")).isEqualTo(ALLOWED);
  }

  private static final class Recording implements EmailDelivery {
    private final List<Email> sent = new ArrayList<>();
    private EmailNotDelivered failure;

    void failWith(EmailNotDelivered failure) {
      this.failure = failure;
    }

    @Override
    public void send(Email email) {
      if (failure != null) {
        throw failure;
      }
      sent.add(email);
    }
  }
}
