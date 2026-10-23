package pe.ayni.notifications.infrastructure;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pe.ayni.notifications.application.Email;
import pe.ayni.notifications.application.EmailDelivery;
import pe.ayni.notifications.application.EmailNotDelivered;

/**
 * Decides, for each email, which server carries it.
 *
 * <p>An address on the allowed list leaves through the real relay. Every other address leaves
 * through the main server, which under {@code dev} and {@code demo} is Mailpit: the people of a
 * demonstration are invented and often carry the domain of a real university, so a real relay must
 * never see their addresses. The test is the whole address, compared without regard to case, never
 * the domain.
 *
 * <p>A failure of the real relay is the failure of that email. It is never handed to the main
 * server in its place, because that would hide from the person that their email did not leave.
 *
 * <p>The log counts what left by each path and names no address, link or code.
 */
class RoutingEmailDelivery implements EmailDelivery {

  private static final Logger log = LoggerFactory.getLogger(RoutingEmailDelivery.class);

  private final EmailDelivery main;
  private final EmailDelivery real;
  private final Set<String> allowed;
  private final AtomicLong viaMain = new AtomicLong();
  private final AtomicLong viaReal = new AtomicLong();
  private final AtomicLong realFailures = new AtomicLong();

  /**
   * @param allowed the addresses that may leave through the real relay, already normalised with
   *     {@link #normalise}
   */
  RoutingEmailDelivery(EmailDelivery main, EmailDelivery real, Set<String> allowed) {
    this.main = Objects.requireNonNull(main, "main must not be null");
    this.real = Objects.requireNonNull(real, "real must not be null");
    this.allowed = Set.copyOf(allowed);
  }

  /** The form in which addresses are compared: no surrounding spaces, lower case. */
  static String normalise(String address) {
    return address.trim().toLowerCase(Locale.ROOT);
  }

  @Override
  public void send(Email email) {
    if (!allowed.contains(normalise(email.to()))) {
      main.send(email);
      log.info(
          "Email left through the main server: {} through the main server and {} through the real"
              + " relay so far",
          viaMain.incrementAndGet(),
          viaReal.get());
      return;
    }
    try {
      real.send(email);
    } catch (EmailNotDelivered failure) {
      log.warn(
          "The real relay did not take an email ({} failures so far); it was not sent through the"
              + " main server",
          realFailures.incrementAndGet());
      throw failure;
    }
    log.info(
        "Email left through the real relay: {} through the main server and {} through the real"
            + " relay so far",
        viaMain.get(),
        viaReal.incrementAndGet());
  }
}
