package pe.ayni.audit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.audit.domain.model.ActivityEntry;
import pe.ayni.audit.domain.model.ActorRole;
import pe.ayni.audit.domain.services.ActivityChain;

/**
 * What the chain of the activity log is for: noticing that an entry was changed, removed or slipped
 * in behind the application, without Spring and with real entries.
 *
 * <p>The table already refuses {@code UPDATE} and {@code DELETE}. This is about the changes that
 * never went through its rules: a restored backup, or whoever owns the table and dropped the
 * triggers first.
 */
class ActivityChainTest {

  private static final Instant AT = Instant.parse("2026-10-12T15:00:00Z");

  @Test
  @DisplayName("an entry takes the next place and the hash of the one before it")
  void anEntryTakesTheNextPlaceAndThePreviousHash() {
    List<ActivityEntry> chain = chainOf(3);

    assertThat(chain).extracting(ActivityEntry::getSequenceNumber).containsExactly(1L, 2L, 3L);
    assertThat(chain.getFirst().getPreviousHash()).isNull();
    assertThat(chain.get(1).getPreviousHash()).isEqualTo(chain.getFirst().getEntryHash());
    assertThat(chain.get(2).getPreviousHash()).isEqualTo(chain.get(1).getEntryHash());
    assertThat(chain).allSatisfy(entry -> assertThat(entry.getEntryHash()).hasSize(64).matches("[0-9a-f]{64}"));
  }

  @Test
  @DisplayName("an entry takes its place in the chain once")
  void anEntryIsChainedOnce() {
    ActivityEntry entry = entry("SESSION_STARTED", Map.of());
    entry.chainAfter(0, null);

    assertThatThrownBy(() -> entry.chainAfter(1, entry.getEntryHash())).isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("the hash depends on every field and on the hash of the entry before")
  void theHashCoversEveryField() {
    UUID id = UUID.randomUUID();
    UUID actor = UUID.randomUUID();
    UUID resource = UUID.randomUUID();
    String base = sealed(id, "UPC", actor, ActorRole.STUDENT, "BOOKING_CONFIRMED", "BOOKING", resource, Map.of("late", "false"), AT, 4, "a".repeat(64));

    assertThat(sealed(id, "UPC", actor, ActorRole.STUDENT, "BOOKING_CONFIRMED", "BOOKING", resource, Map.of("late", "false"), AT, 4, "a".repeat(64)))
        .isEqualTo(base);
    assertThat(List.of(
            sealed(UUID.randomUUID(), "UPC", actor, ActorRole.STUDENT, "BOOKING_CONFIRMED", "BOOKING", resource, Map.of("late", "false"), AT, 4, "a".repeat(64)),
            sealed(id, "UTP", actor, ActorRole.STUDENT, "BOOKING_CONFIRMED", "BOOKING", resource, Map.of("late", "false"), AT, 4, "a".repeat(64)),
            sealed(id, "UPC", null, ActorRole.STUDENT, "BOOKING_CONFIRMED", "BOOKING", resource, Map.of("late", "false"), AT, 4, "a".repeat(64)),
            sealed(id, "UPC", actor, ActorRole.TUTOR, "BOOKING_CONFIRMED", "BOOKING", resource, Map.of("late", "false"), AT, 4, "a".repeat(64)),
            sealed(id, "UPC", actor, ActorRole.STUDENT, "BOOKING_CANCELLED", "BOOKING", resource, Map.of("late", "false"), AT, 4, "a".repeat(64)),
            sealed(id, "UPC", actor, ActorRole.STUDENT, "BOOKING_CONFIRMED", "SESSION", resource, Map.of("late", "false"), AT, 4, "a".repeat(64)),
            sealed(id, "UPC", actor, ActorRole.STUDENT, "BOOKING_CONFIRMED", "BOOKING", UUID.randomUUID(), Map.of("late", "false"), AT, 4, "a".repeat(64)),
            sealed(id, "UPC", actor, ActorRole.STUDENT, "BOOKING_CONFIRMED", "BOOKING", resource, Map.of("late", "true"), AT, 4, "a".repeat(64)),
            sealed(id, "UPC", actor, ActorRole.STUDENT, "BOOKING_CONFIRMED", "BOOKING", resource, Map.of(), AT, 4, "a".repeat(64)),
            sealed(id, "UPC", actor, ActorRole.STUDENT, "BOOKING_CONFIRMED", "BOOKING", resource, Map.of("late", "false"), AT.plusNanos(1000), 4, "a".repeat(64)),
            sealed(id, "UPC", actor, ActorRole.STUDENT, "BOOKING_CONFIRMED", "BOOKING", resource, Map.of("late", "false"), AT, 5, "a".repeat(64)),
            sealed(id, "UPC", actor, ActorRole.STUDENT, "BOOKING_CONFIRMED", "BOOKING", resource, Map.of("late", "false"), AT, 4, "b".repeat(64))))
        .doesNotContain(base)
        .doesNotHaveDuplicates();
  }

  @Test
  @DisplayName("two details cannot blur into one, and their order does not matter")
  void detailsAreEncodedWithoutAmbiguity() {
    UUID id = UUID.randomUUID();
    Map<String, String> oneWay = new LinkedHashMap<>();
    oneWay.put("tutorId", "1");
    oneWay.put("startsAt", "2");
    Map<String, String> theOtherWay = new LinkedHashMap<>();
    theOtherWay.put("startsAt", "2");
    theOtherWay.put("tutorId", "1");

    assertThat(sealed(id, "UPC", null, ActorRole.SYSTEM, "A", "B", null, oneWay, AT, 1, null))
        .isEqualTo(sealed(id, "UPC", null, ActorRole.SYSTEM, "A", "B", null, theOtherWay, AT, 1, null));
    assertThat(sealed(id, "UPC", null, ActorRole.SYSTEM, "A", "B", null, Map.of("ab", "c"), AT, 1, null))
        .isNotEqualTo(sealed(id, "UPC", null, ActorRole.SYSTEM, "A", "B", null, Map.of("a", "bc"), AT, 1, null));
  }

  @Test
  @DisplayName("a chain nobody touched holds, whatever order its entries arrive in")
  void anUntouchedChainHolds() {
    List<ActivityEntry> chain = chainOf(4);

    assertThat(ActivityChain.firstInconsistentEntry(null, chain)).isEmpty();
    assertThat(ActivityChain.firstInconsistentEntry(null, List.of(chain.get(3), chain.get(1), chain.getFirst(), chain.get(2)))).isEmpty();
  }

  @Test
  @DisplayName("finds the entry whose contents no longer match its hash")
  void findsAnEntryEditedInPlace() throws Exception {
    List<ActivityEntry> chain = chainOf(4);
    ActivityEntry second = chain.get(1);

    // There is no method for this, which is the point: it takes reflection to do to an entry what
    // only a hand on the database could do.
    behindTheApplication(second, "action", "NOTHING_HAPPENED");

    assertThat(ActivityChain.matchesOwnHash(second)).isFalse();
    assertThat(ActivityChain.firstInconsistentEntry(null, chain)).contains(second);
  }

  @Test
  @DisplayName("finds the entry after the one that was removed")
  void findsTheEntryAfterARemovedOne() {
    List<ActivityEntry> chain = chainOf(4);

    assertThat(ActivityChain.firstInconsistentEntry(null, List.of(chain.getFirst(), chain.get(2), chain.get(3))))
        .contains(chain.get(2));
    // Removing the first of the chain leaves a first entry that points at something.
    assertThat(ActivityChain.firstInconsistentEntry(null, chain.subList(1, 4))).contains(chain.get(1));
  }

  @Test
  @DisplayName("finds an entry slipped in, even one that hashes correctly")
  void findsAnEntrySlippedIn() {
    List<ActivityEntry> chain = chainOf(3);

    // A forger who knows the format: the entry hashes correctly and points at the first one, but it
    // claims a place after the third.
    ActivityEntry forged = entry("RECOGNITION_APPROVED", Map.of());
    forged.chainAfter(3, chain.getFirst().getEntryHash());
    List<ActivityEntry> withTheForgery = new ArrayList<>(chain);
    withTheForgery.add(forged);

    assertThat(ActivityChain.matchesOwnHash(forged)).isTrue();
    assertThat(ActivityChain.firstInconsistentEntry(null, withTheForgery)).contains(forged);
  }

  @Test
  @DisplayName("checks a stretch of the chain as the continuation of the entry before it")
  void checksAStretchAsAContinuation() {
    List<ActivityEntry> chain = chainOf(5);

    assertThat(ActivityChain.firstInconsistentEntry(chain.get(1), chain.subList(2, 5))).isEmpty();
    // What a removed entry between two pages looks like: the stretch does not continue the entry
    // handed over as the one before it.
    assertThat(ActivityChain.firstInconsistentEntry(chain.getFirst(), chain.subList(2, 5))).contains(chain.get(2));
  }

  @Test
  @DisplayName("a moment more precise than the database keeps is cut before it is hashed")
  void theMomentIsCutToMicroseconds() {
    ActivityEntry entry =
        ActivityEntry.of(UUID.randomUUID(), "UPC", null, ActorRole.SYSTEM, "A", "B", null, null, AT.plusNanos(123_456_789));

    assertThat(entry.getOccurredAt()).isEqualTo(AT.plusNanos(123_456_000));
  }

  private static List<ActivityEntry> chainOf(int entries) {
    List<ActivityEntry> chain = new ArrayList<>();
    ActivityEntry last = null;
    for (int i = 0; i < entries; i++) {
      ActivityEntry entry = entry("SESSION_STARTED", Map.of("order", Integer.toString(i)));
      entry.chainAfter(last == null ? 0 : last.getSequenceNumber(), last == null ? null : last.getEntryHash());
      chain.add(entry);
      last = entry;
    }
    return chain;
  }

  private static ActivityEntry entry(String action, Map<String, String> details) {
    return ActivityEntry.of(UUID.randomUUID(), "UPC", null, ActorRole.SYSTEM, action, "SESSION", UUID.randomUUID(), details, AT);
  }

  private static String sealed(
      UUID id,
      String tenant,
      UUID actor,
      ActorRole role,
      String action,
      String resourceType,
      UUID resource,
      Map<String, String> details,
      Instant at,
      long sequenceNumber,
      String previousHash) {
    ActivityEntry entry = ActivityEntry.of(id, tenant, actor, role, action, resourceType, resource, details, at);
    entry.chainAfter(sequenceNumber - 1, previousHash);
    return entry.getEntryHash();
  }

  private static void behindTheApplication(ActivityEntry entry, String field, Object value) throws Exception {
    Field column = ActivityEntry.class.getDeclaredField(field);
    column.setAccessible(true);
    column.set(entry, value);
  }
}
