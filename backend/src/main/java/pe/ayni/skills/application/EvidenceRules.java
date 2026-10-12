package pe.ayni.skills.application;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pe.ayni.skills.domain.model.SkillsRuleViolation;

/**
 * What a submission of evidence may contain: how many files, of which kinds, how big.
 *
 * <p>A coordinator reads these files, so the kinds are the ones that open without running anything:
 * a PDF or an image. The limits are properties, {@code ayni.skills.evidence.max-files} and {@code
 * ayni.skills.evidence.max-file-bytes}, because they are a decision of the team and not of the code.
 *
 * <p>The type a client declares is a claim, not a fact, so {@link #matchesDeclaredType} also looks
 * at the first bytes of the file.
 */
@Component
public class EvidenceRules {

  static final Set<String> ALLOWED_TYPES = Set.of("application/pdf", "image/png", "image/jpeg");

  private static final byte[] PDF = {'%', 'P', 'D', 'F', '-'};
  private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};
  private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

  private final long maxFileBytes;
  private final int maxFiles;

  EvidenceRules(
      @Value("${ayni.skills.evidence.max-file-bytes:5242880}") long maxFileBytes,
      @Value("${ayni.skills.evidence.max-files:3}") int maxFiles) {
    this.maxFileBytes = maxFileBytes;
    this.maxFiles = maxFiles;
  }

  /**
   * Checks what the student says they are attaching.
   *
   * @throws SkillsRuleViolation when there are no files, too many, or one that is empty, too big or
   *     of a kind that is not accepted
   */
  public void check(List<EvidenceUpload> uploads) {
    Objects.requireNonNull(uploads, "uploads must not be null");
    if (uploads.isEmpty()) {
      throw new SkillsRuleViolation("attach at least one file: a portfolio or a certificate");
    }
    if (uploads.size() > maxFiles) {
      throw new SkillsRuleViolation("at most %d files can be attached".formatted(maxFiles));
    }
    for (EvidenceUpload upload : uploads) {
      checkOne(upload);
    }
  }

  /** Whether the first bytes of the file are those of the type it declares. */
  public boolean matchesDeclaredType(String contentType, byte[] firstBytes) {
    byte[] signature =
        switch (baseType(contentType)) {
          case "application/pdf" -> PDF;
          case "image/png" -> PNG;
          case "image/jpeg" -> JPEG;
          default -> null;
        };
    return signature != null && startsWith(firstBytes, signature);
  }

  /** How many bytes of a file are enough to tell what it is. */
  public int signatureLength() {
    return 8;
  }

  private void checkOne(EvidenceUpload upload) {
    String name = upload.fileName() == null ? "" : upload.fileName();
    if (name.isBlank() || name.length() > 255) {
      throw new SkillsRuleViolation("every file needs a name of at most 255 characters");
    }
    if (upload.sizeBytes() <= 0) {
      throw new SkillsRuleViolation("the file %s is empty".formatted(name));
    }
    if (upload.sizeBytes() > maxFileBytes) {
      throw new SkillsRuleViolation(
          "the file %s is bigger than %d MB".formatted(name, maxFileBytes / (1024 * 1024)));
    }
    if (!ALLOWED_TYPES.contains(baseType(upload.contentType()))) {
      throw new SkillsRuleViolation(
          "the file %s must be a PDF, a PNG or a JPEG".formatted(name));
    }
  }

  /** The type without parameters and in lower case: {@code Application/PDF; x=y} is a PDF. */
  private static String baseType(String contentType) {
    if (contentType == null) {
      return "";
    }
    int parameters = contentType.indexOf(';');
    String base = parameters < 0 ? contentType : contentType.substring(0, parameters);
    return base.strip().toLowerCase(Locale.ROOT);
  }

  private static boolean startsWith(byte[] content, byte[] signature) {
    if (content == null || content.length < signature.length) {
      return false;
    }
    for (int i = 0; i < signature.length; i++) {
      if (content[i] != signature[i]) {
        return false;
      }
    }
    return true;
  }
}
