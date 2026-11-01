package pe.ayni.notifications.infrastructure;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.javamail.JavaMailSender;
import pe.ayni.notifications.application.Email;
import pe.ayni.notifications.application.EmailDelivery;
import pe.ayni.notifications.application.EmailNotDelivered;

/**
 * Sends email over one SMTP server.
 *
 * <p>Which server is decided by whoever builds it: the one {@code spring.mail.*} names (Mailpit
 * under {@code docker compose}, the university's relay in a deployment) or the real relay that
 * {@link MailRoutingConfig} builds for the allowed addresses. When the server refuses an email,
 * the reason kept on the notice never repeats the address, because a server may quote it.
 */
class SmtpEmailDelivery implements EmailDelivery {

  private static final String RECIPIENT_PLACEHOLDER = "<recipient>";

  private final JavaMailSender mailSender;
  private final String from;
  private final String serverName;

  /**
   * @param serverName how the failure refers to the server, such as "mail server" or "real relay"
   */
  SmtpEmailDelivery(JavaMailSender mailSender, String from, String serverName) {
    this.mailSender = mailSender;
    this.from = from;
    this.serverName = serverName;
  }

  @Override
  public void send(Email email) {
    try {
      if (email.html() == null) {
        mailSender.send(plainText(email));
      } else {
        mailSender.send(withHtml(email));
      }
    } catch (MailException | MessagingException failure) {
      throw new EmailNotDelivered(
          "The " + serverName + " did not take the email: " + withoutAddress(failure, email.to()),
          failure);
    }
  }

  private SimpleMailMessage plainText(Email email) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(email.to());
    message.setSubject(email.subject());
    message.setText(email.body());
    return message;
  }

  /** The two versions as alternatives: the client shows the HTML, or the text if it cannot. */
  private MimeMessage withHtml(Email email) throws MessagingException {
    MimeMessage message = mailSender.createMimeMessage();
    MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
    helper.setFrom(from);
    helper.setTo(email.to());
    helper.setSubject(email.subject());
    helper.setText(email.body(), email.html());
    return message;
  }

  private static String withoutAddress(Exception failure, String address) {
    String reason = String.valueOf(failure.getMessage());
    if (address.isBlank()) {
      return reason;
    }
    return Pattern.compile(Pattern.quote(address), Pattern.CASE_INSENSITIVE)
        .matcher(reason)
        .replaceAll(Matcher.quoteReplacement(RECIPIENT_PLACEHOLDER));
  }
}
