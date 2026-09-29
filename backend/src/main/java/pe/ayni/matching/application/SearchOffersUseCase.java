package pe.ayni.matching.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.identity.IdentityApi;
import pe.ayni.matching.domain.model.AvailableOffer;
import pe.ayni.matching.domain.model.MatchingRuleViolation;
import pe.ayni.matching.domain.model.SearchWindow;
import pe.ayni.matching.domain.services.NearestOffers;
import pe.ayni.matching.infrastructure.AvailableOfferRepository;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * US01: the hours a student can book for a course, inside the window they can make.
 *
 * <p>It answers from the projection alone, never from booking or skills, which is what keeps the
 * search fast however many tutors a course has (TS07). Only hours that have not started come back:
 * the time is read from the injected clock, like every rule that depends on it.
 *
 * <p>Every tutor free at an hour is returned, the best rated first and new tutors last, and the
 * student chooses. Choosing for them would send every booking to the same few tutors and leave the
 * new ones with nothing, which is why the event storming session rejected it.
 *
 * <p>When nothing falls inside the window, the closest hours outside it come back instead, with
 * {@link OfferSearch#exactMatch()} false so the client can say the window was empty (scenario 2).
 * That answer is a single page of at most {@code size} hours: paging through "the closest" would
 * only mean moving further from what the student asked for.
 */
@Service
public class SearchOffersUseCase {

  /** The largest page a client may ask for. */
  public static final int MAX_PAGE_SIZE = 100;

  private final AvailableOfferRepository offers;
  private final IdentityApi identity;
  private final Clock clock;

  SearchOffersUseCase(AvailableOfferRepository offers, IdentityApi identity, Clock clock) {
    this.offers = offers;
    this.identity = identity;
    this.clock = clock;
  }

  /**
   * @param studentId who is searching: their own hours, if they tutor the course, are left out
   * @throws MatchingRuleViolation when the window does not end after it starts, or the page is out
   *     of range
   * @throws java.util.NoSuchElementException when the university does not exist
   */
  @Transactional(readOnly = true)
  public OfferSearch execute(
      UUID studentId, UUID catalogItemId, Instant from, Instant to, int page, int size) {

    Objects.requireNonNull(studentId, "studentId must not be null");
    Objects.requireNonNull(catalogItemId, "catalogItemId must not be null");
    SearchWindow window = new SearchWindow(from, to);
    if (page < 0) {
      throw new MatchingRuleViolation("page must not be negative");
    }
    if (size < 1 || size > MAX_PAGE_SIZE) {
      throw new MatchingRuleViolation("size must be between 1 and " + MAX_PAGE_SIZE);
    }

    String tenantId = TenantContext.require();
    String timezone = identity.requireTenant(tenantId).timezone();
    Instant now = clock.instant();

    Page<AvailableOffer> within =
        offers.findWithin(
            tenantId, catalogItemId, studentId, now, from, to, PageRequest.of(page, size));
    if (within.getTotalElements() > 0) {
      return new OfferSearch(
          within.getContent().stream().map(FoundOffer::of).toList(),
          true,
          timezone,
          page,
          size,
          within.getTotalElements(),
          within.getTotalPages());
    }

    List<AvailableOffer> nearest =
        NearestOffers.around(
            window,
            offers.findClosestBefore(
                tenantId, catalogItemId, studentId, now, from, Limit.of(size)),
            offers.findClosestAfter(tenantId, catalogItemId, studentId, now, to, Limit.of(size)),
            size);
    List<FoundOffer> shown =
        page == 0 ? nearest.stream().map(FoundOffer::of).toList() : List.of();
    return new OfferSearch(
        shown, false, timezone, page, size, nearest.size(), nearest.isEmpty() ? 0 : 1);
  }
}
