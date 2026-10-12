package pe.ayni.skills.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A tool that a student asks to add to the catalogue because it is not there.
 *
 * <p>It belongs to the university of the student who proposed it, and only they read it back. What
 * a moderator decides is recorded here, so the student can see the decision next to their proposal.
 */
@Entity
@Table(schema = "skills", name = "skill_proposals")
public class SkillProposal {

  public static final int MIN_NAME_LENGTH = 3;
  public static final int MAX_NAME_LENGTH = 160;
  public static final int MAX_DESCRIPTION_LENGTH = 500;
  static final int MAX_REASON_LENGTH = 500;

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "tenant_id", length = 32, nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "proposed_by", nullable = false, updatable = false)
  private UUID proposedBy;

  @Column(name = "category_id", nullable = false, updatable = false)
  private UUID categoryId;

  @Column(name = "name", length = MAX_NAME_LENGTH, nullable = false, updatable = false)
  private String name;

  @Column(name = "description", length = MAX_DESCRIPTION_LENGTH, updatable = false)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", length = 16, nullable = false)
  private ProposalStatus status;

  /** The moderator who decided. {@code null} while the proposal waits. */
  @Column(name = "resolved_by")
  private UUID resolvedBy;

  @Column(name = "resolved_at")
  private Instant resolvedAt;

  @Column(name = "decision_reason", length = MAX_REASON_LENGTH)
  private String decisionReason;

  /** The catalogue item created by an approval. {@code null} until then. */
  @Column(name = "catalog_item_id")
  private UUID catalogItemId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected SkillProposal() {
    // Required by JPA
  }

  private SkillProposal(
      UUID id,
      String tenantId,
      UUID proposedBy,
      UUID categoryId,
      String name,
      String description,
      Instant now) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
    this.proposedBy = Objects.requireNonNull(proposedBy, "proposedBy must not be null");
    this.categoryId = Objects.requireNonNull(categoryId, "categoryId must not be null");
    this.name = name;
    this.description = description;
    this.status = ProposalStatus.PROPOSED;
    this.createdAt = Objects.requireNonNull(now, "now must not be null");
  }

  /**
   * Proposes a tool for the catalogue.
   *
   * @param name what the tool is called; surrounding spaces are dropped
   * @param description a short note on what it is; {@code null} or blank for none
   * @throws SkillsRuleViolation when the name is missing, too short or too long, or the description
   *     is longer than the column holds
   */
  public static SkillProposal propose(
      UUID id,
      String tenantId,
      UUID proposedBy,
      UUID categoryId,
      String name,
      String description,
      Instant now) {
    return new SkillProposal(
        id, tenantId, proposedBy, categoryId, requireName(name), cleanDescription(description), now);
  }

  public boolean isWaiting() {
    return this.status == ProposalStatus.PROPOSED;
  }

  private static String requireName(String name) {
    String stripped = name == null ? "" : name.strip();
    if (stripped.length() < MIN_NAME_LENGTH || stripped.length() > MAX_NAME_LENGTH) {
      throw new SkillsRuleViolation(
          "the name must have between %d and %d characters"
              .formatted(MIN_NAME_LENGTH, MAX_NAME_LENGTH));
    }
    return stripped;
  }

  private static String cleanDescription(String description) {
    if (description == null || description.isBlank()) {
      return null;
    }
    String stripped = description.strip();
    if (stripped.length() > MAX_DESCRIPTION_LENGTH) {
      throw new SkillsRuleViolation(
          "the description can have at most %d characters".formatted(MAX_DESCRIPTION_LENGTH));
    }
    return stripped;
  }

  public UUID getId() {
    return id;
  }

  public String getTenantId() {
    return tenantId;
  }

  public UUID getProposedBy() {
    return proposedBy;
  }

  public UUID getCategoryId() {
    return categoryId;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public ProposalStatus getStatus() {
    return status;
  }

  public UUID getResolvedBy() {
    return resolvedBy;
  }

  public Instant getResolvedAt() {
    return resolvedAt;
  }

  public String getDecisionReason() {
    return decisionReason;
  }

  public UUID getCatalogItemId() {
    return catalogItemId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
