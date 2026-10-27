package pe.ayni.matching.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * One hour a tutor is free, offered for one course they are enabled to teach: what a student finds.
 *
 * <p>Nothing here is decided by matching. The block and its hour come from booking, the course from
 * skills, the name from identity and the standing from reputation; matching copies them when their
 * events arrive so that a search reads one table and joins nothing. A row that went stale is
 * harmless, because booking checks the hour again when it is held and when it is booked.
 *
 * <p>{@link Persistable} because the key is assigned, not generated: without it every insert would
 * first read the row to find out whether it exists, which the projection already knows.
 */
@Entity
@IdClass(AvailableOfferId.class)
@Table(schema = "matching", name = "available_offers")
public class AvailableOffer implements Persistable<AvailableOfferId> {

  /** Every block is one hour long, because one credit buys one hour. */
  public static final Duration BLOCK_LENGTH = Duration.ofHours(1);

  /**
   * The order a student reads offers in: by time, and at the same hour the best rated tutor first,
   * with new tutors after every tutor who has an average. The name and the tutor only make the order
   * stable. The repository query sorts the same way.
   */
  public static final Comparator<AvailableOffer> SEARCH_ORDER =
      Comparator.comparing(AvailableOffer::getStartsAt)
          .thenComparing(
              AvailableOffer::getAverageStars, Comparator.nullsLast(Comparator.reverseOrder()))
          .thenComparing(AvailableOffer::getTutorName)
          .thenComparing(AvailableOffer::getTutorId);

  @Id
  @Column(name = "tenant_id", length = 32, nullable = false, updatable = false)
  private String tenantId;

  @Id
  @Column(name = "block_id", nullable = false, updatable = false)
  private UUID blockId;

  @Id
  @Column(name = "catalog_item_id", nullable = false, updatable = false)
  private UUID catalogItemId;

  @Column(name = "tutor_id", nullable = false, updatable = false)
  private UUID tutorId;

  @Column(name = "starts_at", nullable = false, updatable = false)
  private Instant startsAt;

  @Column(name = "tutor_name", length = 160, nullable = false)
  private String tutorName;

  @Column(name = "average_stars", precision = 3, scale = 2)
  private BigDecimal averageStars;

  @Column(name = "ratings_count", nullable = false)
  private int ratingsCount;

  @Column(name = "sessions_taught", nullable = false)
  private int sessionsTaught;

  /** Whether this object has never been stored. Not a column. */
  @Transient private boolean fresh = true;

  protected AvailableOffer() {
    // Required by JPA
  }

  public AvailableOffer(
      String tenantId,
      UUID blockId,
      UUID catalogItemId,
      UUID tutorId,
      Instant startsAt,
      String tutorName,
      Standing standing) {
    this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
    this.blockId = Objects.requireNonNull(blockId, "blockId must not be null");
    this.catalogItemId = Objects.requireNonNull(catalogItemId, "catalogItemId must not be null");
    this.tutorId = Objects.requireNonNull(tutorId, "tutorId must not be null");
    this.startsAt = Objects.requireNonNull(startsAt, "startsAt must not be null");
    this.tutorName = Objects.requireNonNull(tutorName, "tutorName must not be null");
    Objects.requireNonNull(standing, "standing must not be null");
    this.averageStars = standing.averageStars();
    this.ratingsCount = standing.ratingsCount();
    this.sessionsTaught = standing.sessionsTaught();
  }

  /** When the hour ends. Not stored: every block lasts {@link #BLOCK_LENGTH}. */
  public Instant getEndsAt() {
    return startsAt.plus(BLOCK_LENGTH);
  }

  /** Whether the tutor is shown as new in this course, with no average. */
  public boolean isNewTutor() {
    return averageStars == null;
  }

  @Override
  public AvailableOfferId getId() {
    return new AvailableOfferId(tenantId, blockId, catalogItemId);
  }

  @Override
  public boolean isNew() {
    return fresh;
  }

  @PostLoad
  @PostPersist
  void markStored() {
    this.fresh = false;
  }

  public String getTenantId() {
    return tenantId;
  }

  public UUID getBlockId() {
    return blockId;
  }

  public UUID getCatalogItemId() {
    return catalogItemId;
  }

  public UUID getTutorId() {
    return tutorId;
  }

  public Instant getStartsAt() {
    return startsAt;
  }

  public String getTutorName() {
    return tutorName;
  }

  public BigDecimal getAverageStars() {
    return averageStars;
  }

  public int getRatingsCount() {
    return ratingsCount;
  }

  public int getSessionsTaught() {
    return sessionsTaught;
  }
}
