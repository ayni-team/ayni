package pe.ayni.notifications.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import pe.ayni.notifications.application.Email;
import pe.ayni.notifications.application.EmailDelivery;

/**
 * Development email adapter.
 *
 * <p>No real email is sent while the dev profile is active. The message is written to the
 * application log instead, so access links can be used during local development.
 */
@Component
@Profile("dev")
class DevEmailDelivery implements EmailDelivery {

    private static final Logger log = LoggerFactory.getLogger(DevEmailDelivery.class);

    @Override
    public void send(Email email) {
        log.info("DEV email\nSubject: {}\n\n{}", email.subject(), email.body());
    }
}