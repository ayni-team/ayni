package pe.ayni.matching.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import pe.ayni.matching.application.FoundOffer;
import pe.ayni.matching.application.OfferSearch;

/**
 * A page of offers.
 *
 * <p>Described with its own fields rather than returned as a Spring {@code Page}, whose shape is an
 * implementation detail of the framework, the same way wallet and skills answer their lists.
 */
@Schema(
    name = "OfferSearch",
    description = "Hours with a tutor free for the course, by time and then by rating")
public record OfferSearchResponse(
    List<Offer> offers,
    @Schema(
            description =
                "false when nothing fell inside [from, to) and offers holds the closest hours "
                    + "outside it instead",
            example = "true")
        boolean exactMatch,
    @Schema(
            description =
                "Time zone of the university. Every instant travels in UTC; show them in this zone",
            example = "America/Lima")
        String timezone,
    @Schema(example = "0") int page,
    @Schema(example = "20") int size,
    @Schema(example = "2") long totalElements,
    @Schema(example = "1") int totalPages) {

  /** One hour a tutor is free for the course. */
  @Schema(name = "Offer")
  public record Offer(
      @Schema(description = "The one hour block. Hold and book it through booking",
              example = "c0000000-0000-4000-8000-000000000001")
          UUID blockId,
      @Schema(example = "22222222-2222-4222-8222-222222222222") UUID tutorId,
      @Schema(example = "Bruno Salas") String tutorName,
      @Schema(example = "b0000000-0000-4000-8000-000000000102") UUID catalogItemId,
      @Schema(description = "Start of the hour, UTC", example = "2026-09-30T20:00:00Z")
          Instant startsAt,
      @Schema(description = "End of the hour, UTC", example = "2026-09-30T21:00:00Z")
          Instant endsAt,
      @Schema(
              description = "The tutor's average in this course; null while they are new",
              nullable = true,
              example = "4.67")
          BigDecimal averageStars,
      @Schema(description = "Ratings received in this course", example = "3") int ratingsCount,
      @Schema(description = "Sessions taught in this course", example = "5") int sessionsTaught,
      @Schema(
              description = "Fewer than three ratings in this course: no average is shown",
              example = "false")
          boolean newTutor) {

    static Offer of(FoundOffer found) {
      return new Offer(
          found.blockId(),
          found.tutorId(),
          found.tutorName(),
          found.catalogItemId(),
          found.startsAt(),
          found.endsAt(),
          found.averageStars(),
          found.ratingsCount(),
          found.sessionsTaught(),
          found.newTutor());
    }
  }

  static OfferSearchResponse of(OfferSearch search) {
    return new OfferSearchResponse(
        search.offers().stream().map(Offer::of).toList(),
        search.exactMatch(),
        search.timezone(),
        search.page(),
        search.size(),
        search.totalElements(),
        search.totalPages());
  }
}
