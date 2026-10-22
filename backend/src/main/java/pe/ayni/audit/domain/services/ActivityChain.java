package pe.ayni.audit.domain.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import pe.ayni.audit.domain.model.ActivityEntry;

/**
 * The chain of a university's activity log: how an entry is sealed, and how the chain is checked.
 *
 * <p>Each entry carries a SHA-256 of its own fields plus the hash of the entry before it. Two
 * things are then verified for every entry: that it still matches its own hash, which catches an
 * entry edited in place, and that it carries the hash of the entry before it, which catches one
 * removed, slipped in or moved. The triggers of the table already refuse a change; this is what is
 * left for the cases they cannot see, such as a restore from a doctored backup or somebody who
 * owns the table and dropped them first.
 *
 * <p>Nothing outside the database is anchored: whoever can rewrite every hash from an entry to the
 * end of the chain can also hide it. That is accepted for now.
 */
public final class ActivityChain {

  /** Stands for a field without a value, which no encoded value can start with. */
  private static final String NO_VALUE = "~";

  private ActivityChain() {}

  /**
   * The hash an entry must carry, from its fields as they stand now.
   *
   * <p>Every column the entry owns goes in, its identifier and its place in the chain included, and
   * the hash of the one before it. Each value is written with its length in front, so two fields
   * can never blur into one, and the details are taken in the order of their names, since the
   * database does not keep the order they were given in.
   */
  public static String hashOf(ActivityEntry entry) {
    StringBuilder canonical = new StringBuilder();
    append(canonical, entry.getId());
    append(canonical, entry.getTenantId());
    append(canonical, entry.getSequenceNumber());
    append(canonical, entry.getActorId());
    append(canonical, entry.getActorRole());
    append(canonical, entry.getAction());
    append(canonical, entry.getResourceType());
    append(canonical, entry.getResourceId());
    Map<String, String> details = new TreeMap<>(entry.getMetadata());
    append(canonical, details.size());
    details.forEach(
        (name, value) -> {
          append(canonical, name);
          append(canonical, value);
        });
    append(canonical, entry.getOccurredAt());
    append(canonical, entry.getPreviousHash());
    return sha256(canonical.toString());
  }

  /** Whether a chained entry still matches its own hash. */
  public static boolean matchesOwnHash(ActivityEntry entry) {
    return entry.getEntryHash() != null && entry.getEntryHash().equals(hashOf(entry));
  }

  /**
   * Whether the entry carries the hash of the given one, which is {@code null} when the entry
   * should be the first of the chain.
   */
  public static boolean follows(ActivityEntry entry, ActivityEntry previous) {
    String expected = previous == null ? null : previous.getEntryHash();
    return Objects.equals(entry.getPreviousHash(), expected);
  }

  /**
   * The first entry of a stretch of the chain that does not fit, if there is one.
   *
   * <p>This is what lets a long chain be checked a page at a time: the last entry of a page is
   * kept, and the next page is checked as the continuation of it. The entries are taken in the
   * order of their place in the chain, so the answer is where the log stopped being trustworthy,
   * not merely that something somewhere is wrong.
   *
   * @param previous the entry right before the stretch, or {@code null} when the stretch starts
   *     the chain
   * @param stretch chained entries, consecutive in the chain
   */
  public static Optional<ActivityEntry> firstInconsistentEntry(
      ActivityEntry previous, List<ActivityEntry> stretch) {
    List<ActivityEntry> inOrder =
        stretch.stream().sorted(Comparator.comparing(ActivityEntry::getSequenceNumber)).toList();
    ActivityEntry before = previous;
    for (ActivityEntry entry : inOrder) {
      if (!matchesOwnHash(entry) || !follows(entry, before)) {
        return Optional.of(entry);
      }
      before = entry;
    }
    return Optional.empty();
  }

  private static void append(StringBuilder canonical, Object value) {
    if (value == null) {
      canonical.append(NO_VALUE);
      return;
    }
    String text = value.toString();
    canonical.append(text.length()).append(':').append(text);
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
}
