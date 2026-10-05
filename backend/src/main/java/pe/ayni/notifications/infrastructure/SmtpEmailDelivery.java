package pe.ayni.notifications.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import pe.ayni.notifications.application.Email;
import pe.ayni.notifications.application.EmailDelivery;
import pe.ayni.notifications.application.EmailNotDelivered;
import org.springframework.context.annotation.Profile;

/**
 * Sends email over SMTP, to whichever server {@code spring.mail.*} names: Mailpit under {@code
 * docker compose}, the university's relay in a deployment.
 */
@Profile("!dev")
@Component
class SmtpEmailDelivery implements EmailDelivery {

  private final JavaMailSender mailSender;
  private final String from;

  SmtpEmailDelivery(
      JavaMailSender mailSender,
      @Value("${ayni.notifications.mail-from:Ayni <no-reply@ayni.local>}") String from) {
    this.mailSender = mailSender;
    this.from = from;
  }

  @Override
  public void send(Email email) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(email.to());
    message.setSubject(email.subject());
    message.setText(email.body());
    try {
      mailSender.send(message);
    } catch (MailException failure) {
      throw new EmailNotDelivered("The mail server did not take the email: " + failure.getMessage(),
          failure);
    }
  }
}
