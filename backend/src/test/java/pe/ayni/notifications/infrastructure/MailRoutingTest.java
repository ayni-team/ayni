package pe.ayni.notifications.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetup;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import pe.ayni.notifications.application.Email;
import pe.ayni.notifications.application.EmailDelivery;

/**
 * Who gets a real email, against two real SMTP servers inside the test: the main one, which stands
 * for Mailpit, and the real relay. The application's own mail configuration builds the delivery
 * from properties, so what is proven here is what a deployment gets. One test per scenario of
 * {@code features/notifications-mail-routing.feature}, with the same display name.
 */
class MailRoutingTest {

  private static final int MAIN_PORT = 3025;
  private static final int REAL_PORT = 3026;

  private static final String ALLOWED = "lead@example.org";
  private static final String SAME_DOMAIN = "invented.student@example.org";

  /** The server behind {@code spring.mail.*}, a fresh inbox for every test. */
  @RegisterExtension
  static final GreenMailExtension MAIN = new GreenMailExtension(ServerSetupTest.SMTP);

  /** The real relay. */
  @RegisterExtension
  static final GreenMailExtension REAL =
      new GreenMailExtension(new ServerSetup(REAL_PORT, null, ServerSetup.PROTOCOL_SMTP));

  private final ApplicationContextRunner application =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
          .withUserConfiguration(MailRoutingConfig.class)
          .withPropertyValues("spring.mail.host=localhost", "spring.mail.port=" + MAIN_PORT);

  private final ListAppender<ILoggingEvent> logged = new ListAppender<>();
  private Logger infrastructureLog;

  @BeforeEach
  void listenToTheLog() {
    infrastructureLog =
        (Logger) LoggerFactory.getLogger("pe.ayni.notifications.infrastructure");
    infrastructureLog.setLevel(Level.INFO);
    logged.start();
    infrastructureLog.addAppender(logged);
  }

  @AfterEach
  void stopListeningToTheLog() {
    infrastructureLog.detachAppender(logged);
    logged.stop();
  }

  private static String[] realRelayFor(String allowed) {
    return new String[] {
      "ayni.notifications.real-relay.allowed-recipients=" + allowed,
      "ayni.notifications.real-relay.host=localhost",
      "ayni.notifications.real-relay.port=" + REAL_PORT
    };
  }

  private static void send(AssertableApplicationContext context, String to) {
    context.getBean(EmailDelivery.class).send(new Email(to, "Tu enlace", "Body of the email"));
  }

  private static List<String> recipientsOf(MimeMessage[] messages) throws Exception {
    return java.util.Arrays.stream(messages)
        .map(
            message -> {
              try {
                return message.getAllRecipients()[0].toString();
              } catch (Exception unreadable) {
                throw new IllegalStateException(unreadable);
              }
            })
        .toList();
  }

  @Test
  @DisplayName("An allowed recipient is delivered only through the real relay")
  void anAllowedRecipientIsDeliveredOnlyThroughTheRealRelay() {
    application
        .withPropertyValues(realRelayFor(ALLOWED))
        .run(
            context -> {
              send(context, ALLOWED);

              assertThat(recipientsOf(REAL.getReceivedMessages())).containsExactly(ALLOWED);
              assertThat(MAIN.getReceivedMessages()).isEmpty();
            });
  }

  @Test
  @DisplayName("A recipient that is not allowed is delivered only through the main server")
  void aRecipientThatIsNotAllowedIsDeliveredOnlyThroughTheMainServer() {
    application
        .withPropertyValues(realRelayFor(ALLOWED))
        .run(
            context -> {
              send(context, SAME_DOMAIN);

              assertThat(recipientsOf(MAIN.getReceivedMessages())).containsExactly(SAME_DOMAIN);
              assertThat(REAL.getReceivedMessages()).isEmpty();
            });
  }

  @Test
  @DisplayName("The allowed list is compared without regard to letter case")
  void theAllowedListIsComparedWithoutRegardToLetterCase() {
    application
        .withPropertyValues(realRelayFor(ALLOWED))
        .run(
            context -> {
              send(context, "LEAD@Example.ORG");

              assertThat(REAL.getReceivedMessages()).hasSize(1);
              assertThat(MAIN.getReceivedMessages()).isEmpty();
            });
  }

  @Test
  @DisplayName("Without a real relay every email goes through the main server")
  void withoutARealRelayEveryEmailGoesThroughTheMainServer() {
    application.run(
        context -> {
          send(context, ALLOWED);
          send(context, SAME_DOMAIN);

          assertThat(MAIN.getReceivedMessages()).hasSize(2);
          assertThat(REAL.getReceivedMessages()).isEmpty();
        });
  }

  @Test
  @DisplayName("A real relay configuration that is incomplete stops the startup")
  void aRealRelayConfigurationThatIsIncompleteStopsTheStartup() {
    application
        .withPropertyValues("ayni.notifications.real-relay.allowed-recipients=" + ALLOWED)
        .run(
            context -> {
              assertThat(context).hasFailed();
              String message = rootMessage(context.getStartupFailure());
              assertThat(message).contains("no host").contains("AYNI_REAL_RELAY_HOST");
              assertThat(message).doesNotContain(ALLOWED).doesNotContain("example.org");
            });

    application
        .withPropertyValues("ayni.notifications.real-relay.host=localhost")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(rootMessage(context.getStartupFailure()))
                  .contains("no allowed recipients")
                  .contains("AYNI_REAL_RELAY_ALLOWED_RECIPIENTS");
            });

    // A list that holds nothing but separators is no list.
    application
        .withPropertyValues("ayni.notifications.real-relay.allowed-recipients= , ,")
        .run(context -> assertThat(context).hasNotFailed());
  }

  @Test
  @DisplayName("The log counts the emails of each path and names no address")
  void theLogCountsTheEmailsOfEachPathAndNamesNoAddress() {
    application
        .withPropertyValues(realRelayFor(ALLOWED))
        .run(
            context -> {
              send(context, ALLOWED);
              send(context, SAME_DOMAIN);
            });

    List<String> lines = logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    assertThat(lines)
        .anyMatch(
            line ->
                line.contains("real relay")
                    && line.contains("1 through the main server")
                    && line.contains("1 through the real relay"));
    assertThat(String.join("\n", lines))
        .doesNotContain(ALLOWED)
        .doesNotContain(SAME_DOMAIN)
        .doesNotContain("example.org")
        .doesNotContain("Body of the email");
  }

  private static String rootMessage(Throwable failure) {
    Throwable root = failure;
    while (root.getCause() != null) {
      root = root.getCause();
    }
    return root.getMessage();
  }
}
