package pe.ayni.matching.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.ayni.matching.domain.model.AvailableOffer;
import pe.ayni.matching.domain.model.AvailableOfferId;

/**
 * The search projection.
 *
 * <p>Every method takes the university: until the database enforces the separation by itself, a
 * query without that filter reads another university's data.
 *
 * <p>The three search queries share their conditions: one course, hours that have not started,
 * never the student's own. A tutor looking for help in a course they also teach would otherwise
 * find themselves, and booking refuses a tutor their own hours.
 */
public interface AvailableOfferRepository extends JpaRepository<AvailableOffer, AvailableOfferId> {

  /**
   * The offers inside a window, in {@link AvailableOffer#SEARCH_ORDER}: by time, then the best rated
   * tutor, with new tutors last. The index on {@code (tenant_id, catalog_item_id, starts_at)} is
   * what keeps this cheap.
   */
  @Query(
      value =
          """
          select offer from AvailableOffer offer
          where offer.tenantId = :tenantId
            and offer.catalogItemId = :catalogItemId
            and offer.tutorId <> :studentId
            and offer.startsAt > :now
            and offer.startsAt >= :from
            and offer.startsAt < :to
          order by offer.startsAt, offer.averageStars desc nulls last, offer.tutorName,
                   offer.tutorId
          """,
      countQuery =
          """
          select count(offer) from AvailableOffer offer
          where offer.tenantId = :tenantId
            and offer.catalogItemId = :catalogItemId
            and offer.tutorId <> :studentId
            and offer.startsAt > :now
            and offer.startsAt >= :from
            and offer.startsAt < :to
          """)
  Page<AvailableOffer> findWithin(
      @Param("tenantId") String tenantId,
      @Param("catalogItemId") UUID catalogItemId,
      @Param("studentId") UUID studentId,
      @Param("now") Instant now,
      @Param("from") Instant from,
      @Param("to") Instant to,
      Pageable pageable);

  /** The offers closest before a window that have not started, the latest first. */
  @Query(
      """
      select offer from AvailableOffer offer
      where offer.tenantId = :tenantId
        and offer.catalogItemId = :catalogItemId
        and offer.tutorId <> :studentId
        and offer.startsAt > :now
        and offer.startsAt < :before
      order by offer.startsAt desc
      """)
  List<AvailableOffer> findClosestBefore(
      @Param("tenantId") String tenantId,
      @Param("catalogItemId") UUID catalogItemId,
      @Param("studentId") UUID studentId,
      @Param("now") Instant now,
      @Param("before") Instant before,
      Limit limit);

  /** The offers closest after a window that have not started, the earliest first. */
  @Query(
      """
      select offer from AvailableOffer offer
      where offer.tenantId = :tenantId
        and offer.catalogItemId = :catalogItemId
        and offer.tutorId <> :studentId
        and offer.startsAt > :now
        and offer.startsAt >= :after
      order by offer.startsAt
      """)
  List<AvailableOffer> findClosestAfter(
      @Param("tenantId") String tenantId,
      @Param("catalogItemId") UUID catalogItemId,
      @Param("studentId") UUID studentId,
      @Param("now") Instant now,
      @Param("after") Instant after,
      Limit limit);

  /** The offers already written for these blocks, whatever their course. */
  List<AvailableOffer> findByTenantIdAndBlockIdIn(String tenantId, Collection<UUID> blockIds);

  /** Removes every offer of these blocks, one per course the tutor teaches. */
  @Modifying
  @Query(
      """
      delete from AvailableOffer offer
      where offer.tenantId = :tenantId
        and offer.blockId in :blockIds
      """)
  int deleteBlocks(
      @Param("tenantId") String tenantId, @Param("blockIds") Collection<UUID> blockIds);

  /** Removes every offer of a tutor for one course. */
  @Modifying
  @Query(
      """
      delete from AvailableOffer offer
      where offer.tenantId = :tenantId
        and offer.tutorId = :tutorId
        and offer.catalogItemId = :catalogItemId
      """)
  int deleteSkill(
      @Param("tenantId") String tenantId,
      @Param("tutorId") UUID tutorId,
      @Param("catalogItemId") UUID catalogItemId);

  /** Rewrites the standing copied into every offer of a tutor for one course. */
  @Modifying
  @Query(
      """
      update AvailableOffer offer
      set offer.averageStars = :averageStars,
          offer.ratingsCount = :ratingsCount,
          offer.sessionsTaught = :sessionsTaught
      where offer.tenantId = :tenantId
        and offer.tutorId = :tutorId
        and offer.catalogItemId = :catalogItemId
      """)
  int updateStanding(
      @Param("tenantId") String tenantId,
      @Param("tutorId") UUID tutorId,
      @Param("catalogItemId") UUID catalogItemId,
      @Param("averageStars") BigDecimal averageStars,
      @Param("ratingsCount") int ratingsCount,
      @Param("sessionsTaught") int sessionsTaught);
}
