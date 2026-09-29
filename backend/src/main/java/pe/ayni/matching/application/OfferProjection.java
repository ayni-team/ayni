package pe.ayni.matching.application;

import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.booking.BookingApi;
import pe.ayni.identity.IdentityApi;
import pe.ayni.matching.domain.model.AvailableOffer;
import pe.ayni.matching.domain.model.AvailableOfferId;
import pe.ayni.matching.domain.model.Standing;
import pe.ayni.matching.infrastructure.AvailableOfferRepository;
import pe.ayni.reputation.ReputationApi;
import pe.ayni.reputation.TutorStandingView;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.SkillsApi;

/**
 * Keeps {@code matching.available_offers} in step with what the other modules announce.
 *
 * <p>One row per hour a tutor is free and per course they are enabled to teach. Availability
 * belongs to the tutor, not to a course, so when either side changes the rows are worked out again
 * from the other: new hours are offered for every enabled course, a newly enabled course for every
 * hour already open.
 *
 * <p>Every method is safe to run twice. Events are delivered in memory today and will be retried
 * once the publication registry arrives (ADR 0005), and a retry must not duplicate an offer nor
 * fail on one that is already there. Adding writes only the offers that are missing; removing and
 * refreshing are the same whatever the rows were.
 *
 * <p>The projection is allowed to go stale, because booking checks the hour against its own tables
 * when a student holds it and when they book it. That is why a hold does not remove anything: it
 * lasts five minutes, and ends either in a booking, which is announced, or back in circulation.
 */
@Service
public class OfferProjection {

  private final AvailableOfferRepository offers;
  private final BookingApi booking;
  private final SkillsApi skills;
  private final IdentityApi identity;
  private final ReputationApi reputation;
  private final Clock clock;

  OfferProjection(
      AvailableOfferRepository offers,
      BookingApi booking,
      SkillsApi skills,
      IdentityApi identity,
      ReputationApi reputation,
      Clock clock) {
    this.offers = offers;
    this.booking = booking;
    this.skills = skills;
    this.identity = identity;
    this.reputation = reputation;
    this.clock = clock;
  }

  /**
   * Offers new hours of a tutor for every course they are enabled to teach.
   *
   * @return the offers written now
   */
  @Transactional
  public int offerHours(UUID tutorId, List<OpenHour> hours) {
    return offer(tutorId, hours, skills.enabledSkillsOf(tutorId));
  }

  /**
   * Offers a newly enabled course in every hour the tutor already has open.
   *
   * <p>Skills is asked again rather than trusting the event: a course withdrawn since it was
   * enabled must not come back because the older event was handled late.
   *
   * @return the offers written now
   */
  @Transactional
  public int offerCourse(UUID tutorId, UUID catalogItemId) {
    if (!skills.enabledSkillsOf(tutorId).contains(catalogItemId)) {
      return 0;
    }
    return offer(tutorId, openHoursOf(tutorId), List.of(catalogItemId));
  }

  /**
   * Offers again the hours a cancellation gave back, if booking still has them open.
   *
   * <p>Booking decides whether a cancelled hour returns to circulation; matching only repeats what
   * booking says, so an hour booking keeps out stays out of the search too.
   *
   * @return the offers written now
   */
  @Transactional
  public int offerReleasedHours(UUID tutorId, Collection<UUID> releasedBlockIds) {
    if (releasedBlockIds.isEmpty()) {
      return 0;
    }
    Set<UUID> released = Set.copyOf(releasedBlockIds);
    List<OpenHour> stillOpen =
        openHoursOf(tutorId).stream().filter(hour -> released.contains(hour.blockId())).toList();
    return offer(tutorId, stillOpen, skills.enabledSkillsOf(tutorId));
  }

  /**
   * Stops offering these hours, for every course: they were booked or withdrawn.
   *
   * @return the offers removed
   */
  @Transactional
  public int withdrawHours(Collection<UUID> blockIds) {
    if (blockIds.isEmpty()) {
      return 0;
    }
    return offers.deleteBlocks(TenantContext.require(), blockIds);
  }

  /**
   * Stops offering a course of a tutor, in every hour.
   *
   * @return the offers removed
   */
  @Transactional
  public int withdrawCourse(UUID tutorId, UUID catalogItemId) {
    return offers.deleteSkill(TenantContext.require(), tutorId, catalogItemId);
  }

  /**
   * Copies again the tutor's standing in a course into every offer of that course.
   *
   * @return the offers refreshed
   */
  @Transactional
  public int refreshStanding(UUID tutorId, UUID catalogItemId) {
    Standing standing = standingOf(tutorId, catalogItemId);
    return offers.updateStanding(
        TenantContext.require(),
        tutorId,
        catalogItemId,
        standing.averageStars(),
        standing.ratingsCount(),
        standing.sessionsTaught());
  }

  /** Writes the offers of these hours and courses that are not there yet. */
  private int offer(UUID tutorId, List<OpenHour> hours, List<UUID> catalogItemIds) {
    Objects.requireNonNull(tutorId, "tutorId must not be null");
    if (hours.isEmpty() || catalogItemIds.isEmpty()) {
      return 0;
    }
    String tenantId = TenantContext.require();

    Set<AvailableOfferId> existing =
        offers
            .findByTenantIdAndBlockIdIn(
                tenantId, hours.stream().map(OpenHour::blockId).collect(Collectors.toSet()))
            .stream()
            .map(AvailableOffer::getId)
            .collect(Collectors.toSet());

    // Read once per event rather than once per row: a month of hours is a hundred rows.
    String tutorName = identity.requireUser(tutorId).fullName();
    Map<UUID, Standing> standings = new LinkedHashMap<>();
    for (UUID catalogItemId : catalogItemIds) {
      standings.put(catalogItemId, standingOf(tutorId, catalogItemId));
    }

    List<AvailableOffer> missing =
        hours.stream()
            .flatMap(
                hour ->
                    standings.entrySet().stream()
                        .map(
                            course ->
                                new AvailableOffer(
                                    tenantId,
                                    hour.blockId(),
                                    course.getKey(),
                                    tutorId,
                                    hour.startsAt(),
                                    tutorName,
                                    course.getValue())))
            .filter(offer -> !existing.contains(offer.getId()))
            .toList();

    offers.saveAll(missing);
    return missing.size();
  }

  private List<OpenHour> openHoursOf(UUID tutorId) {
    return booking.openHoursOf(tutorId, clock.instant()).stream()
        .map(hour -> new OpenHour(hour.blockId(), hour.startsAt()))
        .toList();
  }

  /** What reputation says of the tutor in the course; a tutor who never taught it has nothing. */
  private Standing standingOf(UUID tutorId, UUID catalogItemId) {
    return reputation
        .standingOf(tutorId, catalogItemId)
        .map(OfferProjection::toStanding)
        .orElseGet(Standing::none);
  }

  private static Standing toStanding(TutorStandingView view) {
    return new Standing(view.averageStars(), view.ratingsCount(), view.sessionsTaught());
  }
}
