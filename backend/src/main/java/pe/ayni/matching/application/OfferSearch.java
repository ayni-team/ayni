package pe.ayni.matching.application;

import java.util.List;

/**
 * What a search found.
 *
 * @param exactMatch {@code false} when nothing fell inside the window and {@code offers} holds the
 *     closest hours outside it instead
 * @param timezone the university's time zone, in which the client shows the hours; they travel in
 *     UTC like every instant of the API
 */
public record OfferSearch(
    List<FoundOffer> offers,
    boolean exactMatch,
    String timezone,
    int page,
    int size,
    long totalElements,
    int totalPages) {}
