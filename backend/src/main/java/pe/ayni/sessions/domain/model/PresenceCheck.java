package pe.ayni.sessions.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * One participant's presence code in one session, and whether they typed it in (US54).
 *
 * <p>The code is emailed and only its hash is kept, salted with the check's id so two participants
 * who happen to get the same six digits do not share a hash. Six digits are few, so the hash alone
 * would not stop somebody reading the database from finding the code; what keeps the check honest
 * is that the code expires within minutes and is dead after {@link #MAX_ATTEMPTS} wrong tries.
 */
@Entity
@Table(schema = "sessions", name = "presence_checks")
public class PresenceCheck {

  private static final SecureRandom RANDOM = new SecureRandom();

  /** Scenario 4 of US54: after five wrong codes the code is useless. */
  public static final int MAX_ATTEMPTS = 5;

  /**
   * How long a code can be typed in. Long enough for an institutional mailbox that takes a few
   * minutes to deliver; short enough that it proves presence during the session, not afterwards.
   */
  public static final Duration LIFETIME = Duration.ofMinutes(15);

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "tenant_id", length = 32, nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "session_id", nullable = false, updatable = false)
  private UUID sessionId;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "code_hash", length = 64, nullable = false)
  private String codeHash;

  @Column(name = "issued_at", nullable = false)
  private Instant issuedAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "confirmed_at")
  private Instant confirmedAt;

  @Column(name = "attempts", nullable = false)
  private short attempts;

  @Column(name = "resend_count", nullable = false)
  private short resendCount;

  @Column(name = "last_resent_at")
  private Instant lastResentAt;

  protected PresenceCheck() {
    // Required by JPA
  }

  /** Six random digits, leading zeros included. */
  public static String newCode() {
    return "%06d".formatted(RANDOM.nextInt(1_000_000));
  }

  /** A code just issued to a participant, which expires {@link #LIFETIME} from now. */
  public static PresenceCheck issue(
      UUID id, String tenantId, UUID sessionId, UUID userId, String code, Instant now) {
    PresenceCheck check = new PresenceCheck();
    check.id = Objects.requireNonNull(id, "id must not be null");
    check.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
    check.sessionId = Objects.requireNonNull(sessionId, "sessionId must not be null");
    check.userId = Objects.requireNonNull(userId, "userId must not be null");
    check.codeHash = hash(id, Objects.requireNonNull(code, "code must not be null"));
    check.issuedAt = Objects.requireNonNull(now, "now must not be null");
    check.expiresAt = now.plus(LIFETIME);
    return check;
  }

  /**
   * The participant types the code in.
   *
   * <p>Typing it again once confirmed changes nothing, so a double click or a retried request does
   * not spend an attempt. A wrong code spends one, and the caller must keep that attempt even though
   * the answer is a refusal: otherwise the cap would never be reached.
   *
   * @return whether this call is the one that confirmed presence
   * @throws PresenceCheckUnavailable when the code expired or no attempts are left
   * @throws WrongPresenceCode when the code is not the one issued
   */
  public boolean confirm(String code, Instant now) {
    Objects.requireNonNull(code, "code must not be null");
    Objects.requireNonNull(now, "now must not be null");

    if (isConfirmed()) {
      return false;
    }
    if (attemptsLeft() == 0) {
      throw new PresenceCheckUnavailable(
          "This presence code can no longer be used: it was entered wrong "
              + MAX_ATTEMPTS + " times");
    }
    if (!now.isBefore(expiresAt)) {
      throw new PresenceCheckUnavailable("This presence code expired at " + expiresAt);
    }
    if (!MessageDigest.isEqual(
        codeHash.getBytes(StandardCharsets.US_ASCII),
        hash(id, code).getBytes(StandardCharsets.US_ASCII))) {
      attempts++;
      throw new WrongPresenceCode(attemptsLeft());
    }
    confirmedAt = now;
    return true;
  }

  /**
   * Replaces the code with a new one, because the email with the first did not arrive (US54-T4).
   *
   * <p>The old code stops being valid at once: only the hash of the new one is kept, and the new
   * code is never equal to the old, so typing the old one is a wrong code. The new one lives the
   * full {@link #LIFETIME} from now. The attempts already spent are kept, so a resend is no way to
   * get five more guesses.
   *
   * @param maxResends how many times a code may be replaced in the whole session
   * @param minInterval the least time between two resends; the first one has no predecessor
   * @return the new code in clear, for the caller to publish; it is not stored
   * @throws PresenceResendRefused when presence is already confirmed, the attempts are spent, the
   *     resends are used up or the last one was too recent
   */
  public String reissue(Instant now, int maxResends, Duration minInterval) {
    Objects.requireNonNull(now, "now must not be null");
    Objects.requireNonNull(minInterval, "minInterval must not be null");

    if (isConfirmed()) {
      throw new PresenceResendRefused("Presence is already confirmed: there is no code to resend");
    }
    if (attemptsLeft() == 0) {
      throw new PresenceResendRefused(
          "This presence code can no longer be used: it was entered wrong " + MAX_ATTEMPTS
              + " times, and a new code does not give the attempts back");
    }
    if (resendCount >= maxResends) {
      throw new PresenceResendRefused(
          "The presence code was already resent " + maxResends + " times in this session");
    }
    if (lastResentAt != null && now.isBefore(lastResentAt.plus(minInterval))) {
      throw new PresenceResendRefused(
          "The presence code was resent a moment ago. Try again at "
              + lastResentAt.plus(minInterval));
    }

    String fresh;
    String freshHash;
    do {
      fresh = newCode();
      freshHash = hash(id, fresh);
    } while (freshHash.equals(codeHash));

    codeHash = freshHash;
    issuedAt = now;
    expiresAt = now.plus(LIFETIME);
    resendCount++;
    lastResentAt = now;
    return fresh;
  }

  public boolean isConfirmed() {
    return confirmedAt != null;
  }

  public int attemptsLeft() {
    return MAX_ATTEMPTS - attempts;
  }

  private static String hash(UUID id, String code) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed = digest.digest((id + ":" + code).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hashed);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }

  public UUID getId() {
    return id;
  }

  public String getTenantId() {
    return tenantId;
  }

  public UUID getSessionId() {
    return sessionId;
  }

  public UUID getUserId() {
    return userId;
  }

  public Instant getIssuedAt() {
    return issuedAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getConfirmedAt() {
    return confirmedAt;
  }

  public int getAttempts() {
    return attempts;
  }

  public int getResendCount() {
    return resendCount;
  }

  public Instant getLastResentAt() {
    return lastResentAt;
  }
}
