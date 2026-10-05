package pe.ayni.skills.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A file a student attached to a submission: a portfolio, a certificate.
 *
 * <p>The file itself is not here. It lives in storage and this row keeps the key to find it, so the
 * database never grows with what students upload. Which types and sizes are accepted is decided
 * before a file gets this far; this class only refuses what the columns cannot hold.
 */
@Entity
@Table(schema = "skills", name = "evidence_files")
public class EvidenceFile {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "tenant_id", length = 32, nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "validation_request_id", nullable = false, updatable = false)
  private UUID validationRequestId;

  @Column(name = "file_name", length = 255, nullable = false, updatable = false)
  private String fileName;

  @Column(name = "storage_key", length = 512, nullable = false, updatable = false)
  private String storageKey;

  @Column(name = "content_type", length = 100, nullable = false, updatable = false)
  private String contentType;

  @Column(name = "size_bytes", nullable = false, updatable = false)
  private long sizeBytes;

  @Column(name = "uploaded_at", nullable = false, updatable = false)
  private Instant uploadedAt;

  protected EvidenceFile() {
    // Required by JPA
  }

  /**
   * @throws SkillsRuleViolation when a text is blank or too long for its column, or the file is
   *     empty
   */
  public EvidenceFile(
      UUID id,
      String tenantId,
      UUID validationRequestId,
      String fileName,
      String storageKey,
      String contentType,
      long sizeBytes,
      Instant now) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
    this.validationRequestId =
        Objects.requireNonNull(validationRequestId, "validationRequestId must not be null");
    this.fileName = text(fileName, 255, "file name");
    this.storageKey = text(storageKey, 512, "storage key");
    this.contentType = text(contentType, 100, "content type");
    if (sizeBytes <= 0) {
      throw new SkillsRuleViolation("an evidence file cannot be empty");
    }
    this.sizeBytes = sizeBytes;
    this.uploadedAt = Objects.requireNonNull(now, "now must not be null");
  }

  private static String text(String value, int maxLength, String field) {
    if (value == null || value.isBlank()) {
      throw new SkillsRuleViolation("the %s must not be blank".formatted(field));
    }
    if (value.length() > maxLength) {
      throw new SkillsRuleViolation("the %s can have at most %d characters".formatted(field, maxLength));
    }
    return value;
  }

  public UUID getId() {
    return id;
  }

  public String getTenantId() {
    return tenantId;
  }

  public UUID getValidationRequestId() {
    return validationRequestId;
  }

  public String getFileName() {
    return fileName;
  }

  public String getStorageKey() {
    return storageKey;
  }

  public String getContentType() {
    return contentType;
  }

  public long getSizeBytes() {
    return sizeBytes;
  }

  public Instant getUploadedAt() {
    return uploadedAt;
  }
}
