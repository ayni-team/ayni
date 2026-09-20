package pe.ayni.wallet.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.hibernate.annotations.Immutable;
import pe.ayni.shared.domain.Credits;

/**
 * One movement, written down for good.
 *
 * <p>Entries are never updated and never deleted: a mistake is answered with a new {@link
 * LedgerReason#ADJUSTMENT} entry, and the mistake stays visible. A trigger in the database refuses
 * {@code UPDATE} and {@code DELETE}, so this holds for anyone with a psql prompt too, not only for
 * code that goes through this class.
 *
 * <p>Each entry carries the hash of the one before it, chained per university. Changing a row
 * behind the application's back leaves its hash no longer matching its contents and breaks every
 * link after it, which is what makes the tampering findable rather than merely forbidden.
 */
@Entity
@Immutable
@Table(schema = "wallet", name = "ledger_entries")
public class LedgerEntry {

  /** Separates the fields inside the hashed text, so that two of them cannot blur into one. */
  private static final String FIELD_SEPARATOR = "|";

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "tenant_id", length = 32, nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "account_id", nullable = false, updatable = false)
  private UUID accountId;

  @Column(name = "lot_id", updatable = false)
  private UUID lotId;

  @Column(name = "sequence_number", nullable = false, updatable = false)
  private long sequenceNumber;

  @Enumerated(EnumType.STRING)
  @Column(name = "direction", length = 8, nullable = false, updatable = false)
  private LedgerDirection direction;

  @Column(name = "amount", nullable = false, updatable = false)
  private int amount;

  @Enumerated(EnumType.STRING)
  @Column(name = "reason", length = 32, nullable = false, updatable = false)
  private LedgerReason reason;

  @Enumerated(EnumType.STRING)
  @Column(name = "reference_type", length = 24, updatable = false)
  private ReferenceType referenceType;

  @Column(name = "reference_id", updatable = false)
  private UUID referenceId;

  @Column(name = "previous_hash", length = 64, updatable = false)
  private String previousHash;

  @Column(name = "entry_hash", length = 64, nullable = false, updatable = false)
  private String entryHash;

  @Column(name = "occurred_at", nullable = false, updatable = false)
  private Instant occurredAt;

  protected LedgerEntry() {
    // Required by JPA
  }

  /**
   * Writes a movement down as the next link of a university's chain.
   *
   * @param previous the last entry of this university, or {@code null} for the very first one
   * @param sequenceNumber assigned by the application inside the transaction, never by a database
   *     sequence: a sequence leaves gaps after a rollback, and a gap looks exactly like a deleted
   *     row
   */
  public static LedgerEntry following(
      LedgerEntry previous, long sequenceNumber, Movement movement, Instant occurredAt) {
    return followingHash(
        previous == null ? null : previous.entryHash(), sequenceNumber, movement, occurredAt);
  }

  /**
   * The same, when all that is known of the previous entry is its hash.
   *
   * <p>That is the case when appending: the tail of the chain is read as two columns under a lock,
   * and loading the whole entry to take one field off it would buy nothing.
   */
  public static LedgerEntry followingHash(
      String previousHash, long sequenceNumber, Movement movement, Instant occurredAt) {

    LedgerEntry entry = new LedgerEntry();
    entry.id = UUID.randomUUID();
    entry.tenantId = movement.tenantId();
    entry.accountId = movement.accountId();
    entry.lotId = movement.lotId();
    entry.sequenceNumber = sequenceNumber;
    entry.direction = movement.direction();
    entry.amount = movement.amount().amount();
    entry.reason = movement.reason();
    entry.referenceType = movement.referenceType();
    entry.referenceId = movement.referenceId();
    entry.previousHash = previousHash;
    // PostgreSQL keeps microseconds, so an instant with more precision would come back different
    // from what was hashed and break the chain on the first verification after a restart.
    entry.occurredAt = occurredAt.truncatedTo(ChronoUnit.MICROS);
    entry.entryHash = entry.computeHash();
    return entry;
  }

  /**
   * Recomputes the hash from the fields as they stand now.
   *
   * <p>An entry whose stored hash no longer equals this was changed outside the application.
   */
  public String computeHash() {
    String canonical =
        Stream.of(
                tenantId,
                accountId,
                lotId,
                sequenceNumber,
                direction,
                amount,
                reason,
                referenceType,
                referenceId,
                occurredAt,
                previousHash)
            .map(field -> field == null ? "" : field.toString())
            .collect(Collectors.joining(FIELD_SEPARATOR));

    return sha256(canonical);
  }

  /** Whether the entry still matches its own hash. */
  public boolean matchesOwnHash() {
    return entryHash.equals(computeHash());
  }

  /** Whether this entry follows the given one, which is {@code null} for the first of a chain. */
  public boolean follows(LedgerEntry previous) {
    String expected = previous == null ? null : previous.entryHash();
    return Objects.equals(previousHash, expected);
  }

  private static String sha256(String text) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      // Every Java platform is required to provide SHA-256.
      throw new IllegalStateException("SHA-256 is not available", impossible);
    }
  }

  public UUID id() {
    return id;
  }

  public String tenantId() {
    return tenantId;
  }

  public UUID accountId() {
    return accountId;
  }

  public UUID lotId() {
    return lotId;
  }

  public long sequenceNumber() {
    return sequenceNumber;
  }

  public LedgerDirection direction() {
    return direction;
  }

  public Credits amount() {
    return Credits.of(amount);
  }

  public LedgerReason reason() {
    return reason;
  }

  public ReferenceType referenceType() {
    return referenceType;
  }

  public UUID referenceId() {
    return referenceId;
  }

  public String previousHash() {
    return previousHash;
  }

  public String entryHash() {
    return entryHash;
  }

  public Instant occurredAt() {
    return occurredAt;
  }
}
