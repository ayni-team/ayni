package pe.ayni.audit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.audit.domain.model.ActivityEntry;
import pe.ayni.audit.domain.model.ActorRole;

class ActivityEntryTest {

  private static final Instant AT = Instant.parse("2026-10-12T15:00:00Z");

  private static ActivityEntry entry(Map<String, String> metadata) {
    return ActivityEntry.of(
        UUID.randomUUID(), "UPC", null, ActorRole.SYSTEM, "SESSION_COMPLETED", "SESSION", UUID.randomUUID(), metadata, AT);
  }

  @Test
  @DisplayName("an entry keeps what it was given and has nothing to change it with")
  void keepsWhatItWasGiven() {
    UUID actor = UUID.randomUUID();
    UUID resource = UUID.randomUUID();

    ActivityEntry entry =
        ActivityEntry.of(
            UUID.randomUUID(), "UPC", actor, ActorRole.TUTOR, "BOOKING_CANCELLED", "BOOKING", resource, Map.of("late", "true"), AT);

    assertThat(entry.getTenantId()).isEqualTo("UPC");
    assertThat(entry.getActorId()).isEqualTo(actor);
    assertThat(entry.getActorRole()).isEqualTo(ActorRole.TUTOR);
    assertThat(entry.getAction()).isEqualTo("BOOKING_CANCELLED");
    assertThat(entry.getResourceType()).isEqualTo("BOOKING");
    assertThat(entry.getResourceId()).isEqualTo(resource);
    assertThat(entry.getOccurredAt()).isEqualTo(AT);
    assertThatThrownBy(() -> entry.getMetadata().put("late", "false")).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @DisplayName("an entry belongs to a university and says what happened")
  void needsAUniversityAndAnAction() {
    assertThatThrownBy(() -> ActivityEntry.of(UUID.randomUUID(), " ", null, ActorRole.SYSTEM, "A", "B", null, null, AT))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ActivityEntry.of(UUID.randomUUID(), "UPC", null, ActorRole.SYSTEM, "", "B", null, null, AT))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ActivityEntry.of(UUID.randomUUID(), "UPC", null, ActorRole.SYSTEM, "A", "B".repeat(33), null, null, AT))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("an entry refuses a detail that looks like an address or a link")
  void refusesAddressesAndLinks() {
    assertThatThrownBy(() -> entry(Map.of("who", "ana@upc.edu.pe"))).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> entry(Map.of("link", "https://ayni.pe/access?token=abc"))).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("an entry without details has an empty map, not null")
  void noDetails() {
    assertThat(entry(null).getMetadata()).isEmpty();
  }
}
