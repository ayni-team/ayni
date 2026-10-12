package pe.ayni.audit;

import java.time.Instant;
import java.util.UUID;

/**
 * An open alert of the audit, as the other modules see it.
 *
 * @param id the anomaly, which a coordinator can open in the audit
 * @param kind what pattern it is: REPEATED_PAIR, SHORT_DURATION, FAILED_PRESENCE and so on
 * @param severity LOW, MEDIUM or HIGH
 * @param description what was found, in a sentence a person can read
 * @param detectedAt when audit found it
 */
public record AlertView(
    UUID id, String kind, String severity, String description, Instant detectedAt) {}
