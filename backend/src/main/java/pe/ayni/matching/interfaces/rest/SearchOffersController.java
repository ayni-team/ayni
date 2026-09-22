package pe.ayni.matching.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.matching.application.SearchAvailableOffersUseCase;

/**
 * What a student can search for: bookable hours for a course.
 *
 * <p>Reads only from the matching projection; it never queries booking or skills. When nothing
 * falls inside the requested range, the response still carries offers — the closest ones outside
 * it — with {@code exactMatch: false} so the client can say so instead of showing an empty page.
 */
@RestController
@RequestMapping("/api/v1/search")
@Validated
@Tag(name = "Matching", description = "Search for bookable tutoring hours")
class SearchOffersController {

    private final SearchAvailableOffersUseCase search;

    SearchOffersController(SearchAvailableOffersUseCase search) {
        this.search = search;
    }

    @GetMapping("/offers")
    @Operation(
            summary = "Bookable hours for a course within a date range",
            description =
                    """
                    Concrete one-hour blocks a tutor has open for the given course, within [from, to).
           
                    When no block falls inside that range, the closest ones outside it are returned instead \
                    of an empty list, and `exactMatch` is `false` so the client knows to say the exact slot \
                    was empty rather than showing them as if they matched.
                    """)
    @Parameter(
            in = ParameterIn.HEADER,
            name = "X-Tenant-Id",
            required = true,
            description = "University the request belongs to. Read by TenantFilter",
            schema = @Schema(type = "string", example = "UPC"))
    @ApiResponse(
            responseCode = "200",
            description = "The offers found, exact or nearest",
            content =
            @Content(
                    schema = @Schema(implementation = SearchOffersResponse.class),
                    examples =
                    @ExampleObject(
                            name = "Two tutors free at the same hour",
                            value =
                                    """
                                    {
                                      "offers": [
                                        {
                                          "offerId": "a1b2c3d4-0000-4000-8000-000000000001",
                                          "tutorId": "a1b2c3d4-0000-4000-8000-000000000010",
                                          "courseId": "a1b2c3d4-0000-4000-8000-000000000099",
                                          "startsAt": "2026-09-25T14:00:00Z",
                                          "endsAt": "2026-09-25T15:00:00Z",
                                          "tutorRating": 4.80,
                                          "newTutor": false
                                        },
                                        {
                                          "offerId": "a1b2c3d4-0000-4000-8000-000000000002",
                                          "tutorId": "a1b2c3d4-0000-4000-8000-000000000011",
                                          "courseId": "a1b2c3d4-0000-4000-8000-000000000099",
                                          "startsAt": "2026-09-25T14:00:00Z",
                                          "endsAt": "2026-09-25T15:00:00Z",
                                          "tutorRating": null,
                                          "newTutor": true
                                        }
                                      ],
                                      "exactMatch": true
                                    }
                                    """)))
    SearchOffersResponse search(
            @Parameter(description = "The course to find a tutor for") @RequestParam UUID courseId,
            @Parameter(description = "First moment included, ISO 8601 UTC") @RequestParam Instant from,
            @Parameter(description = "First moment excluded, ISO 8601 UTC") @RequestParam Instant to) {

        return SearchOffersResponse.of(search.execute(courseId, from, to));
    }
}