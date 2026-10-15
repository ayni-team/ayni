package pe.ayni.sessions.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.sessions.domain.model.Session;
import pe.ayni.sessions.domain.model.SessionRuleViolation;
import pe.ayni.sessions.domain.model.SupportMaterial;

/** The rules of the support material (US07), without Spring. */
class SupportMaterialTest {

  private static final Instant NOW = Instant.parse("2026-10-15T15:00:00Z");

  private final Session session =
      Session.schedule(
          UUID.randomUUID(),
          "UPC",
          UUID.randomUUID(),
          UUID.randomUUID(),
          UUID.randomUUID(),
          NOW.plus(Duration.ofDays(1)),
          NOW.plus(Duration.ofDays(1)).plus(Duration.ofHours(1)),
          NOW);

  @Test
  @DisplayName("belongs to the session and its university")
  void belongsToTheSession() {
    SupportMaterial material = SupportMaterial.preparedFor(session, " A summary ", List.of(), NOW);

    assertThat(material.getSessionId()).isEqualTo(session.getId());
    assertThat(material.getTenantId()).isEqualTo("UPC");
    assertThat(material.getSummary()).isEqualTo("A summary");
    assertThat(material.getGeneratedAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("needs a summary")
  void needsASummary() {
    assertThatThrownBy(() -> SupportMaterial.preparedFor(session, "  ", List.of(), NOW))
        .isInstanceOf(SessionRuleViolation.class);
  }

  @Test
  @DisplayName("keeps at most five suggestions, without blanks, and a summary of 1000 characters")
  void keepsItShort() {
    SupportMaterial material =
        SupportMaterial.preparedFor(
            session,
            "x".repeat(1500),
            Arrays.asList("one", " ", null, "two", "three", "four", "five", "six"),
            NOW);

    assertThat(material.getSuggestions()).containsExactly("one", "two", "three", "four", "five");
    assertThat(material.getSummary()).hasSize(SupportMaterial.MAX_SUMMARY_LENGTH);
  }
}
