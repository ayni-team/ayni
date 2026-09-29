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

  @Column(name = "code_hash", length = 64, nullable = false, updatable = false)
  private String codeHash;

  @Column(name = "issued_at", nullable = false, updatable = false)
  private Instant issuedAt;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private Instant expiresAt;

  @Column(name = "confirmed_at")
  private Instant confirmedAt;

  @Column(name = "attempts", nullable = false)
  private short attempts;

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
}
