package pe.ayni.skills.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.ayni.skills.domain.SkillsFixtures.NOW;
import static pe.ayni.skills.domain.SkillsFixtures.UPC;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.skills.domain.model.EvidenceFile;
import pe.ayni.skills.domain.model.SkillsRuleViolation;

/** A file attached to a submission: only what the columns can hold gets this far. */
class EvidenceFileTest {

  private static EvidenceFile file(String name, String key, String type, long size) {
    return new EvidenceFile(UUID.randomUUID(), UPC, UUID.randomUUID(), name, key, type, size, NOW);
  }

  @Test
  @DisplayName("a file keeps the pointer to where it is stored, not the file")
  void aFileKeepsThePointerToWhereItIsStored() {
    EvidenceFile evidence = file("portfolio.pdf", "UPC/abc/portfolio.pdf", "application/pdf", 2048);

    assertThat(evidence.getFileName()).isEqualTo("portfolio.pdf");
    assertThat(evidence.getStorageKey()).isEqualTo("UPC/abc/portfolio.pdf");
    assertThat(evidence.getContentType()).isEqualTo("application/pdf");
    assertThat(evidence.getSizeBytes()).isEqualTo(2048);
    assertThat(evidence.getUploadedAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("a blank name, key or type is refused")
  void aBlankNameKeyOrTypeIsRefused() {
    assertThatThrownBy(() -> file(" ", "key", "application/pdf", 1)).isInstanceOf(SkillsRuleViolation.class);
    assertThatThrownBy(() -> file("a.pdf", "", "application/pdf", 1)).isInstanceOf(SkillsRuleViolation.class);
    assertThatThrownBy(() -> file("a.pdf", "key", null, 1)).isInstanceOf(SkillsRuleViolation.class);
  }

  @Test
  @DisplayName("a name, key or type longer than its column is refused")
  void aTextLongerThanItsColumnIsRefused() {
    assertThatThrownBy(() -> file("x".repeat(256), "key", "application/pdf", 1))
        .isInstanceOf(SkillsRuleViolation.class);
    assertThatThrownBy(() -> file("a.pdf", "x".repeat(513), "application/pdf", 1))
        .isInstanceOf(SkillsRuleViolation.class);
    assertThatThrownBy(() -> file("a.pdf", "key", "x".repeat(101), 1))
        .isInstanceOf(SkillsRuleViolation.class);
    assertThat(file("x".repeat(255), "x".repeat(512), "x".repeat(100), 1)).isNotNull();
  }

  @Test
  @DisplayName("an empty file is refused")
  void anEmptyFileIsRefused() {
    assertThatThrownBy(() -> file("a.pdf", "key", "application/pdf", 0))
        .isInstanceOf(SkillsRuleViolation.class);
  }
}
