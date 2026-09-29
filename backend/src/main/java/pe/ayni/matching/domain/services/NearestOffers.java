package pe.ayni.matching.domain.services;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import pe.ayni.matching.domain.model.AvailableOffer;
import pe.ayni.matching.domain.model.SearchWindow;

/**
 * What a search answers when nothing falls inside the window: the offers closest to it, on either
 * side, instead of an empty page (US01, scenario 2).
 *
 * <p>An empty answer leaves a student with an exam on Friday nowhere to go; an hour on Wednesday
 * evening instead of Wednesday afternoon may be exactly what they need.
 */
public final class NearestOffers {

  private NearestOffers() {}

  /**
   * Chooses the {@code limit} offers closest to the window among the candidates before and after
   * it, and returns them in {@linkplain AvailableOffer#SEARCH_ORDER search order}, so the answer
   * reads like any other.
   *
   * <p>Each side only needs its own {@code limit} closest candidates: nothing further away on that
   * side could make the cut.
   */
  public static List<AvailableOffer> around(
      SearchWindow window, List<AvailableOffer> before, List<AvailableOffer> after, int limit) {
    if (limit < 1) {
      throw new IllegalArgumentException("limit must be at least 1");
    }
    Comparator<AvailableOffer> closestFirst =
        Comparator.comparing((AvailableOffer offer) -> window.distanceTo(offer.getStartsAt()))
            .thenComparing(AvailableOffer.SEARCH_ORDER);

    return Stream.concat(before.stream(), after.stream())
        .sorted(closestFirst)
        .limit(limit)
        .sorted(AvailableOffer.SEARCH_ORDER)
        .toList();
  }
}
