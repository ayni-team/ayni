package pe.ayni.matching.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.matching.application.SearchOffersUseCase;
import pe.ayni.shared.tenancy.CurrentUser;

/**
 * The tutor search.
 *
 * <p>The controller only translates HTTP into the use case. The student is read from {@link
 * CurrentUser}, so the search can leave out their own hours without taking anybody's id as a
 * parameter.
 */
@RestController
@RequestMapping("/api/v1/search")
@Validated
@Tag(name = "Search", description = "Finding hours with a tutor free for a course")
class SearchOffersController {

  private final SearchOffersUseCase searchOffers;

  SearchOffersController(SearchOffersUseCase searchOffers) {
    this.searchOffers = searchOffers;
  }

  @GetMapping("/offers")
  @Operation(
      summary = "Find the hours with a tutor free for a course",
      description =
          "One hour blocks of every tutor enabled for the course that start inside [from, to) and "
              + "have not started yet, ordered by time and, at the same hour, by the tutor's "
              + "average in the course with new tutors last. All the tutors free at an hour are "
              + "returned: the student chooses. When nothing falls inside the window the closest "
              + "hours outside it are returned instead, with exactMatch false, as a single page. "
              + "Instants travel in UTC; timezone says in which zone to show them. The student's "
              + "own hours are left out.")
  @Parameter(
      in = ParameterIn.HEADER,
      name = "X-Tenant-Id",
      required = true,
      description = "University the request belongs to. Read by TenantFilter",
      schema = @Schema(type = "string", example = "UPC"))
  @Parameter(
      in = ParameterIn.HEADER,
      name = "X-User-Id",
      required = true,
      description = "Student searching. Read by CurrentUserFilter",
      schema = @Schema(type = "string", format = "uuid",
          example = "11111111-1111-4111-8111-111111111111"))
  @ApiResponse(
      responseCode = "200",
      description = "The hours found inside the window, or the closest ones outside it",
      content =
          @Content(
              schema = @Schema(implementation = OfferSearchResponse.class),
              examples =
                  @ExampleObject(
                      name = "Two tutors free at the same hour",
                      value =
                          """
                          {"offers":[
                            {"blockId":"c0000000-0000-4000-8000-000000000001",
                             "tutorId":"22222222-2222-4222-8222-222222222222",
                             "tutorName":"Bruno Salas",
                             "catalogItemId":"b0000000-0000-4000-8000-000000000102",
                             "startsAt":"2026-09-30T20:00:00Z","endsAt":"2026-09-30T21:00:00Z",
                             "averageStars":4.67,"ratingsCount":3,"sessionsTaught":5,
                             "newTutor":false},
                            {"blockId":"c0000000-0000-4000-8000-000000000002",
                             "tutorId":"33333333-3333-4333-8333-333333333333",
                             "tutorName":"Carla Ruiz",
                             "catalogItemId":"b0000000-0000-4000-8000-000000000102",
                             "startsAt":"2026-09-30T20:00:00Z","endsAt":"2026-09-30T21:00:00Z",
                             "averageStars":null,"ratingsCount":1,"sessionsTaught":1,
                             "newTutor":true}],
                           "exactMatch":true,"timezone":"America/Lima",
                           "page":0,"size":20,"totalElements":2,"totalPages":1}
                          """)))
  @ApiResponse(
      responseCode = "400",
      description =
          "A parameter is missing or invalid, from is not before to, or a header is missing",
      content =
          @Content(
              schema = @Schema(implementation = ApiError.class),
              examples =
                  @ExampleObject(
                      name = "Window that ends before it starts",
                      value =
                          """
                          {"timestamp":"2026-09-29T21:00:00Z","status":400,"error":"Bad Request",
                           "message":"from must be before to","path":"/api/v1/search/offers"}
                          """)))
  @ApiResponse(
      responseCode = "404",
      description = "The university does not exist",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  OfferSearchResponse search(
      @Parameter(description = "The course (catalogue item) to find a tutor for",
              example = "b0000000-0000-4000-8000-000000000102")
          @RequestParam
          UUID catalogItemId,
      @Parameter(description = "First instant of the window, included. ISO 8601 UTC",
              example = "2026-09-30T13:00:00Z")
          @RequestParam
          Instant from,
      @Parameter(description = "Last instant of the window, excluded. ISO 8601 UTC",
              example = "2026-10-01T01:00:00Z")
          @RequestParam
          Instant to,
      @Parameter(description = "Page number, from 0", example = "0")
          @RequestParam(defaultValue = "0")
          @Min(0)
          int page,
      @Parameter(description = "Hours per page, at most 100", example = "20")
          @RequestParam(defaultValue = "20")
          @Min(1)
          @Max(SearchOffersUseCase.MAX_PAGE_SIZE)
          int size) {
    return OfferSearchResponse.of(
        searchOffers.execute(CurrentUser.require(), catalogItemId, from, to, page, size));
  }
}
