package pe.ayni.skills.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.skills.domain.model.SkillsRuleViolation;

/** What a submission of evidence may contain. */
class EvidenceRulesTest {

  private static final long FIVE_MB = 5L * 1024 * 1024;

  private final EvidenceRules rules = new EvidenceRules(FIVE_MB, 3);

  private static EvidenceUpload pdf(long size) {
    return new EvidenceUpload("portfolio.pdf", "application/pdf", size);
  }

  @Test
  @DisplayName("a portfolio and a certificate within the limits are accepted")
  void filesWithinTheLimitsAreAccepted() {
    assertThatCode(
            () ->
                rules.check(
                    List.of(
                        pdf(FIVE_MB),
                        new EvidenceUpload("certificate.png", "image/png", 1),
                        new EvidenceUpload("badge.jpg", "image/jpeg", 2048))))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("a submission with no files is refused")
  void aSubmissionWithNoFilesIsRefused() {
    assertThatThrownBy(() -> rules.check(List.of())).isInstanceOf(SkillsRuleViolation.class);
  }

  @Test
  @DisplayName("more files than the limit are refused")
  void moreFilesThanTheLimitAreRefused() {
    assertThatThrownBy(() -> rules.check(List.of(pdf(1), pdf(1), pdf(1), pdf(1))))
        .isInstanceOf(SkillsRuleViolation.class)
        .hasMessageContaining("3");
  }

  @Test
  @DisplayName("a file over the size limit or an empty one is refused")
  void aFileOverTheLimitOrEmptyIsRefused() {
    assertThatThrownBy(() -> rules.check(List.of(pdf(FIVE_MB + 1))))
        .isInstanceOf(SkillsRuleViolation.class)
        .hasMessageContaining("5 MB");
    assertThatThrownBy(() -> rules.check(List.of(pdf(0)))).isInstanceOf(SkillsRuleViolation.class);
  }

  @Test
  @DisplayName("a kind that is not a PDF or an image is refused")
  void aKindThatIsNotAcceptedIsRefused() {
    assertThatThrownBy(
            () -> rules.check(List.of(new EvidenceUpload("run.exe", "application/x-msdownload", 10))))
        .isInstanceOf(SkillsRuleViolation.class);
    assertThatThrownBy(() -> rules.check(List.of(new EvidenceUpload("page.html", "text/html", 10))))
        .isInstanceOf(SkillsRuleViolation.class);
    assertThatThrownBy(() -> rules.check(List.of(new EvidenceUpload("x", null, 10))))
        .isInstanceOf(SkillsRuleViolation.class);
  }

  @Test
  @DisplayName("the kind is read without its parameters and ignoring case")
  void theKindIsReadWithoutParametersAndIgnoringCase() {
    assertThatCode(
            () ->
                rules.check(
                    List.of(new EvidenceUpload("a.pdf", " Application/PDF; charset=binary ", 10))))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("a file without a usable name is refused")
  void aFileWithoutAUsableNameIsRefused() {
    assertThatThrownBy(() -> rules.check(List.of(new EvidenceUpload(" ", "application/pdf", 10))))
        .isInstanceOf(SkillsRuleViolation.class);
    assertThatThrownBy(() -> rules.check(List.of(new EvidenceUpload(null, "application/pdf", 10))))
        .isInstanceOf(SkillsRuleViolation.class);
    assertThatThrownBy(
            () -> rules.check(List.of(new EvidenceUpload("x".repeat(256), "application/pdf", 10))))
        .isInstanceOf(SkillsRuleViolation.class);
  }

  @Test
  @DisplayName("the first bytes of the file must be those of the kind it declares")
  void theFirstBytesMustMatchTheDeclaredKind() {
    byte[] pdf = {'%', 'P', 'D', 'F', '-', '1', '.', '7'};
    byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0};

    assertThat(rules.matchesDeclaredType("application/pdf", pdf)).isTrue();
    assertThat(rules.matchesDeclaredType("image/png", png)).isTrue();
    assertThat(rules.matchesDeclaredType("IMAGE/JPEG; q=1", jpeg)).isTrue();

    // A program renamed as a certificate says it is a PDF and is not one.
    byte[] program = {'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0};
    assertThat(rules.matchesDeclaredType("application/pdf", program)).isFalse();
    assertThat(rules.matchesDeclaredType("image/png", pdf)).isFalse();
    assertThat(rules.matchesDeclaredType("application/pdf", new byte[] {'%', 'P'})).isFalse();
    assertThat(rules.matchesDeclaredType("application/pdf", null)).isFalse();
    assertThat(rules.matchesDeclaredType("text/html", pdf)).isFalse();
  }
}
