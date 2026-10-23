package pe.ayni.notifications.infrastructure;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import pe.ayni.notifications.application.EmailDelivery;

/**
 * Builds the way emails leave: through the main server alone, or through the main server and, for
 * an allowed list of addresses, a real relay.
 *
 * <p>The main server is the {@code JavaMailSender} that {@code spring.mail.*} configures. The real
 * relay is described by {@code ayni.notifications.real-relay.*} and is optional. With neither the
 * list nor the host, nothing changes. With only one of them the application does not start: a
 * list without a host would send nobody's email, and a host without a list would be a relay that
 * nobody may use, and either one is a mistake worth stopping for.
 *
 * <p>The sender of the real relay is not a bean. A second {@code JavaMailSender} in the context
 * would make Spring Boot give up the main one.
 */
@Configuration(proxyBeanMethods = false)
class MailRoutingConfig {

  private static final Logger log = LoggerFactory.getLogger(MailRoutingConfig.class);

  private static final String PREFIX = "ayni.notifications.real-relay";

  @Bean
  EmailDelivery emailDelivery(
      JavaMailSender mailSender,
      @Value("${ayni.notifications.mail-from:Ayni <no-reply@ayni.local>}") String from,
      @Value("${" + PREFIX + ".allowed-recipients:}") String allowedRecipients,
      @Value("${" + PREFIX + ".host:}") String host,
      @Value("${" + PREFIX + ".port:}") String port,
      @Value("${" + PREFIX + ".username:}") String username,
      @Value("${" + PREFIX + ".password:}") String password,
      @Value("${" + PREFIX + ".starttls:}") String starttls,
      @Value("${" + PREFIX + ".ssl:}") String ssl) {

    EmailDelivery main = new SmtpEmailDelivery(mailSender, from, "mail server");
    Set<String> allowed = parseAllowed(allowedRecipients);
    boolean hasHost = !host.isBlank();

    if (allowed.isEmpty() && !hasHost) {
      log.info("No real relay is configured: every email leaves through the main server");
      return main;
    }
    if (allowed.isEmpty()) {
      throw new IllegalStateException(
          "The real relay has a host but no allowed recipients: set "
              + PREFIX
              + ".allowed-recipients (AYNI_REAL_RELAY_ALLOWED_RECIPIENTS) or remove the host");
    }
    if (!hasHost) {
      throw new IllegalStateException(
          "The real relay has allowed recipients but no host: set "
              + PREFIX
              + ".host (AYNI_REAL_RELAY_HOST) or remove the allowed recipients");
    }

    EmailDelivery real =
        new SmtpEmailDelivery(
            realRelay(host.trim(), port, username, password, starttls, ssl), from, "real relay");
    log.info(
        "A real relay is configured for {} allowed recipients: every other email leaves through the"
            + " main server",
        allowed.size());
    return new RoutingEmailDelivery(main, real, allowed);
  }

  private static Set<String> parseAllowed(String allowedRecipients) {
    return Arrays.stream(allowedRecipients.split(","))
        .map(RoutingEmailDelivery::normalise)
        .filter(address -> !address.isEmpty())
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private static JavaMailSenderImpl realRelay(
      String host, String port, String username, String password, String starttls, String ssl) {
    JavaMailSenderImpl sender = new JavaMailSenderImpl();
    sender.setHost(host);
    if (!port.isBlank()) {
      try {
        sender.setPort(Integer.parseInt(port.trim()));
      } catch (NumberFormatException notANumber) {
        throw new IllegalStateException(
            PREFIX + ".port (AYNI_REAL_RELAY_PORT) must be a number or empty", notANumber);
      }
    }
    if (!username.isBlank()) {
      sender.setUsername(username);
      sender.setPassword(password);
    }
    Properties properties = sender.getJavaMailProperties();
    // The same limits as the main server: an unreachable relay fails the email in seconds.
    properties.setProperty("mail.smtp.connectiontimeout", "5000");
    properties.setProperty("mail.smtp.timeout", "5000");
    properties.setProperty("mail.smtp.writetimeout", "5000");
    properties.setProperty("mail.smtp.starttls.enable", String.valueOf(isTrue(starttls)));
    properties.setProperty("mail.smtp.ssl.enable", String.valueOf(isTrue(ssl)));
    return sender;
  }

  private static boolean isTrue(String value) {
    return Boolean.parseBoolean(value.trim());
  }
}
