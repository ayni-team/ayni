package pe.ayni.wallet.infrastructure;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on Spring's scheduler, which wallet needs for the nightly expiry.
 *
 * <p>It lives here rather than in {@code config} because wallet is the only module that schedules
 * anything today. Another module declaring the same annotation changes nothing: it registers the
 * same scheduler once. If scheduled work becomes common, this moves to {@code config} and this
 * class disappears.
 */
@Configuration
@EnableScheduling
class WalletSchedulingConfig {}
